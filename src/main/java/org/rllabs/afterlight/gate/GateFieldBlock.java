package org.rllabs.afterlight.gate;

import net.minecraft.core.BlockPos;
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
