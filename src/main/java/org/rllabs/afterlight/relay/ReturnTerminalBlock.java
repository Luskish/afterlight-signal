package org.rllabs.afterlight.relay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.rllabs.afterlight.gate.GateTravelService;

public final class ReturnTerminalBlock extends Block {
    public ReturnTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
