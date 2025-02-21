package com.mrh0.createaddition.blocks.stator;

import com.mrh0.createaddition.blocks.oriented.SimpleOrientation;
import com.mrh0.createaddition.blocks.oriented.SimpleOrientedBlock;
import com.mrh0.createaddition.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;

public class StatorConnectivityHandler {


    public static BlockPos[] AngledNormalFromOrientation = new BlockPos[]{
            new BlockPos(0, -1, +1), // down_x
            new BlockPos(+1, -1, 0), // down_z
            new BlockPos(0, +1, -1), // up_x
            new BlockPos(-1, +1, 0), // up_z
            new BlockPos(0, 0, -1), // north_y
            new BlockPos(0, -1, -1), // north_x
            new BlockPos(0, 0, +1), // south_y
            new BlockPos(0, +1, +1), // south_x
            new BlockPos(+1, 0, 0), // east_y
            new BlockPos(+1, +1, 0), // east_z
            new BlockPos(-1, 0, 0), // west_y
            new BlockPos(-1, -1, 0) //west_z
    };

    public static BlockPos[] FlatNormalFromOrientation = new BlockPos[]{
            new BlockPos(0, -1, 0), // down_x
            new BlockPos(0, -1, 0), // down_z
            new BlockPos(0, +1, 0), // up_x
            new BlockPos(0, +1, 0), // up_z
            new BlockPos(0, 0, -1), // north_y
            new BlockPos(0, 0, -1), // north_x
            new BlockPos(0, 0, +1), // south_y
            new BlockPos(0, 0, +1), // south_x
            new BlockPos(+1, 0, 0), // east_y
            new BlockPos(+1, 0, 0), // east_z
            new BlockPos(-1, 0, 0), // west_y
            new BlockPos(-1, 0, 0) //west_z
    };
    public static BlockPos getStatorNormalOffset(SimpleOrientation orient, StatorBlock.StatorBlockModelType modelType){
        if (modelType == StatorBlock.StatorBlockModelType.CORNER) {
            return StatorConnectivityHandler.AngledNormalFromOrientation[orient.getIndex()];
        }
        else {
            return StatorConnectivityHandler.FlatNormalFromOrientation[orient.getIndex()];
        }
    }
    public static Direction getStatorCoilConnectivityDirection(SimpleOrientation orientation, StatorBlock.StatorBlockModelType modelType, Direction.AxisDirection clockwiseDirection){
        BlockPos normal = StatorConnectivityHandler.getStatorNormalOffset(orientation, modelType);
        ArrayList<Direction> possibleDirections = getStatorConnectivityDirections(orientation, modelType);
        for(Direction direction : possibleDirections){
            // select the direction that is
            // A: not along orientAxis
            if (direction.getAxis() == orientation.getOrient())
                continue;
            // B: the cross product with normal matches the sign of clockwiseDirection
            BlockPos blockPos = BlockPos.ZERO.relative(direction);
            BlockPos cross = blockPos.cross(normal);
            int crossValue = cross.get(orientation.getOrient());
            if (crossValue != clockwiseDirection.getStep())
                continue;
            return direction;
        }
        assert(false);
        return Direction.UP;
    }
    public static ArrayList<Direction> getStatorConnectivityDirections(SimpleOrientation orientation, StatorBlock.StatorBlockModelType modelType){
        ArrayList<Direction> directions = new ArrayList<>();
        directions.ensureCapacity(5);
        Direction.Axis orientAxis = orientation.getOrient();
        directions.add(Direction.get(Direction.AxisDirection.POSITIVE, orientAxis));
        directions.add(Direction.get(Direction.AxisDirection.NEGATIVE, orientAxis));

        BlockPos normal = StatorConnectivityHandler.getStatorNormalOffset(orientation, modelType);
        int numDirs = 0;
        for (Direction.Axis axis : Direction.Axis.VALUES){
            numDirs += Math.min(Math.abs(normal.get(axis)), 1);
        }

        if (numDirs == 1){
            // we want the normal of axis and whatever axis is non-zero in this blockpos
            for(Direction.Axis axis : Direction.Axis.VALUES) {
                // is parallel to normal
                if (normal.get(axis) != 0)
                {
                    // can transmit behind (But only to connectors? maybe need a Type in there...)
                    directions.add(Direction.get( normal.get(axis) > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis));
                    continue;
                }
                // is parallel to orient axis, already handled
                if (axis == orientAxis)
                    continue;

                directions.add(Direction.get(Direction.AxisDirection.POSITIVE, axis));
                directions.add(Direction.get(Direction.AxisDirection.NEGATIVE, axis));
            }
        }
        else{
            assert(numDirs == 2);
            for (Direction.Axis axis : Direction.Axis.VALUES){
                if (axis == orientAxis)
                    continue;
                Direction.AxisDirection normalDirection = normal.get(axis) < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE;
                Direction direction = Direction.get(normalDirection, axis);
                directions.add(direction);
            }
        }
        assert(directions.size() == 4);
        return directions;
    }


    public static class StatorBlockData{
        public StatorBlock.StatorBlockModelType modelType;
        public SimpleOrientation orientation;
        public BlockPos blockPos;
    }
    public void tryFormNewStatorMulti(StatorBlockEntity statorBlockEntity){

    }

    public void tryBreakStatorMulti(StatorBlockEntity statorBlockEntity){
        // if this statorblockentity was a controller, check around it
    }

    public static StatorBlockData getNextStatorBlockData(StatorBlockData data, Direction.AxisDirection axisDirection){
        Direction newDirection = getStatorCoilConnectivityDirection(data.orientation, data.modelType, axisDirection);
        boolean isCorner =  data.modelType == StatorBlock.StatorBlockModelType.CORNER;
        boolean isClockwise = axisDirection.equals(Direction.AxisDirection.POSITIVE);
        StatorBlockData newData;
        newData = new StatorBlockData();
        newData.modelType   = isCorner ? StatorBlock.StatorBlockModelType.BASE : StatorBlock.StatorBlockModelType.CORNER;
        newData.orientation = data.orientation;
        newData.blockPos    = data.blockPos.relative(newDirection);
        Direction.Axis clockwiseAxis = data.orientation.getOrient();
        boolean invert             = clockwiseAxis == Direction.Axis.Z;
        boolean newOrientation     = isCorner ^ isClockwise;
        boolean fuckingOrientation = newOrientation ^ invert;
        if (fuckingOrientation) {
            BlockPos clockwiseVector = BlockPos.ZERO.relative(clockwiseAxis, axisDirection.getStep());
            Direction cardinalDirection = data.orientation.getCardinal();
            BlockPos cardinalDirectionVector = BlockPos.ZERO.relative(cardinalDirection);
            BlockPos newCardinal = clockwiseVector.cross(cardinalDirectionVector).multiply(axisDirection.getStep());
            newData.orientation = SimpleOrientation.combine(Util.getDirection(newCardinal), data.orientation.getOrient());
        }
        return newData;
    }
}
