package com.mrh0.createaddition.blocks.collector;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.blocks.base_electro_kinetic.BaseElectroKineticBlockEntity;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.blocks.rotor.RotorBlockEntity;
import com.mrh0.createaddition.util.Util;

import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

public class CollectorBlockEntity extends BaseElectroKineticBlockEntity implements IHaveGoggleInformation {
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.numCoils" + Util.format(cachedTotalCoils)).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.numRotors" + Util.format(cachedTotalRotorBlocks)).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.efficiency" + cachedTotalEfficiency).withStyle(ChatFormatting.GRAY)));

        if (isGeneratorMode()) {
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.stressUsed" + (calculateStressApplied())).withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID +  ".tooltip.energy.energyGenerated" + (calculateEnergyGenerated(true))).withStyle(ChatFormatting.GRAY)));
        }
        else {
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.stressGenerated" + (calculateStressGenerated(true))).withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.energyUsed" + (calculateEnergyConsumed(true))).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }
    protected ScrollOptionBehaviour<CollectorMode> collectorMode;
    private float cachedTotalEfficiency = 0.0f;
    private float cachedEfficiency = 0.0f;
    private int   cachedTotalRotorBlocks = 0;
    private int cachedTotalCoils = 0;
    private boolean updateRotorDataScheduled = false;

    private float speedPerCoil = 10.0f;
    private float stressEnergyPerCoil = 10.0f;
    private boolean enoughEnergy = false;
    private int calculateEnergyGenerated(boolean theoretical){
        return (int)(calculateStressConsumed(theoretical) * cachedEfficiency);
    }
    private float calculateStressConsumed(boolean theoretical){
        return cachedTotalCoils * stressEnergyPerCoil * (theoretical ? getTheoreticalSpeed() : speed);
    }
    private float calculateStressGenerated(boolean theoretical){
        return calculateEnergyConsumed(theoretical) * cachedEfficiency;
    }
    private int calculateEnergyConsumed(boolean theoretical){
            return (int)((cachedTotalCoils * stressEnergyPerCoil) * calculateSpeedGenerated(theoretical) * ((theoretical || enoughEnergy ) ? 1 : 0));
    }
    private float calculateSpeedGenerated(boolean theoretical){
        return cachedTotalCoils * speedPerCoil * ((theoretical || enoughEnergy ) ? 1 : 0);
    }
    @Override
    public float getGeneratedSpeed() {
        if(isGeneratorMode())
            return 0.0f;
        return calculateSpeedGenerated(false);
    }
    @Override
    public float calculateAddedStressCapacity() {
        if (isGeneratorMode())
            return 0.0f;
        return calculateStressGenerated(false);
    }

    @Override
    public float calculateStressApplied() {
        if (isMotorMode())
            return 0.0f;
        return calculateStressConsumed(true);
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);

        //onOperationalDataChanged();
    }

    private void doGenerateTick(){
        if (!isGeneratorMode())
            return;
        for(Direction d : Direction.values()) {
            if(!isEnergyOutput(d))
                continue;
            IEnergyStorage ies = getCachedEnergy(d);
            if(ies == null)
                continue;
            int ext = energy.extractEnergy(ies.receiveEnergy(getMaxOut(), true), false);
            ies.receiveEnergy(ext, false);
        }

        int energyToProduce = calculateEnergyGenerated(false);
        energy.internalProduceEnergy(energyToProduce);
    }

    private void doMotorTick(){
        if (!isMotorMode())
            return;
        for(Direction d : Direction.values()) {
            if(!isEnergyInput(d))
                continue;
            IEnergyStorage ies = getCachedEnergy(d);
            if(ies == null)
                continue;
            int ext = ies.extractEnergy(energy.receiveEnergy(getMaxIn(), true), false);
            energy.receiveEnergy(ext, false);
        }
        int energyToExtract = calculateEnergyConsumed(true);
        boolean enoughEnergy = energyToExtract != 0 && energyToExtract == energy.extractEnergy(energyToExtract, true);
        setEnoughEnergy(enoughEnergy);
        if (!enoughEnergy)
            return;
        energy.internalConsumeEnergy(energyToExtract);
    }
    public void onNeighbourChanged(BlockPos neighbour){
        updateCache();
    }
    public CollectorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }
    @Override
    public int getCapacity() {
        return 100000000;
    }

    @Override
    public int getMaxIn() {
        return 100000000;
    }

    @Override
    public int getMaxOut() {
        return 100000000;
    }

    @Override
    public boolean isEnergyInput(Direction side) {
        return energy.canReceive();
    }

    @Override
    public boolean isEnergyOutput(Direction side) {
        return energy.canExtract();
    }

    @Override
    public void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        cachedTotalEfficiency  = compound.getFloat("efficiency");
        cachedTotalRotorBlocks = compound.getInt  ("rotorBlocks");
        cachedTotalCoils       = compound.getInt  ("coils");
        cachedEfficiency       = cachedTotalEfficiency / cachedTotalRotorBlocks;
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putFloat("efficiency",  cachedTotalEfficiency);
        compound.putInt  ("rotorBlocks", cachedTotalRotorBlocks);
        compound.putInt  ("coils",       cachedTotalCoils);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        collectorMode = new ScrollOptionBehaviour<>(CollectorMode.class, Lang.translateDirect("contraptions.windmill.rotation_direction"), this,  getCollectorModeSlot());
        collectorMode.withCallback($ -> onCollectorModeChanged());
        behaviours.add(collectorMode);
    }
    private boolean isMotorMode(){
        return collectorMode.get() == CollectorMode.MOTOR;
    }
    private boolean isGeneratorMode(){
        return collectorMode.get() == CollectorMode.GENERATOR;
    }
    
    private void setEnoughEnergy(boolean enoughEnergy){
        if (this.enoughEnergy == enoughEnergy)
            return;
        this.enoughEnergy = enoughEnergy;
        onOperationalDataChanged();
    }

    private void onOperationalDataChanged(){
        if (!isMotorMode())
            return;
        updateGeneratedRotation();
    }

    private void onCollectorModeChanged(){
        energy.setMaxIn(getMaxIn());
        energy.setMaxOut(getMaxOut());
        onOperationalDataChanged();
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
        energy.setMaxIn(getMaxIn());
        energy.setMaxOut(getMaxOut());
        doMotorTick();
        doGenerateTick();
        if (!updateRotorDataScheduled)
            return;
        updateRotorData();
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
            totalEfficiency += 1.0f; //rotorBlockEntity.getCachedEfficiency();
            totalCoils      += rotorBlockEntity.getHasCoil() ? 1 : 0;
            totalBlocks++;
            newBlockPos = newBlockPos.relative(facingDirection);
        }
        boolean hasChanged = cachedTotalCoils != totalCoils || cachedTotalEfficiency != totalEfficiency || cachedTotalRotorBlocks != totalBlocks;
        if (!hasChanged)
            return;
        cachedTotalRotorBlocks = totalBlocks;
        cachedTotalEfficiency  = totalEfficiency;
        cachedTotalCoils       = totalCoils;
        cachedEfficiency       = totalEfficiency / totalBlocks;
        onOperationalDataChanged();

    }
    public void scheduleRotorDataUpdate() {
        updateRotorDataScheduled = true;
    }
    @Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}

    public static enum CollectorMode implements INamedIconOptions {
        GENERATOR(AllIcons.I_REFRESH), MOTOR(AllIcons.I_ROTATE_CCW),

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
