package com.mrh0.createaddition.blocks.rotor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class RotorData{
    Direction.Axis rotorAxis;
    BlockPos[] endBlocks = new BlockPos[2];
    int numRotors;
}