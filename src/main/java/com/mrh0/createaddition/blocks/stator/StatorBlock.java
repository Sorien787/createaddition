package com.mrh0.createaddition.blocks.stator;

import java.util.Locale;
import java.util.Optional;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.mrh0.createaddition.index.CABlockEntities;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.blocks.rotor.RotorBlockEntity;
import com.mrh0.createaddition.blocks.oriented.SimpleOrientedBlock;
import com.mrh0.createaddition.blocks.oriented.SimpleOrientation;
import com.mrh0.createaddition.helper.StatorDirectionalHelper;
import com.mrh0.createaddition.shapes.CAShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.placement.PlacementHelpers;
import com.simibubi.create.foundation.placement.IPlacementHelper;
import com.simibubi.create.foundation.placement.PlacementOffset;
import com.simibubi.create.foundation.utility.VoxelShaper;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public class StatorBlock extends SimpleOrientedBlock implements IBE<StatorBlockEntity> {

    public static final EnumProperty<StatorBlockModelType> MODEL_TYPE = EnumProperty.create("model", StatorBlockModelType.class);  // BASE or CORNER
    public static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());
    public static final VoxelShaper BASE_SHAPE = CAShapes.shape(0, 0, 0, 16, 11, 16).defaultUp();
    public static final VoxelShaper CORNER_SHAPE = CAShapes.shape(0, 0, 0, 16, 11, 7).add(0, 9, 5, 16, 16, 16).defaultUp();
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        IBE.onRemove(pState, pLevel, pPos, pNewState);
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (pLevel.isClientSide())
            return;
        ((StatorBlockEntity)pLevel.getBlockEntity(pPos)).onStatorPlaced();
    }


    public enum StatorBlockModelType implements StringRepresentable {
        BASE, CORNER;

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
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction cardinal = state.getValue(ORIENTATION).getCardinal();
        if(state.getValue(MODEL_TYPE) == StatorBlockModelType.BASE) {
            return BASE_SHAPE.get(cardinal);
        } // TODO ADDRESS WEIRDNESS
        return CORNER_SHAPE.get(cardinal);
    }

    ////////
    public StatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(MODEL_TYPE, StatorBlockModelType.BASE));
    }

    @Override
    public Class<StatorBlockEntity> getBlockEntityClass() {
        return StatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StatorBlockEntity> getBlockEntityType() {
        return CABlockEntities.STATOR.get();
    }

    @Override
    public float getShadeBrightness(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return 1f;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODEL_TYPE);
    }
    
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult ray) {
        IPlacementHelper helper = PlacementHelpers.get(placementHelperId);

        ItemStack heldItem = player.getItemInHand(hand);
		if (helper.matchesItem(heldItem))
			return helper.getOffset(player, world, state, pos, ray)
				.placeInWorld(world, (BlockItem) heldItem.getItem(), player, hand, ray);

		return InteractionResult.PASS;
	}

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if(state.getValue(MODEL_TYPE) == StatorBlockModelType.CORNER)
            return InteractionResult.PASS;
        return super.onWrenched(state, context);
    }

    @MethodsReturnNonnullByDefault
	private static class PlacementHelper extends StatorDirectionalHelper<SimpleOrientation> {
		// co-opted from Create's shaft placement helper, but this uses SimpelOrientation instead of DirectionalAxis

		private PlacementHelper() {
			super(state -> state.getBlock() instanceof StatorBlock, state -> state.getValue(ORIENTATION), ORIENTATION);
		}

		@Override
		public Predicate<ItemStack> getItemPredicate() {
			return i -> i.getItem() instanceof BlockItem
				&& ((BlockItem) i.getItem()).getBlock() instanceof StatorBlock;
		}

		@Override
		public Predicate<BlockState> getStatePredicate() {
			return Predicates.or(CABlocks.STATOR::has);
		}

		@Override
		public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
			PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
			if (!offset.isSuccessful())
                return offset;
            offset.withTransform(offset.getTransform());
            return offset;
		}
	}
}
