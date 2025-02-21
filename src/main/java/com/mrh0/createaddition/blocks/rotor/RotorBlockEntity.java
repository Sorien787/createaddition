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

    private boolean hasCoil = false;


    public RotorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        setLazyTickRate(20);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        hasCoil = compound.getBoolean("hasCoil");
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putBoolean("hasCoil", hasCoil);
    }


    public void setHasCoil(boolean hasCoil){
        this.hasCoil = hasCoil;
        coilDataUpdated();
    }


    @Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}

    public boolean getHasCoil() {
        return hasCoil;
    }

    public void coilDataUpdated() {
        Optional<CollectorBlockEntity> collectorBlockEntityOptional = RotorBlock.getAssociatedCollector(getLevel(), getBlockPos(), getBlockState());
        if (collectorBlockEntityOptional.isEmpty())
            return;
        collectorBlockEntityOptional.get().scheduleRotorDataUpdate();
    }
}
