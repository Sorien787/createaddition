package com.mrh0.createaddition.blocks.rotor;

import java.util.*;
import java.util.function.Predicate;

import com.google.common.base.Predicates;

import com.mrh0.createaddition.index.CABlockEntities;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.blocks.collector.CollectorBlock;
import com.mrh0.createaddition.blocks.collector.CollectorBlockEntity;
import com.mrh0.createaddition.blocks.stator.StatorBlock;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.placement.IPlacementHelper;
import com.simibubi.create.foundation.placement.PlacementHelpers;
import com.simibubi.create.foundation.placement.PlacementOffset;
import com.simibubi.create.foundation.placement.PoleHelper;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RotorBlock extends RotatedPillarKineticBlock implements IBE<RotorBlockEntity> {

    public static final EnumProperty<RotorModelType> MODEL_TYPE = EnumProperty.create("model", RotorModelType.class);
    public static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());


    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (pLevel.isClientSide())
            return;
        DoStatorCheck(pLevel, pPos, pState);
        DoCollectorUpdate(pLevel, pPos, pState);
    }

    @Override
    public void destroy(LevelAccessor pLevel, BlockPos pPos, BlockState pState) {
        super.destroy(pLevel, pPos, pState);
        if (pLevel.isClientSide())
            return;
        DoCollectorUpdate(pLevel, pPos, pState);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Optional<RotorData> rotorData = getRotorData(world, pos, state);
        if(rotorData.isPresent() && rotorData.get().numRotors > 10)
            return false;
        return super.canSurvive(state, world, pos);
    }
    public static Optional<CollectorBlockEntity> getAssociatedCollector(LevelReader pLevel, BlockPos pPos, BlockState pState){

        Optional<RotorData> rotorData = getRotorData(pLevel, pPos, pState);
        if (rotorData.isEmpty())
            return Optional.empty();

        Axis rotorAxis = pState.getValue(AXIS);
        Direction.AxisDirection[] axisDirections = Direction.AxisDirection.values();
        for (int i = 0; i < axisDirections.length; i++)
        {
            Direction.AxisDirection axisDirection = axisDirections[i];
            BlockPos rotorEnd = rotorData.get().endBlocks[i];
            BlockState potentialCollectorBlockState = pLevel.getBlockState(rotorEnd);

            if (potentialCollectorBlockState.getBlock() != CABlocks.COLLECTOR.get())
                continue;

            Direction facing = potentialCollectorBlockState.getValue(CollectorBlock.FACING);

            // if we're facing a different axis than this rotor, then we're not facing this rotor
            if (facing.getAxis() != rotorAxis)
                continue;
            // if we're facing the same way as we're looking along, then we're facing away from this set of rotors
            if (facing.getAxisDirection() == axisDirection)
                continue;

            CollectorBlockEntity collectorBlockEntity = (CollectorBlockEntity)pLevel.getBlockEntity(rotorEnd);

            if (collectorBlockEntity == null)
                return Optional.empty();

            return Optional.of(collectorBlockEntity);
        }
        return Optional.empty();
    }
    public static Optional<RotorData> getRotorData(LevelReader pLevel, BlockPos pPos, BlockState pState){
        if (pState.getBlock() != CABlocks.ROTOR.get())
            return Optional.empty();
        RotorData returnData = new RotorData();
        Axis rotorAxis = pState.getValue(AXIS);
        Direction.AxisDirection[] values = Direction.AxisDirection.values();

        int num = 0;
        for (Direction.AxisDirection axisDirection : values) {
            returnData.endBlocks[num] = findRotorEnd(pLevel, pPos, pState, rotorAxis, axisDirection);
            num++;
        }
        returnData.numRotors = returnData.endBlocks[0].get(rotorAxis) - returnData.endBlocks[1].get(rotorAxis) - 1;
        returnData.rotorAxis = rotorAxis;
        return Optional.of(returnData);
    }
    private static BlockPos findRotorEnd(LevelReader pLevel, BlockPos pPos, BlockState pState, Axis rotorAxis, Direction.AxisDirection axisDirection){
        BlockPos rotorEndBlockPos = pPos;
        while(true) {
            rotorEndBlockPos = rotorEndBlockPos.relative(rotorAxis, axisDirection.getStep());
            BlockState stateAtBlock = pLevel.getBlockState(rotorEndBlockPos);
            if (stateAtBlock.getBlock() != CABlocks.ROTOR.get())
                break;
            if (stateAtBlock.getValue(AXIS) != rotorAxis)
                break;
        }
        return rotorEndBlockPos;
    }
    private void DoCollectorUpdate(LevelAccessor pLevel, BlockPos pPos, BlockState pState){
        Optional<CollectorBlockEntity> collectorBlockEntity = getAssociatedCollector(pLevel, pPos, pState);
        if (collectorBlockEntity.isEmpty())
            return;
        collectorBlockEntity.get().scheduleRotorDataUpdate();
    }
    private void DoStatorCheck(Level pLevel, BlockPos pPos, BlockState pState){
        Collection<Axis> axesChoices = AXIS.getPossibleValues();
        ArrayList<Axis> axes = new ArrayList<Axis>(axesChoices);
        axes.remove(pState.getValue(AXIS));
        RotorBlockEntity rotorBlockEntity = (RotorBlockEntity) pLevel.getBlockEntity(pPos);
        for (int i = 0; i < 9; i++)
        {

            int axisADistance = i / 3 - 1;
            int axisBDistance = i % 3 - 1;
            if (axisADistance == 0 && axisBDistance == 0)
                continue;
            Axis axisA = axes.get(0);
            Axis axisB = axes.get(1);
            BlockPos otherPos = pPos.relative(axisA, axisADistance).relative(axisB, axisBDistance);
            BlockState otherBlockState = pLevel.getBlockState(otherPos);
            if (otherBlockState.getBlock() != CABlocks.STATOR.get())
                continue;
            Optional<RotorBlockEntity> optionalRotorBlock = StatorBlock.GetFacingRotorBlock(pLevel, otherPos, otherBlockState);
            if (optionalRotorBlock.isEmpty())
                continue;
            if(optionalRotorBlock.get().getBlockPos() != pPos)
                continue;
            optionalRotorBlock.get().AddStator(otherPos);
        }

        rotorBlockEntity.UpdateNeighbourEfficiency();
    }
    public RotorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(defaultBlockState().setValue(MODEL_TYPE, RotorModelType.SINGLE));
    }

    public enum RotorModelType implements StringRepresentable {
        SINGLE, MIDDLE, END_A, END_B;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return getSerializedName();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, block, fromPos, isMoving);
        Axis axis = state.getValue(AXIS);
        if(fromPos.get(axis) == pos.get(axis))
            return;

        OnParallelChanged(state, axis, pos, world);
    }

    private void OnParallelChanged(BlockState state, Axis axis, BlockPos pos, Level world)
    {
        ArrayList<BlockPos> adjacentBlockStates = getAdjacentShaftPosition(axis, pos, world); // front block = 0, rear block = 1
        RotorModelType modelType = getRotorModelTypeFromAdjacent(world, adjacentBlockStates, state);
        setModel(world, pos, state, modelType);
    }


    private void setModel(Level world, BlockPos pos, BlockState state, RotorModelType bType) {
        if(state.getValue(MODEL_TYPE) == bType)
            return;
        world.setBlock(pos, state.setValue(MODEL_TYPE, bType), Block.UPDATE_ALL);
    }
    public static boolean IsBlockPosParallelRotor(Level world, BlockPos blockPos, Axis axis){
        BlockState otherBlockState = world.getBlockState(blockPos);
        if (otherBlockState.getBlock() != CABlocks.ROTOR.get())
            return false;

        if (otherBlockState.getValue(AXIS) != axis)
            return false;
        return true;
    }
    private RotorModelType getRotorModelTypeFromAdjacent( Level world, ArrayList<BlockPos> adjacentBlocks, BlockState state) {
        Axis thisAxis = state.getValue(AXIS);
        boolean hasFrontRotor = IsBlockPosParallelRotor(world, adjacentBlocks.get(0), thisAxis);
        boolean hasRearRotor  = IsBlockPosParallelRotor(world, adjacentBlocks.get(1), thisAxis);
        
        if(hasFrontRotor && hasRearRotor)
            return RotorModelType.MIDDLE;
        if(hasFrontRotor)
            return RotorModelType.END_B;
        if(hasRearRotor)
            return RotorModelType.END_A;
        return RotorModelType.SINGLE;
    }

    public ArrayList<BlockPos> getAdjacentShaftPosition(Axis rotationAxis, BlockPos currentPos, Level world) {
        ArrayList<BlockPos> out = new ArrayList<>(2);
        out.add(currentPos.relative(rotationAxis, +1));
        out.add(currentPos.relative(rotationAxis, -1));
        return out;
    }

    @Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult ray) {
        if (player.isShiftKeyDown() || !player.mayBuild()) return InteractionResult.PASS;

        IPlacementHelper helper = PlacementHelpers.get(placementHelperId);
        ItemStack heldItem = player.getItemInHand(hand);

        if (helper.matchesItem(heldItem))
            return helper.getOffset(player, world, state, pos, ray)
                .placeInWorld(world, (BlockItem) heldItem.getItem(), player, hand, ray);

        return InteractionResult.PASS;
    }

    @Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

    @Override
    public Class<RotorBlockEntity> getBlockEntityClass() {
        return RotorBlockEntity.class;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODEL_TYPE);
    }

    @Override
    public BlockEntityType<? extends RotorBlockEntity> getBlockEntityType() {
        return CABlockEntities.ROTOR.get();
    }    

    @MethodsReturnNonnullByDefault
	private static class PlacementHelper extends PoleHelper<Direction.Axis> {
		private PlacementHelper() {
			super(state -> state.getBlock() instanceof RotorBlock, state -> state.getValue(AXIS), AXIS);
		}

		@Override
		public Predicate<ItemStack> getItemPredicate() {
			return i -> i.getItem() instanceof BlockItem
				&& ((BlockItem) i.getItem()).getBlock() instanceof RotorBlock;
		}

		@Override
		public Predicate<BlockState> getStatePredicate() {
			return Predicates.or(CABlocks.ROTOR::has);
		}

		@Override
		public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
			BlockHitResult ray) {
			PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
			if (offset.isSuccessful()) {
				offset.withTransform(offset.getTransform()
					.andThen(newState -> ShaftBlock.pickCorrectShaftType(newState, world, offset.getBlockPos())));
            }
			return offset;
		}
	}
}
