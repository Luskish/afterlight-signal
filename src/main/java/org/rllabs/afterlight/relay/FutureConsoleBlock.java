package org.rllabs.afterlight.relay;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FutureConsoleBlock extends SignalTerminalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            Block.box(1.0, 0.0, 2.0, 15.0, 4.0, 14.0),
            Block.box(4.0, 4.0, 6.0, 12.0, 8.0, 14.0),
            Block.box(2.0, 7.0, 1.0, 14.0, 11.0, 13.0));
    private static final VoxelShape EAST = Shapes.or(
            Block.box(2.0, 0.0, 1.0, 14.0, 4.0, 15.0),
            Block.box(2.0, 4.0, 4.0, 10.0, 8.0, 12.0),
            Block.box(3.0, 7.0, 2.0, 15.0, 11.0, 14.0));
    private static final VoxelShape SOUTH = Shapes.or(
            Block.box(1.0, 0.0, 2.0, 15.0, 4.0, 14.0),
            Block.box(4.0, 4.0, 2.0, 12.0, 8.0, 10.0),
            Block.box(2.0, 7.0, 3.0, 14.0, 11.0, 15.0));
    private static final VoxelShape WEST = Shapes.or(
            Block.box(2.0, 0.0, 1.0, 14.0, 4.0, 15.0),
            Block.box(6.0, 4.0, 4.0, 14.0, 8.0, 12.0),
            Block.box(1.0, 7.0, 2.0, 13.0, 11.0, 14.0));

    public FutureConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shape(Direction facing) {
        return switch (facing) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }
}
