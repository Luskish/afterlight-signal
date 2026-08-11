package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class GatePatternTest {
    @Test
    void expectedRingHasExactPartCountsAndSignalGlassPositions() {
        Map<GateLocalPos, GatePattern.GatePart> expected = GatePattern.expected(Direction.NORTH);

        assertEquals(28, expected.size());
        assertEquals(19, count(expected, GatePattern.GatePart.FRAME));
        assertEquals(8, count(expected, GatePattern.GatePart.SIGNAL_GLASS));
        assertEquals(1, count(expected, GatePattern.GatePart.CONTROLLER));
        assertEquals(GatePattern.GatePart.CONTROLLER, expected.get(pos(0, 0)));
        assertEquals(
                Set.of(
                        pos(-2, 0),
                        pos(2, 0),
                        pos(-3, 1),
                        pos(3, 1),
                        pos(-3, 7),
                        pos(3, 7),
                        pos(-2, 8),
                        pos(2, 8)),
                expected.entrySet().stream()
                        .filter(entry -> entry.getValue() == GatePattern.GatePart.SIGNAL_GLASS)
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void interiorContainsExactlyTheThirtyFiveOpenCells() {
        assertEquals(
                Set.of(
                        pos(-2, 1), pos(-1, 1), pos(0, 1), pos(1, 1), pos(2, 1),
                        pos(-2, 2), pos(-1, 2), pos(0, 2), pos(1, 2), pos(2, 2),
                        pos(-2, 3), pos(-1, 3), pos(0, 3), pos(1, 3), pos(2, 3),
                        pos(-2, 4), pos(-1, 4), pos(0, 4), pos(1, 4), pos(2, 4),
                        pos(-2, 5), pos(-1, 5), pos(0, 5), pos(1, 5), pos(2, 5),
                        pos(-2, 6), pos(-1, 6), pos(0, 6), pos(1, 6), pos(2, 6),
                        pos(-2, 7), pos(-1, 7), pos(0, 7), pos(1, 7), pos(2, 7)),
                GatePattern.interior(Direction.NORTH));
    }

    @Test
    void localPositionTransformsAcrossEveryHorizontalFacing() {
        BlockPos controller = new BlockPos(10, 20, 30);
        GateLocalPos local = pos(2, 3);

        assertEquals(new BlockPos(12, 23, 30), local.toWorld(controller, Direction.NORTH));
        assertEquals(new BlockPos(8, 23, 30), local.toWorld(controller, Direction.SOUTH));
        assertEquals(new BlockPos(10, 23, 32), local.toWorld(controller, Direction.EAST));
        assertEquals(new BlockPos(10, 23, 28), local.toWorld(controller, Direction.WEST));
    }

    @Test
    void patternRejectsVerticalFacings() {
        assertThrows(IllegalArgumentException.class, () -> GatePattern.expected(Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> GatePattern.interior(Direction.DOWN));
        assertThrows(
                IllegalArgumentException.class,
                () -> pos(0, 0).toWorld(BlockPos.ZERO, Direction.UP));
    }

    @Test
    void patternCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> GatePattern.expected(Direction.WEST).put(pos(0, 1), GatePattern.GatePart.FRAME));
        assertThrows(
                UnsupportedOperationException.class,
                () -> GatePattern.interior(Direction.WEST).add(pos(0, 0)));
    }

    private static long count(
            Map<GateLocalPos, GatePattern.GatePart> expected, GatePattern.GatePart part) {
        return expected.values().stream().filter(part::equals).count();
    }

    private static GateLocalPos pos(int u, int v) {
        return new GateLocalPos(u, v);
    }
}
