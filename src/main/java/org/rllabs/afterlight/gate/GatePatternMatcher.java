package org.rllabs.afterlight.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class GatePatternMatcher {
    private static final ResourceLocation FRAME = id("gate_frame");
    private static final ResourceLocation SIGNAL_GLASS = id("signal_glass");
    private static final ResourceLocation CONTROLLER = id("gate_controller");

    private GatePatternMatcher() {}

    public static MatchResult match(
            LevelReader level, BlockPos controllerPosition, Direction facing) {
        return match(new LevelWorldView(level), controllerPosition, facing);
    }

    public static MatchResult match(
            WorldView world, BlockPos controllerPosition, Direction facing) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(controllerPosition, "controllerPosition");
        List<Mismatch> mismatches = new ArrayList<>();
        for (Map.Entry<GateLocalPos, GatePattern.GatePart> entry :
                GatePattern.expected(facing).entrySet()) {
            GateLocalPos localPosition = entry.getKey();
            BlockPos worldPosition = localPosition.toWorld(controllerPosition, facing);
            ResourceLocation expectedBlock = blockFor(entry.getValue());
            if (!world.isLoaded(worldPosition)) {
                mismatches.add(new Mismatch(
                        MismatchKind.UNLOADED_CHUNK,
                        localPosition,
                        worldPosition,
                        expectedBlock,
                        null));
                continue;
            }
            ResourceLocation actualBlock = world.blockId(worldPosition);
            if (entry.getValue() == GatePattern.GatePart.CONTROLLER) {
                if (!CONTROLLER.equals(actualBlock)) {
                    mismatches.add(new Mismatch(
                            MismatchKind.MISSING_CONTROLLER,
                            localPosition,
                            worldPosition,
                            CONTROLLER,
                            actualBlock));
                }
            } else if (CONTROLLER.equals(actualBlock)) {
                mismatches.add(new Mismatch(
                        MismatchKind.SECOND_CONTROLLER,
                        localPosition,
                        worldPosition,
                        expectedBlock,
                        actualBlock));
            } else if (!expectedBlock.equals(actualBlock)) {
                mismatches.add(new Mismatch(
                        MismatchKind.WRONG_BLOCK,
                        localPosition,
                        worldPosition,
                        expectedBlock,
                        actualBlock));
            }
        }
        for (GateLocalPos localPosition : GatePattern.interior(facing)) {
            BlockPos worldPosition = localPosition.toWorld(controllerPosition, facing);
            if (!world.isLoaded(worldPosition)) {
                mismatches.add(new Mismatch(
                        MismatchKind.UNLOADED_CHUNK,
                        localPosition,
                        worldPosition,
                        null,
                        null));
                continue;
            }
            ResourceLocation actualBlock = world.blockId(worldPosition);
            if (CONTROLLER.equals(actualBlock)) {
                mismatches.add(new Mismatch(
                        MismatchKind.SECOND_CONTROLLER,
                        localPosition,
                        worldPosition,
                        null,
                        actualBlock));
            } else if (!world.isReplaceable(worldPosition)) {
                mismatches.add(new Mismatch(
                        MismatchKind.INTERIOR_BLOCKED,
                        localPosition,
                        worldPosition,
                        null,
                        actualBlock));
            }
        }
        return new MatchResult(mismatches);
    }

    private static ResourceLocation blockFor(GatePattern.GatePart part) {
        if (part == GatePattern.GatePart.FRAME) {
            return FRAME;
        }
        if (part == GatePattern.GatePart.SIGNAL_GLASS) {
            return SIGNAL_GLASS;
        }
        return CONTROLLER;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }

    public enum MismatchKind {
        UNLOADED_CHUNK,
        WRONG_BLOCK,
        INTERIOR_BLOCKED,
        MISSING_CONTROLLER,
        SECOND_CONTROLLER
    }

    public record Mismatch(
            MismatchKind kind,
            GateLocalPos localPosition,
            BlockPos worldPosition,
            ResourceLocation expectedBlock,
            ResourceLocation actualBlock) {}

    public record MatchResult(List<Mismatch> mismatches) {
        public MatchResult {
            mismatches = List.copyOf(mismatches);
        }

        public boolean matches() {
            return mismatches.isEmpty();
        }
    }

    public interface WorldView {
        boolean isLoaded(BlockPos position);

        ResourceLocation blockId(BlockPos position);

        boolean isReplaceable(BlockPos position);
    }

    private record LevelWorldView(LevelReader level) implements WorldView {
        private LevelWorldView {
            Objects.requireNonNull(level, "level");
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return level.getChunk(
                            SectionPos.blockToSectionCoord(position.getX()),
                            SectionPos.blockToSectionCoord(position.getZ()),
                            ChunkStatus.FULL,
                            false)
                    != null;
        }

        @Override
        public ResourceLocation blockId(BlockPos position) {
            return level.getBlockState(position)
                    .getBlockHolder()
                    .unwrapKey()
                    .orElseThrow()
                    .location();
        }

        @Override
        public boolean isReplaceable(BlockPos position) {
            return level.getBlockState(position).canBeReplaced();
        }
    }
}
