package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GatePatternMatcherTest {
    private static final ResourceLocation AIR = id("minecraft:air");
    private static final ResourceLocation DIRT = id("minecraft:dirt");
    private static final ResourceLocation STONE = id("minecraft:stone");
    private static final ResourceLocation FRAME = id("afterlight:gate_frame");
    private static final ResourceLocation SIGNAL_GLASS = id("afterlight:signal_glass");
    private static final ResourceLocation CONTROLLER = id("afterlight:gate_controller");
    private static final Set<GateLocalPos> SIGNAL_GLASS_POSITIONS = Set.of(
            pos(-2, 0),
            pos(2, 0),
            pos(-3, 1),
            pos(3, 1),
            pos(-3, 7),
            pos(3, 7),
            pos(-2, 8),
            pos(2, 8));

    @Test
    void exactLoadedStructureMatches() {
        BlockPos controller = new BlockPos(20, 64, -12);
        TestWorld world = exactGate(controller, Direction.EAST);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.EAST);

        assertTrue(result.matches());
        assertEquals(Set.of(), Set.copyOf(result.mismatches()));
    }

    @Test
    void unloadedPositionReturnsPreciseMismatchWithoutReadingBlock() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.NORTH);
        GateLocalPos local = pos(-1, 8);
        BlockPos worldPosition = local.toWorld(controller, Direction.NORTH);
        world.unloaded.add(worldPosition);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.NORTH);

        assertFalse(result.matches());
        assertEquals(
                new GatePatternMatcher.Mismatch(
                        GatePatternMatcher.MismatchKind.UNLOADED_CHUNK,
                        local,
                        worldPosition,
                        FRAME,
                        null),
                onlyMismatch(result));
        assertFalse(world.readPositions.contains(worldPosition));
    }

    @Test
    void wrongRingBlockReturnsExpectedAndActualIds() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.SOUTH);
        GateLocalPos local = pos(2, 8);
        BlockPos worldPosition = local.toWorld(controller, Direction.SOUTH);
        world.blocks.put(worldPosition, DIRT);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.SOUTH);

        assertEquals(
                new GatePatternMatcher.Mismatch(
                        GatePatternMatcher.MismatchKind.WRONG_BLOCK,
                        local,
                        worldPosition,
                        SIGNAL_GLASS,
                        DIRT),
                onlyMismatch(result));
    }

    @Test
    void nonReplaceableInteriorReturnsBlockedMismatch() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.WEST);
        GateLocalPos local = pos(1, 4);
        BlockPos worldPosition = local.toWorld(controller, Direction.WEST);
        world.blocks.put(worldPosition, STONE);
        world.nonReplaceable.add(worldPosition);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.WEST);

        assertEquals(
                new GatePatternMatcher.Mismatch(
                        GatePatternMatcher.MismatchKind.INTERIOR_BLOCKED,
                        local,
                        worldPosition,
                        null,
                        STONE),
                onlyMismatch(result));
    }

    @Test
    void absentControllerReturnsMissingControllerMismatch() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.NORTH);
        world.blocks.put(controller, AIR);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.NORTH);

        assertEquals(
                new GatePatternMatcher.Mismatch(
                        GatePatternMatcher.MismatchKind.MISSING_CONTROLLER,
                        pos(0, 0),
                        controller,
                        CONTROLLER,
                        AIR),
                onlyMismatch(result));
    }

    @Test
    void everyAdditionalControllerReturnsItsOwnMismatch() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.NORTH);
        GateLocalPos ringLocal = pos(1, 8);
        GateLocalPos interiorLocal = pos(1, 3);
        BlockPos ringWorld = ringLocal.toWorld(controller, Direction.NORTH);
        BlockPos interiorWorld = interiorLocal.toWorld(controller, Direction.NORTH);
        world.blocks.put(ringWorld, CONTROLLER);
        world.blocks.put(interiorWorld, CONTROLLER);
        world.nonReplaceable.add(interiorWorld);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.NORTH);

        assertEquals(
                Set.of(
                        new GatePatternMatcher.Mismatch(
                                GatePatternMatcher.MismatchKind.SECOND_CONTROLLER,
                                ringLocal,
                                ringWorld,
                                FRAME,
                                CONTROLLER),
                        new GatePatternMatcher.Mismatch(
                                GatePatternMatcher.MismatchKind.SECOND_CONTROLLER,
                                interiorLocal,
                                interiorWorld,
                                null,
                                CONTROLLER)),
                Set.copyOf(result.mismatches()));
    }

    @Test
    void matcherReturnsEveryIndependentMismatch() {
        BlockPos controller = BlockPos.ZERO;
        TestWorld world = exactGate(controller, Direction.NORTH);
        GateLocalPos unloadedLocal = pos(-1, 8);
        GateLocalPos wrongLocal = pos(-2, 0);
        GateLocalPos blockedLocal = pos(0, 4);
        BlockPos unloadedWorld = unloadedLocal.toWorld(controller, Direction.NORTH);
        BlockPos wrongWorld = wrongLocal.toWorld(controller, Direction.NORTH);
        BlockPos blockedWorld = blockedLocal.toWorld(controller, Direction.NORTH);
        world.unloaded.add(unloadedWorld);
        world.blocks.put(wrongWorld, DIRT);
        world.blocks.put(blockedWorld, STONE);
        world.nonReplaceable.add(blockedWorld);
        world.blocks.put(controller, AIR);

        GatePatternMatcher.MatchResult result =
                GatePatternMatcher.match(world, controller, Direction.NORTH);

        assertEquals(4, result.mismatches().size());
        assertEquals(
                Set.of(
                        GatePatternMatcher.MismatchKind.UNLOADED_CHUNK,
                        GatePatternMatcher.MismatchKind.WRONG_BLOCK,
                        GatePatternMatcher.MismatchKind.INTERIOR_BLOCKED,
                        GatePatternMatcher.MismatchKind.MISSING_CONTROLLER),
                result.mismatches().stream()
                        .map(GatePatternMatcher.Mismatch::kind)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.mismatches().add(result.mismatches().getFirst()));
    }

    private static GatePatternMatcher.Mismatch onlyMismatch(
            GatePatternMatcher.MatchResult result) {
        assertEquals(1, result.mismatches().size());
        return result.mismatches().getFirst();
    }

    private static TestWorld exactGate(BlockPos controller, Direction facing) {
        TestWorld world = new TestWorld();
        for (int u = -3; u <= 3; u++) {
            for (int v = 0; v <= 8; v++) {
                GateLocalPos local = pos(u, v);
                BlockPos worldPosition = local.toWorld(controller, facing);
                boolean perimeter = u == -3 || u == 3 || v == 0 || v == 8;
                if (!perimeter) {
                    world.blocks.put(worldPosition, AIR);
                } else if (local.equals(pos(0, 0))) {
                    world.blocks.put(worldPosition, CONTROLLER);
                    world.nonReplaceable.add(worldPosition);
                } else if (SIGNAL_GLASS_POSITIONS.contains(local)) {
                    world.blocks.put(worldPosition, SIGNAL_GLASS);
                    world.nonReplaceable.add(worldPosition);
                } else {
                    world.blocks.put(worldPosition, FRAME);
                    world.nonReplaceable.add(worldPosition);
                }
            }
        }
        return world;
    }

    private static GateLocalPos pos(int u, int v) {
        return new GateLocalPos(u, v);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static final class TestWorld implements GatePatternMatcher.WorldView {
        private final Map<BlockPos, ResourceLocation> blocks = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private final Set<BlockPos> nonReplaceable = new HashSet<>();
        private final Set<BlockPos> readPositions = new HashSet<>();

        @Override
        public boolean isLoaded(BlockPos position) {
            return !unloaded.contains(position);
        }

        @Override
        public ResourceLocation blockId(BlockPos position) {
            readPositions.add(position);
            return blocks.getOrDefault(position, AIR);
        }

        @Override
        public boolean isReplaceable(BlockPos position) {
            readPositions.add(position);
            return !nonReplaceable.contains(position);
        }
    }
}
