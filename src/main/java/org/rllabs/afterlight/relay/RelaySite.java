package org.rllabs.afterlight.relay;

import net.minecraft.core.Direction;

public enum RelaySite {
    CENTRAL(0, 0),
    EAST(256, 0),
    WEST(-256, 0),
    SOUTH(0, 256),
    NORTH(0, -256);

    private final int x;
    private final int z;

    RelaySite(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public Direction directionTowardCenter() {
        return switch (this) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            case SOUTH -> Direction.NORTH;
            case NORTH -> Direction.SOUTH;
            case CENTRAL -> Direction.SOUTH;
        };
    }
}
