package com.mrh0.createaddition.blocks.base_electro_kinetic;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.energy.InternalEnergyStorage;
import com.mrh0.createaddition.util.Util;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

public abstract class BaseElectroKineticBlockEntity extends GeneratingKineticBlockEntity {

    protected final InternalEnergyStorage energy;
    private LazyOptional<IEnergyStorage> lazyEnergy;

    private boolean firstTickState = true;

    public BaseElectroKineticBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        energy = new InternalEnergyStorage(getCapacity(), getMaxIn(), getMaxOut());
        lazyEnergy = LazyOptional.of(() -> energy);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return super.getCapability(cap, side);
    }
    public abstract int getCapacity();
    public abstract int getMaxIn();
    public abstract int getMaxOut();
    public abstract boolean isEnergyInput(Direction side);
    public abstract boolean isEnergyOutput(Direction side);

    @Override
    public void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        energy.read(compound);
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        energy.write(compound);
    }

    @Override
    public void tick() {
        super.tick();
        if(level.isClientSide())
            return;
        if(firstTickState)
            firstTick();
        firstTickState = false;


    }

    @Override
    public void remove() {
        lazyEnergy.invalidate();
        super.remove();
    }

    public void firstTick() {
        updateCache();
    };
    public void updateCache() {
        if(level.isClientSide())
            return;
        for(Direction side : Direction.values()) {
            updateCache(side);
        }
    }
    private void updateCache(Direction side) {
        if (!level.isLoaded(worldPosition.relative(side))) {
            setCache(side, LazyOptional.empty());
            return;
        }
        BlockEntity te = level.getBlockEntity(worldPosition.relative(side));
        if(te == null) {
            setCache(side, LazyOptional.empty());
            return;
        }
        LazyOptional<IEnergyStorage> le = te.getCapability(ForgeCapabilities.ENERGY, side.getOpposite());
        if(ignoreCapSide() && !le.isPresent()) le = te.getCapability(ForgeCapabilities.ENERGY);
        // Make sure the side isn't already cached.
        IEnergyStorage cachedLe = getCachedEnergy(side);
        boolean isSame = cachedLe == le.orElse(null);
        if (isSame)
            return;
        setCache(side, le);
        le.addListener((es) -> updateCache(side));
    }
    public boolean ignoreCapSide() {
        return false;
    }

    private LazyOptional<IEnergyStorage> escacheUp = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheDown = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheNorth = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheEast = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheSouth = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheWest = LazyOptional.empty();

    public void setCache(Direction side, LazyOptional<IEnergyStorage> storage) {
        switch(side) {
            case DOWN:
                escacheDown = storage;
                break;
            case EAST:
                escacheEast = storage;
                break;
            case NORTH:
                escacheNorth = storage;
                break;
            case SOUTH:
                escacheSouth = storage;
                break;
            case UP:
                escacheUp = storage;
                break;
            case WEST:
                escacheWest = storage;
                break;
        }
    }

    public IEnergyStorage getCachedEnergy(Direction side) {
        switch(side) {
            case DOWN:
                return escacheDown.orElse(null);
            case EAST:
                return escacheEast.orElse(null);
            case NORTH:
                return escacheNorth.orElse(null);
            case SOUTH:
                return escacheSouth.orElse(null);
            case UP:
                return escacheUp.orElse(null);
            case WEST:
                return escacheWest.orElse(null);
        }
        return null;
    }

}
