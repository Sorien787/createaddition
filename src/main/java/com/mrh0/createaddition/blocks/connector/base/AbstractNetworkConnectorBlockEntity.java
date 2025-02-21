package com.mrh0.createaddition.blocks.connector.base;

import com.mrh0.createaddition.blocks.connector.ConnectorType;
import com.mrh0.createaddition.energy.IWireNode;
import com.mrh0.createaddition.energy.LocalNode;
import com.mrh0.createaddition.energy.NodeRotation;
import com.mrh0.createaddition.energy.WireType;
import com.mrh0.createaddition.energy.network.EnergyNetwork;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractNetworkConnectorBlockEntity extends SmartBlockEntity implements IWireNode {
    protected boolean wasContraption = false;
    public AbstractNetworkConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.localNodes = new LocalNode[getNodeCount()];
        this.nodeCache = new IWireNode[getNodeCount()];
    }

    protected final Set<LocalNode> wireCache = new HashSet<>();
    protected EnergyNetwork network;
    protected final LocalNode[] localNodes;
    protected final IWireNode[] nodeCache;
    @Override
    public @Nullable IWireNode getWireNode(int index) {
        return IWireNode.getWireNodeFrom(index, this, this.localNodes, this.nodeCache, level);
    }

    @Override
    public @Nullable LocalNode getLocalNode(int index) {
        return this.localNodes[index];
    }

    @Override
    public void setNode(int index, int other, BlockPos pos, WireType type) {
        this.localNodes[index] = new LocalNode(this, index, other, type, pos);

        notifyUpdate();

        // Invalidate
        if (network != null)
            network.invalidate();
    }

    @Override
    public void removeNode(int index, boolean dropWire) {
        LocalNode old = this.localNodes[index];
        this.localNodes[index] = null;
        this.nodeCache[index] = null;

        invalidateNodeCache();
        notifyUpdate();

        // Invalidate
        if (network != null)
            network.invalidate();
        // Drop wire next tick.
        if (dropWire && old != null)
            this.wireCache.add(old);
    }

    private boolean firstTick = true;
    private void validateNodes() {
        boolean changed = validateLocalNodes(this.localNodes);

        // Always set as changed if we were a contraption, as nodes might have been rotated.
        notifyUpdate();

        if (!changed)
            return;
        invalidateNodeCache();
        // Invalidate
        if (this.network == null)
            return;
        this.network.invalidate();
    }
    public void firstTick() {
        this.firstTick = false;
        // Check if this block entity was a part of a contraption.
        // If it was, then make sure all the nodes are valid.
        if(level == null)
            return;


        if (this.wasContraption && !level.isClientSide()) {
            this.wasContraption = false;
            validateNodes();
        }
        onFirstTick();
    }

    @Override
    public void tick() {
        if (this.firstTick)
            firstTick();

        if (level == null)
            return;

        if (!level.isLoaded(getBlockPos()))
            return;

        // Check if we need to drop any wires due to contraption.
        if (!this.wireCache.isEmpty() && !isRemoved())
            handleWireCache(level, this.wireCache);

        OnTick();

        super.tick();

    }

    public abstract OptionalInt getContactTransmissionDirectionIndex(BlockPos pOtherPos);
    public abstract ArrayList<Direction> getContactTransmissionDirections();
    protected void setContactTransmissionOnPlace(){
        ArrayList<Direction> transmissionDirections = getContactTransmissionDirections();
        BlockPos thisBlockPos = getBlockPos();
        for(Direction transmissionDirection : transmissionDirections){
            BlockPos otherBlockPos = thisBlockPos.relative(transmissionDirection);
            BlockEntity be = getLevel().getBlockEntity(otherBlockPos);
            if (be == null)
                continue;
            if (!(be instanceof AbstractNetworkConnectorBlockEntity))
                continue;

            AbstractNetworkConnectorBlockEntity otherConnectorBlockEntity = (AbstractNetworkConnectorBlockEntity)be;

            OptionalInt otherTransmissionDirectionIndex = otherConnectorBlockEntity.getContactTransmissionDirectionIndex(thisBlockPos);
            if (otherTransmissionDirectionIndex.isEmpty())
                continue;
            OptionalInt thisTransmissionDirectionIndex  = getContactTransmissionDirectionIndex(otherBlockPos);
            assert(thisTransmissionDirectionIndex.isPresent());
            setNode(thisTransmissionDirectionIndex.getAsInt(), otherTransmissionDirectionIndex.getAsInt(), otherBlockPos, WireType.NONE);
            otherConnectorBlockEntity.setNode(otherTransmissionDirectionIndex.getAsInt(), thisTransmissionDirectionIndex.getAsInt(), thisBlockPos, WireType.NONE);
        }
    }

    protected void OnTick() {}

    protected void onFirstTick() {}

    @Override
    public Vec3 getNodeOffset(int node) {
        return new Vec3(0.0f, 0.0f, 0.0f);
    }

    @Override
    public BlockPos getPos() {
        return getBlockPos();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void invalidateNodeCache() {
        for(int i = 0; i < getNodeCount(); i++)
            this.nodeCache[i] = null;
    }
    @Override
    public void setNetwork(int node, EnergyNetwork network) {
        this.network = network;
    }

    @Override
    public EnergyNetwork getNetwork(int node) {
        return this.network;
    }

    @Override
    public ConnectorType getConnectorType() {
        return ConnectorType.Small;
    }

    @Override
    public int getMaxWireLength() {
        return 0;
    }

    @Override
    public void remove() {
        super.remove();
        if(level == null) return;
        if (level.isClientSide()) return;
        // Remove all nodes.
        for (int i = 0; i < getNodeCount(); i++) {
            LocalNode localNode = getLocalNode(i);
            if (localNode == null) continue;
            IWireNode otherNode = getWireNode(i);
            if(otherNode == null) continue;

            int ourNode = localNode.getOtherIndex();
            if (localNode.isInvalid())
                otherNode.removeNode(ourNode);
            else
                otherNode.removeNode(ourNode, true); // Make the other node drop the wires.
        }

        invalidateNodeCache();
        invalidateCaps();

        // Invalidate
        if (network != null) network.invalidate();
    }


    @Override
    public void read(CompoundTag nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        // Convert old nbt data. x0, y0, z0, node0 & type0 etc.
        if (!clientPacket && nbt.contains("node0")) {
            convertOldNbt(nbt);
            setChanged();
        }

        // Read the nodes.
        invalidateLocalNodes();
        invalidateNodeCache();
        ListTag nodes = nbt.getList(LocalNode.NODES, Tag.TAG_COMPOUND);
        nodes.forEach(tag -> {
            LocalNode localNode = new LocalNode(this, (CompoundTag) tag);
            this.localNodes[localNode.getIndex()] = localNode;
        });

        // Check if this was a contraption.
        if (nbt.contains("contraption") && !clientPacket) {
            this.wasContraption = nbt.getBoolean("contraption");
            NodeRotation rotation = getBlockState().getValue(NodeRotation.ROTATION);
            if(level == null) return;
            if (rotation != NodeRotation.NONE)
                level.setBlock(getBlockPos(), getBlockState().setValue(NodeRotation.ROTATION, NodeRotation.NONE), 0);
            // Loop over all nodes and update their relative positions.
            for (LocalNode localNode : this.localNodes) {
                if (localNode == null) continue;
                localNode.updateRelative(rotation);
            }
        }

        // Invalidate the network if we updated the nodes.
        if (!nodes.isEmpty() && this.network != null) this.network.invalidate();
    }

    public void invalidateLocalNodes() {
        for(int i = 0; i < getNodeCount(); i++)
            this.localNodes[i] = null;
    }

    @Override
    public void write(CompoundTag nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        // Write nodes.
        ListTag nodes = new ListTag();
        for (int i = 0; i < getNodeCount(); i++) {
            LocalNode localNode = this.localNodes[i];
            if (localNode == null) continue;
            CompoundTag tag = new CompoundTag();
            localNode.write(tag);
            nodes.add(tag);
        }
        nbt.put(LocalNode.NODES, nodes);
    }

}
