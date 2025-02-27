package com.mrh0.createaddition.blocks.connector.base;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.debug.IDebugDrawer;
import com.mrh0.createaddition.energy.*;
import com.mrh0.createaddition.energy.network.EnergyNetwork;
import com.mrh0.createaddition.util.Util;
import com.mrh0.createaddition.network.EnergyNetworkPacket;
import com.mrh0.createaddition.network.IObserveTileEntity;
import com.mrh0.createaddition.network.ObservePacket;
import com.simibubi.create.CreateClient;

import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractConnectorBlockEntity extends AbstractNetworkConnectorBlockEntity implements IObserveTileEntity, IHaveGoggleInformation, IDebugDrawer {


	protected LazyOptional<IEnergyStorage> capability = this.createEmptyHandler();
	protected LazyOptional<IEnergyStorage> external = LazyOptional.empty();
	@Override
	public OptionalInt getContactTransmissionDirectionIndex(BlockPos pOtherPos){

		Direction dir = getBlockState().getValue(AbstractConnectorBlock.FACING);

		BlockPos thisPos = getBlockPos();
		BlockPos posInDirection = thisPos.relative(dir);
		int offset = posInDirection.compareTo(pOtherPos);
		if (offset != 0)
			return OptionalInt.empty();
		int availableNode = getAvailableNode();
		if (availableNode == -1)
			return OptionalInt.empty();
		return OptionalInt.of(availableNode);
	}

	@Override
	public ArrayList<Direction> getContactTransmissionDirections(){
		ArrayList<Direction> returnVal = new ArrayList<>();
		int availableNode = getAvailableNode();
		if (availableNode == -1)
			return returnVal;
		returnVal.ensureCapacity(1);
		returnVal.add(getBlockState().getValue(AbstractConnectorBlock.FACING));
		return returnVal;
	}

	public AbstractConnectorBlockEntity(BlockEntityType<?> blockEntityTypeIn, BlockPos pos, BlockState state) {
		super(blockEntityTypeIn, pos, state);
	}

	private LazyOptional<IEnergyStorage> createEmptyHandler() {
		return LazyOptional.of(InterfaceEnergyHandler::new);
	}

	@Override
	public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.ENERGY && (isEnergyInput(side) || isEnergyOutput(side))) return this.capability.cast();
		return super.getCapability(cap, side);
	}

	public abstract int getMaxIn();
	public abstract int getMaxOut();
	public int getCapacity() {
		return Math.min(getMaxIn(), getMaxOut());
	}

	private class InterfaceEnergyHandler implements IEnergyStorage {
		public InterfaceEnergyHandler() {}

		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			if(!Config.CONNECTOR_ALLOW_PASSIVE_IO.get()) return 0;
			if(getMode() != ConnectorMode.Pull) return 0;
			if (network == null) return 0;
			maxReceive = Math.min(maxReceive, getMaxIn());
			return network.push(maxReceive, simulate);
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			if(!Config.CONNECTOR_ALLOW_PASSIVE_IO.get()) return 0;
			if(getMode() != ConnectorMode.Push) return 0;
			if (network == null) return 0;
			maxExtract = Math.min(maxExtract, getMaxOut());
			return network.pull(maxExtract, simulate);
		}

		@Override
		public int getEnergyStored() {
			if (network == null) return 0;
			return Math.min(getCapacity(), network.getBuff());
		}

		@Override
		public int getMaxEnergyStored() {
			return getCapacity();
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return true;
		}
	}


	public boolean isEnergyInput(Direction side) {
		return getBlockState().getValue(AbstractConnectorBlock.FACING) == side;
	}

	@Override
	protected void OnTick() {
		if (getMode() == ConnectorMode.None)
			return;

		if(level.isClientSide())
			return;

		if(awakeNetwork(level))
			notifyUpdate();

		networkTick(network);

		if (externalStorageInvalid)
			updateExternalEnergyStorage();
	}

	public boolean isEnergyOutput(Direction side) {
		return getBlockState().getValue(AbstractConnectorBlock.FACING) == side;
	}

	/**
	 * Called after the tile entity has been part of a contraption.
	 * Only runs on the server.
	 */


	@Override
	protected void onFirstTick() {
		updateExternalEnergyStorage();
	}

	boolean externalStorageInvalid = false;

	private final static IEnergyStorage NULL_ES = new EnergyStorage(0, 0, 0);
	private void networkTick(EnergyNetwork network) {
		ConnectorMode mode = getMode();

		if (mode == ConnectorMode.Push) {
			int pulled = network.pull(network.demand(external.orElse(NULL_ES).receiveEnergy(getMaxOut(), true)));
			external.orElse(NULL_ES).receiveEnergy(pulled, false);
		}

		if (mode == ConnectorMode.Pull) {
			int toPush = external.orElse(NULL_ES).extractEnergy(network.push(getMaxIn(), true), false);
			network.push(toPush);
		}
	}

	public ConnectorMode getMode() {
		return getBlockState().getValue(AbstractConnectorBlock.MODE);
	}

	@Override
	public void onObserved(ServerPlayer player, ObservePacket pack) {
		if(isNetworkValid(0))
			EnergyNetworkPacket.send(worldPosition, getNetwork(0).getPulled(), getNetwork(0).getPushed(), player);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		ObservePacket.send(worldPosition, 0);

		tooltip.add(Component.literal(spacing)
				.append(Component.translatable(CreateAddition.MODID + ".tooltip.connector.info").withStyle(ChatFormatting.WHITE)));

		tooltip.add(Component.literal(spacing)
				.append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.mode").withStyle(ChatFormatting.GRAY)));
		tooltip.add(Component.literal(spacing).append(Component.literal(" "))
				.append(getBlockState().getValue(AbstractConnectorBlock.MODE).getTooltip().withStyle(ChatFormatting.AQUA)));

		tooltip.add(Component.literal(spacing)
				.append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.usage").withStyle(ChatFormatting.GRAY)));
		tooltip.add(Component.literal(spacing).append(" ")
				.append(Util.format((int)EnergyNetworkPacket.clientBuff)).append("fe/t").withStyle(ChatFormatting.AQUA));

		return IHaveGoggleInformation.super.addToGoggleTooltip(tooltip, isPlayerSneaking);
	}

	public boolean ignoreCapSide() {
		return this.getBlockState().getValue(AbstractConnectorBlock.MODE).isActive();
	}

	public void updateExternalEnergyStorage() {
		if (level == null) return;
		if (!level.isLoaded(getBlockPos())) return;
		externalStorageInvalid = false;
		var side = getBlockState().getValue(AbstractConnectorBlock.FACING);
		if (!level.isLoaded(worldPosition.relative(side))) {
			external = LazyOptional.empty();
			return;
		}
		BlockEntity te = level.getBlockEntity(worldPosition.relative(side));
		if(te == null) {
			external = LazyOptional.empty();
			return;
		}
		LazyOptional<IEnergyStorage> le = te.getCapability(ForgeCapabilities.ENERGY, side.getOpposite());
		if(ignoreCapSide() && !le.isPresent()) le = te.getCapability(ForgeCapabilities.ENERGY);
		// Make sure the side isn't already cached.
		if (le.equals(external)) return;
		external = le;
		le.addListener((es) -> externalStorageInvalid = true);
	}

	@Override
	public void drawDebug() {
		if (level == null) return;
		// Outline all connected nodes.
		for (int i = 0; i < getNodeCount(); i++) {
			LocalNode localNode = this.localNodes[i];
			if (localNode == null) continue;
			BlockPos pos = localNode.getPos();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getBlockSupportShape(level, pos);
			int color;
			if (i == 0) color = 0xFF0000;
			else if (i == 1) color = 0x00FF00;
			else if (i == 2) color = 0x0000FF;
			else color = 0xFFFFFF;
			// Make sure the node is a connector block.
			if (!(level.getBlockEntity(pos) instanceof IWireNode)) {
				shape = Shapes.block();
				color = 0xFF00FF;
			}
			// ca_ = Create Addition
			CreateClient.OUTLINER.chaseAABB("ca_nodes_" + i, shape.bounds().move(pos)).lineWidth(0.0625F).colored(color);
		}
		// Outline connected power
		BlockEntity te = level.getBlockEntity(worldPosition.relative(getBlockState().getValue(AbstractConnectorBlock.FACING)));
		if(te == null) return;

		var cap = te.getCapability(ForgeCapabilities.ENERGY, getBlockState().getValue(AbstractConnectorBlock.FACING).getOpposite());
		if(ignoreCapSide() && !cap.isPresent()) cap = te.getCapability(ForgeCapabilities.ENERGY);

		if (!cap.isPresent()) return;
		VoxelShape shape = level.getBlockState(te.getBlockPos()).getBlockSupportShape(level, te.getBlockPos());
		CreateClient.OUTLINER.chaseAABB("ca_output", shape.bounds().move(te.getBlockPos())).lineWidth(0.0625F).colored(0x5B5BFF);
	}
}
