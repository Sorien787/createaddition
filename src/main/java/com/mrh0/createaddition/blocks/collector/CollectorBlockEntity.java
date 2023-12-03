package com.mrh0.createaddition.blocks.collector;

import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.blocks.rotor.RotorBlockEntity;
import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class CollectorBlockEntity extends KineticBlockEntity {

    protected ScrollOptionBehaviour<CollectorMode> collectorMode;
    private float cachedTotalEfficiency = 0.0f;
    private int   cachedTotalRotorBlocks = 0;
    private int cachedTotalCoils = 0;
    private boolean updateRotorDataScheduled = false;
    public CollectorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        setLazyTickRate(20);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        cachedTotalEfficiency  = compound.getFloat("efficiency");
        cachedTotalRotorBlocks = compound.getInt  ("rotorBlocks");
        cachedTotalCoils       = compound.getInt  ("coils");
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        collectorMode = new ScrollOptionBehaviour<>(CollectorMode.class, Lang.translateDirect("contraptions.windmill.rotation_direction"), this,  getCollectorModeSlot());
        collectorMode.withCallback($ -> onCollectorModeChanged());
        behaviours.add(collectorMode);
    }
    private void onCollectorModeChanged(){

    }
    private ValueBoxTransform getCollectorModeSlot(){
        return new DirectionalExtenderScrollOptionSlot((state, d) -> {
            Direction.Axis axis = d.getAxis();
            Direction.Axis collectorAxis = state.getValue(CollectorBlock.FACING).getAxis();
            return axis != collectorAxis;
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (!updateRotorDataScheduled)
            return;
        updateRotorData();
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putFloat("efficiency",  cachedTotalEfficiency);
        compound.putInt  ("rotorBlocks", cachedTotalRotorBlocks);
        compound.putInt  ("coils",       cachedTotalCoils);
    }
    private void updateRotorData(){
        updateRotorDataScheduled = false;
        Direction facingDirection = getBlockState().getValue(CollectorBlock.FACING);
        BlockPos  newBlockPos     = getBlockPos().relative(facingDirection);
        float     totalEfficiency = 0.0f;
        int       totalBlocks     = 0;
        int       totalCoils      = 0;
        while(getLevel().getBlockState(newBlockPos).getBlock() == CABlocks.ROTOR.get())
        {
            RotorBlockEntity rotorBlockEntity = (RotorBlockEntity)getLevel().getBlockEntity(newBlockPos);
            totalEfficiency += rotorBlockEntity.getCachedEfficiency();
            totalCoils      += rotorBlockEntity.getHasCoil() ? 1 : 0;
            totalBlocks++;
            newBlockPos = newBlockPos.relative(facingDirection);
        }
        cachedTotalRotorBlocks = totalBlocks;
        cachedTotalEfficiency  = totalEfficiency;
        cachedTotalCoils       = totalCoils;
    }
    public void scheduleRotorDataUpdate()
    {
        updateRotorDataScheduled = true;
    }
    @Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}
    public static enum CollectorMode implements INamedIconOptions {

        CLOCKWISE(AllIcons.I_REFRESH), COUNTER_CLOCKWISE(AllIcons.I_ROTATE_CCW),

        ;

        private String translationKey;
        private AllIcons icon;

        private CollectorMode(AllIcons icon) {
            this.icon = icon;
            translationKey = "generic." + Lang.asId(name());
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

    }

}
