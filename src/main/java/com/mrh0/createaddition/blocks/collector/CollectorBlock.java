package com.mrh0.createaddition.blocks.collector;

import java.util.Locale;

import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CABlockEntities;
import com.mrh0.createaddition.blocks.rotor.RotorBlock;
import com.mrh0.createaddition.shapes.CAShapes;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.utility.VoxelShaper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CollectorBlock extends DirectionalKineticBlock implements IBE<CollectorBlockEntity> {

    public static final EnumProperty<CollectorBlockModelType> MODEL_TYPE = EnumProperty.create("model", CollectorBlockModelType.class);
    public static final VoxelShaper SHAPE = CAShapes.shape(2, 5, 5, 9, 11, 11).add(9, 2, 2, 16, 14, 14).defaultUp();

    public CollectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(defaultBlockState().setValue(MODEL_TYPE, CollectorBlockModelType.BASE));
    }

    public enum CollectorBlockModelType implements StringRepresentable {
        BASE, ROTORED;

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
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1f;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING).getOpposite();
        if(facing.getAxis() == Axis.Y) return SHAPE.get(facing);
        return SHAPE.get(facing.getCounterClockWise());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
		if (preferred == null || (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown())) {
			Direction nearestLookingDirection = context.getNearestLookingDirection();
			return defaultBlockState().setValue(FACING, context.getPlayer() != null && context.getPlayer()
				.isShiftKeyDown() ? nearestLookingDirection.getOpposite() : nearestLookingDirection);
		}
		return defaultBlockState().setValue(FACING, preferred);
    }

    @Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction dir) {
		return dir.getAxis() == state.getValue(FACING).getAxis();
	}

    @Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if(!doRotorCheck(world, pos, state))
            return;
        setModelType(CollectorBlockModelType.ROTORED, world, pos, state);
        ((CollectorBlockEntity)world.getBlockEntity(pos)).scheduleRotorDataUpdate();
    }

    private void setModelType(CollectorBlockModelType modelType, Level world, BlockPos pos, BlockState state){
        if(state.getValue(MODEL_TYPE) == modelType)
            return;
        world.setBlock(pos, state.setValue(MODEL_TYPE, modelType), Block.UPDATE_ALL);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        super.neighborChanged(state, world, pos, pBlock, pFromPos, pIsMoving);
        boolean rotorInFront = doRotorCheck(world, pos, state);
        setModelType(rotorInFront ? CollectorBlockModelType.ROTORED : CollectorBlockModelType.BASE, world, pos, state);
    }

    private boolean doRotorCheck(Level world, BlockPos pos, BlockState state) {
        Direction ax = state.getValue(FACING);

        if(world.getBlockState(pos.relative(ax)).getBlock() != CABlocks.ROTOR.get()) {
            return false;
        }
        if (world.getBlockState(pos.relative(ax)).getValue(RotorBlock.AXIS) != ax.getAxis())
            return false;
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction ax = state.getValue(FACING);
        if(world.getBlockState(pos.relative(ax)).getBlock() == CABlocks.COLLECTOR.get())
            return false;
        if(world.getBlockState(pos.relative(ax.getOpposite())).getBlock() == CABlocks.COLLECTOR.get())
            return false;

        return super.canSurvive(state, world, pos);
    }

    @Override
    public Class<CollectorBlockEntity> getBlockEntityClass() {
        return CollectorBlockEntity.class;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODEL_TYPE);
    }

    @Override
    public BlockEntityType<? extends CollectorBlockEntity> getBlockEntityType() {
        return CABlockEntities.COLLECTOR.get();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }    
}
