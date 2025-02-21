package com.mrh0.createaddition.blocks.stator;

import java.util.*;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.blocks.connector.base.AbstractNetworkConnectorBlockEntity;
import com.mrh0.createaddition.blocks.oriented.SimpleOrientation;
import com.mrh0.createaddition.blocks.oriented.SimpleOrientedBlock;
import com.mrh0.createaddition.blocks.rotor.RotorBlockEntity;
import com.mrh0.createaddition.index.CABlocks;

import com.mrh0.createaddition.util.Util;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;


public class StatorBlockEntity extends AbstractNetworkConnectorBlockEntity implements IHaveGoggleInformation {

    public StatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        controller = pos;
    }
    private final int idleUsePerTick = 1;
    @Override
    public int getNodeCount() {
        return 5;
    }
    private BlockPos controller;

    private float cachedEfficiency = 0f;
    private boolean enoughEnergy = false;
    private boolean hasCoil = false;
    private StatorBlockEntity getController(){
        return isController() ? this : (StatorBlockEntity) Objects.requireNonNull(getLevel()).getBlockEntity(controller);
    }
    public void setController(BlockPos controllerPos){
        controller = controllerPos;
        sendData();
    }
    public void onControllerCleared(){
        controller = getBlockPos();
        sendData();
    }
    public boolean isController(){
        return controller.equals(getBlockPos());
    }
    public boolean hasEnoughEnergy(){
        return getController().enoughEnergy;
    }
    public float getCachedEfficiency(){
        return getController().cachedEfficiency;
    }
    public boolean hasCoil(){
        return getController().hasCoil;
    }
    public void updateNeighbourEfficiency()
    {
        BlockPos thisBlockPos = getBlockPos();
        updateEfficiency();
        Direction.Axis thisAxis = getBlockState().getValue(StatorBlock.ORIENTATION).getOrient();
        List<BlockPos> neighbours = Arrays.asList(thisBlockPos.relative(thisAxis, -1), thisBlockPos.relative(thisAxis, +1));

        for (BlockPos neighbourPos : neighbours){
            if (isPosNotIdenticalStator(neighbourPos))
                continue;
            ((StatorBlockEntity)getLevel().getBlockEntity(neighbourPos)).updateEfficiency();
        }
    }

    private List<BlockPos> getAdjacentPositions(){
        BlockPos thisBlockPos = getBlockPos();
        Direction.Axis thisAxis = getBlockState().getValue(StatorBlock.ORIENTATION).getOrient();
        return Arrays.asList(thisBlockPos.relative(thisAxis, -1), thisBlockPos.relative(thisAxis, +1));
    }

    private boolean isPosNotIdenticalStator(BlockPos pos){
        if (!(getLevel().getBlockEntity(pos) instanceof StatorBlockEntity))
            return true;
        BlockState thisBlockState = getBlockState();
        BlockState otherBlockState = getLevel().getBlockState(pos);
        if (thisBlockState.getValue(StatorBlock.MODEL_TYPE)  != otherBlockState.getValue(StatorBlock.MODEL_TYPE))
            return true;
        if (thisBlockState.getValue(StatorBlock.ORIENTATION) != otherBlockState.getValue(StatorBlock.ORIENTATION))
            return true;

        return false;
    }

    public float getProximalEfficiencyScalar(){
        return hasCoil() ? 0.75f : 1.0f;
    }
    private float getStatorEfficiencyScalar(){
        return hasCoil() ? 1.0f : 0.0f;
    }

    private void updateEfficiency()
    {
        if (!isController()) {
            StatorBlockEntity controllerStator = getController();
            controllerStator.updateEfficiency();
            return;
        }


        List<BlockPos> neighbours = getAdjacentPositions();

        float proximalEfficiencyScalar = getStatorEfficiencyScalar();

        for (int i = 0; i < neighbours.size(); i++) {
            if(isPosNotIdenticalStator(neighbours.get(i)))
                continue;
            StatorBlockEntity neighbourStator = ((StatorBlockEntity)level.getBlockEntity(neighbours.get(i)));
            proximalEfficiencyScalar *= neighbourStator.getProximalEfficiencyScalar();
        }

        if (cachedEfficiency == proximalEfficiencyScalar)
            return;

        cachedEfficiency = proximalEfficiencyScalar;

        Optional<RotorBlockEntity> facingRotor = getFacingRotorBlock();

        if (facingRotor.isEmpty())
            return;

        facingRotor.get().coilDataUpdated();

    }
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.iscontroller" + (isController() ? "true" : "false")).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.usage").withStyle(ChatFormatting.GRAY)));
        if (getController().hasCoil()) {
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.isCoil").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID +  ".tooltip.energy.isFunctioning" + (getController().hasEnoughEnergy() ? "true" : "false")).withStyle(ChatFormatting.GRAY)));
        }
        else {
            tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.isNotCoil").withStyle(ChatFormatting.GRAY)));
        }
        tooltip.add(Component.literal(spacing).append(Component.literal(" " + Util.format(getController().getEnergyUsage()) + "fe/t ") // fix
                .withStyle(ChatFormatting.AQUA)).append(Lang.translateDirect("gui.goggles.at_current_speed").withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }

    private void clearController(){
        if (!isController())
            return;
        if (!hasCoil())
            return;
        SimpleOrientation orientation = getBlockState().getValue(SimpleOrientedBlock.ORIENTATION);
        StatorBlock.StatorBlockModelType modelType = getBlockState().getValue(StatorBlock.MODEL_TYPE);
        StatorConnectivityHandler.StatorBlockData blockData = new StatorConnectivityHandler.StatorBlockData();
        blockData.blockPos    = getBlockPos();
        blockData.orientation = orientation;
        blockData.modelType   = modelType;

        for(int numIterations = 0; numIterations < 8; numIterations++)
        {
            blockData = StatorConnectivityHandler.getNextStatorBlockData(blockData, Direction.AxisDirection.POSITIVE);
            ((StatorBlockEntity)getLevel().getBlockEntity(blockData.blockPos)).onControllerCleared();
        }
        setHasCoil(false);
    }
    private void  tryFormCompleteCoil(){
        SimpleOrientation orientation = getBlockState().getValue(SimpleOrientedBlock.ORIENTATION);
        StatorBlock.StatorBlockModelType modelType = getBlockState().getValue(StatorBlock.MODEL_TYPE);
        StatorConnectivityHandler.StatorBlockData blockData = new StatorConnectivityHandler.StatorBlockData();
        blockData.blockPos    = getBlockPos();
        blockData.orientation = orientation;
        blockData.modelType   = modelType;

        for(int numIterations = 0; numIterations < 8; numIterations++)
        {
            blockData = StatorConnectivityHandler.getNextStatorBlockData(blockData, Direction.AxisDirection.POSITIVE);
            BlockState blockState = getLevel().getBlockState(blockData.blockPos);
            if(blockState.getBlock() != CABlocks.STATOR.get())
                return;
            if (blockState.getValue(SimpleOrientedBlock.ORIENTATION) != blockData.orientation)
                return;
            if (blockState.getValue(StatorBlock.MODEL_TYPE) != blockData.modelType)
                return;
        }

        for(int numIterations = 0; numIterations < 8; numIterations++)
        {
            blockData = StatorConnectivityHandler.getNextStatorBlockData(blockData, Direction.AxisDirection.POSITIVE);
            StatorBlockEntity otherStator = ((StatorBlockEntity)getLevel().getBlockEntity(blockData.blockPos));
            otherStator.setController(getBlockPos());
        }

        setHasCoil(true);
    }

    private void setHasCoil(boolean set){
        if (hasCoil == set)
            return;
        hasCoil = set;
        updateNeighbourEfficiency();
        updateCentreHasCoil();
    }

    public int getEnergyUsage(){
        if (!isController())
            return 0;
        return hasCoil() ? idleUsePerTick * 8 : idleUsePerTick;
    }
    @Override
    protected void OnTick() {
        super.OnTick();
        if (network == null || !isController())
            return;
        int usage  = getEnergyUsage();
        int pulled = network.pull(usage, true);
        enoughEnergy = pulled == usage;
        if (!enoughEnergy)
            return;
        network.pull(usage, false);
    }
    @Override
    public void destroy() {
        super.destroy();
        getController().clearController();
    }

    @Override
    public void read(CompoundTag nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        hasCoil = nbt.getBoolean("hasCoil");
        controller = NbtUtils.readBlockPos(nbt.getCompound("controller"));
        cachedEfficiency = nbt.getFloat("cachedEfficiency");
    }

    @Override
    public void write(CompoundTag nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        nbt.putBoolean("hasCoil", hasCoil);
        nbt.put("controller", NbtUtils.writeBlockPos(controller));
        nbt.putFloat("cachedEfficiency", cachedEfficiency);
    }

    private void updateCentreHasCoil(){
        Optional<RotorBlockEntity> facing = getFacingRotorBlock();
        if (facing.isEmpty())
            return;
        facing.get().setHasCoil(hasCoil);
    }

    public Optional<RotorBlockEntity> getFacingRotorBlock()
    {
        BlockPos   normalPos   = getStatorNormalPos();
        BlockState normalState = getLevel().getBlockState(normalPos);
        if (normalState.getBlock() != CABlocks.ROTOR.get())
            return Optional.empty();
        return Optional.of((RotorBlockEntity) getLevel().getBlockEntity(normalPos));
    }

    private BlockPos getStatorNormalPos() {
        return getBlockPos().offset(StatorConnectivityHandler.getStatorNormalOffset(getBlockState().getValue(SimpleOrientedBlock.ORIENTATION) ,getBlockState().getValue(StatorBlock.MODEL_TYPE)));
    }


    private Direction blockPosToDirection(BlockPos otherBlockPos){
        BlockPos offset = otherBlockPos.offset(getBlockPos().multiply(-1));
        int numAxes = 0;
        Direction.Axis cachedDirection = null;
        for (Direction.Axis axisDirection : Direction.Axis.VALUES){
            if (offset.get(axisDirection) == 0)
                continue;
            cachedDirection = axisDirection;
            numAxes++;
        }
        if (cachedDirection == null || numAxes > 1)
            return null;

        int value = offset.get(cachedDirection);
        return Direction.get(value > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, cachedDirection);
    }
    @Override
    public OptionalInt getContactTransmissionDirectionIndex(BlockPos pOtherPos){
        Direction possibleTransmissionDirection = blockPosToDirection(pOtherPos);
        if (possibleTransmissionDirection == null)
            return OptionalInt.empty();
        SimpleOrientation orient = getBlockState().getValue(SimpleOrientedBlock.ORIENTATION);
        Direction.Axis orientAxis = orient.getOrient();
        if(possibleTransmissionDirection.getAxis() == orientAxis)
            return OptionalInt.of(possibleTransmissionDirection.getAxisDirection().ordinal());

        BlockPos statorNormal = StatorConnectivityHandler.getStatorNormalOffset(getBlockState().getValue(SimpleOrientedBlock.ORIENTATION) ,getBlockState().getValue(StatorBlock.MODEL_TYPE));
        int numDirs = 0;

        for (Direction.Axis axis : Direction.Axis.VALUES){
            numDirs += Math.min(Math.abs(statorNormal.get(axis)), 1);
        }

        if(numDirs == 1){
            // simple normal of this
            // already tested for orient parallel (a valid state for both)
            // and otherDirection must be either along statorNormal or perp to both
            if (statorNormal.get(possibleTransmissionDirection.getAxis()) != 0) {
                return OptionalInt.of(4);
            }
            return OptionalInt.of(2 + possibleTransmissionDirection.getAxisDirection().ordinal());
        }
        else {
            // there WILL be an axis along possible transmission direction
            // question is, is the DIRECTION along transmission direction?
            int value = statorNormal.get(possibleTransmissionDirection.getAxis());
            if(possibleTransmissionDirection.getAxisDirection().getStep() != value)
                return OptionalInt.empty();

            // and for index, just go via convention:
            // X, then Y, then Z.
            // so if the stator normal has an X component, that'll be index 3.
            // otherwise it has a Y component, so that'll be index 3 instead.
            boolean hasX = statorNormal.get(Direction.Axis.X) != 0;
            if (hasX)
            {
                return OptionalInt.of(possibleTransmissionDirection.getAxis() == Direction.Axis.X ? 3 : 4);
            }
            else
            {
                return OptionalInt.of(possibleTransmissionDirection.getAxis() == Direction.Axis.Y ? 3 : 4);
            }
        }
    }

    @Override
    public ArrayList<Direction> getContactTransmissionDirections(){
        return StatorConnectivityHandler.getStatorConnectivityDirections(getBlockState().getValue(StatorBlock.ORIENTATION), getBlockState().getValue(StatorBlock.MODEL_TYPE));
    }


    public void onStatorPlaced(){
        setContactTransmissionOnPlace();
        tryFormCompleteCoil();

        if(awakeNetwork(level))
            notifyUpdate();
    }
}
