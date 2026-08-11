package org.rllabs.afterlight.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record GateLocalPos(int u, int v) {
    public BlockPos toWorld(BlockPos controllerPosition, Direction facing) {
        requireHorizontal(facing);
        return controllerPosition.relative(facing.getClockWise(), u).above(v);
    }

    static void requireHorizontal(Direction facing) {
        if (facing == null || !facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Gate facing must be horizontal");
        }
    }
}
