package org.rllabs.afterlight.relay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.rllabs.afterlight.gate.GateTravelService;

public final class ReturnTerminalBlock extends SignalTerminalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(4.0, 3.0, 5.0, 12.0, 16.0, 14.0),
            Block.box(3.0, 5.0, 0.0, 13.0, 14.0, 5.0));
    private static final VoxelShape EAST = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(2.0, 3.0, 4.0, 11.0, 16.0, 12.0),
            Block.box(11.0, 5.0, 3.0, 16.0, 14.0, 13.0));
    private static final VoxelShape SOUTH = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(4.0, 3.0, 2.0, 12.0, 16.0, 11.0),
            Block.box(3.0, 5.0, 11.0, 13.0, 14.0, 16.0));
    private static final VoxelShape WEST = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(5.0, 3.0, 4.0, 14.0, 16.0, 12.0),
            Block.box(0.0, 5.0, 3.0, 5.0, 14.0, 13.0));

    public ReturnTerminalBlock(BlockBehaviour.Properties properties) {
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

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.serverLevel() != serverLevel) {
            return InteractionResult.PASS;
        }
        return GateTravelService.INSTANCE.returnPlayer(serverPlayer)
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }
}
