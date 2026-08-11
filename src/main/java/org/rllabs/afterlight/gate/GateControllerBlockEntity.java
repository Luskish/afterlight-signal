package org.rllabs.afterlight.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.rllabs.afterlight.EchoContent;

public final class GateControllerBlockEntity extends BlockEntity {
    public GateControllerBlockEntity(BlockPos position, BlockState state) {
        super(EchoContent.GATE_CONTROLLER_BLOCK_ENTITY.get(), position, state);
    }
}
