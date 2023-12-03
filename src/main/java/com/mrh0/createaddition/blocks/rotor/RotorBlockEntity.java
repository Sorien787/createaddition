package com.mrh0.createaddition.blocks.rotor;

import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.blocks.collector.CollectorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class RotorBlockEntity extends KineticBlockEntity {

    private int numStators = 0;

    private float cachedEfficiency = 0f;

    public RotorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        setLazyTickRate(20);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        numStators = compound.getInt("statorNumber");
        cachedEfficiency = compound.getFloat("efficiency");
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putInt("statorNumber", numStators);
        compound.putFloat("efficiency", cachedEfficiency);
    }

    public void AddStator(BlockPos pos)
    {
        BlockState rotorBlockState = level.getBlockState(getBlockPos());
        Direction.Axis axis = rotorBlockState.getValue(RotorBlock.AXIS);
        if(getBlockPos().get(axis) != pos.get(axis))
            return;
        numStators++;
        if (numStators != 8)
            return;
        UpdateNeighbourEfficiency();
    }

    public void RemoveStator(BlockPos pos)
    {
        BlockState rotorBlockState = level.getBlockState(getBlockPos());
        Direction.Axis axis = rotorBlockState.getValue(RotorBlock.AXIS);
        if(getBlockPos().get(axis) != pos.get(axis))
            return;
        numStators--;
        if (numStators != 7)
            return;
        UpdateNeighbourEfficiency();
    }

    public void UpdateNeighbourEfficiency()
    {
        RotorBlock block = (RotorBlock) getBlockState().getBlock();
        ArrayList<BlockPos> neighbours = block.getAdjacentShaftPosition(getBlockState().getValue(RotorBlock.AXIS), getBlockPos(), level);
        TryUpdateEfficiency(getBlockPos());

        Direction.Axis thisAxis = getBlockState().getValue(RotorBlock.AXIS);
        for (BlockPos neighbourPos : neighbours){
            if (!RotorBlock.IsBlockPosParallelRotor(getLevel(), neighbourPos, thisAxis))
                continue;
            TryUpdateEfficiency(neighbourPos);
        }
    }
    private boolean hasFullCoil(){
        return numStators == 8;
    }
    public float getProximalEfficiencyScalar(){
        return hasFullCoil() ? 0.75f : 1.0f;
    }
    private float getStatorEfficiencyScalar(){
        return hasFullCoil() ? 1.0f : 0.0f;
    }
    public void TryUpdateEfficiency(BlockPos blockPos){
        if (level.getBlockState(blockPos).getBlock() != CABlocks.ROTOR.get())
            return;
        ((RotorBlockEntity)level.getBlockEntity(blockPos)).UpdateEfficiency();
    }
    public boolean getHasCoil(){
        return numStators == 8;
    }
    public float getCachedEfficiency(){
        return cachedEfficiency;
    }
    private void UpdateEfficiency()
    {
        RotorBlock rotorBlock = (RotorBlock) getBlockState().getBlock();
        ArrayList<BlockPos> neighbours = rotorBlock.getAdjacentShaftPosition(getBlockState().getValue(RotorBlock.AXIS), getBlockPos(), level);

        float proximalEfficiencyScalar = getStatorEfficiencyScalar();

        for (int i = 0; i < neighbours.size(); i++) {
            if (level.getBlockState(neighbours.get(i)).getBlock() != CABlocks.ROTOR.get())
                continue;
            proximalEfficiencyScalar *= ((RotorBlockEntity)level.getBlockEntity(neighbours.get(i))).getProximalEfficiencyScalar();
        }

        if (cachedEfficiency == proximalEfficiencyScalar)
            return;

        cachedEfficiency = proximalEfficiencyScalar;
        Optional<CollectorBlockEntity> collectorBlockEntityOptional = RotorBlock.getAssociatedCollector(getLevel(), getBlockPos(), getBlockState());
        if (collectorBlockEntityOptional.isEmpty())
            return;
        collectorBlockEntityOptional.get().scheduleRotorDataUpdate();
    }

    @Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}
}
