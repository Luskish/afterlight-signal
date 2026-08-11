package org.rllabs.afterlight.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.rllabs.afterlight.EchoContent;

public final class GateFieldBlock extends Block implements EntityBlock {
    public GateFieldBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new GateFieldBlockEntity(position, state);
    }

    @Override
    protected void entityInside(
            BlockState state, Level level, BlockPos position, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)
                || player.serverLevel() != serverLevel) {
            return;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(position);
        if (blockEntity instanceof GateFieldBlockEntity field
                && field.ownerPosition() != null
                && field.ownerId() != null
                && field.isOwnedBy(field.ownerPosition(), field.ownerId())) {
            GateTravelService.INSTANCE.travelToFarRelay(player, field.ownerPosition());
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()
                || blockEntityType != EchoContent.GATE_FIELD_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickerLevel, position, tickerState, blockEntity) ->
                GateFieldBlockEntity.serverTick(
                        tickerLevel,
                        position,
                        tickerState,
                        (GateFieldBlockEntity) blockEntity);
    }
}
