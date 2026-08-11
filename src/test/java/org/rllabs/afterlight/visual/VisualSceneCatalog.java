package org.rllabs.afterlight.visual;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.rllabs.afterlight.gate.GatePattern;
import org.rllabs.afterlight.gate.GateState;
import org.rllabs.afterlight.relay.FarRelayKeys;
import org.rllabs.afterlight.relay.FarRelayStructurePlan;
import org.rllabs.afterlight.relay.RelaySite;

public final class VisualSceneCatalog {
    private static final double EXACT_POSITION_TOLERANCE = 0.05;
    private static final List<WorldScene> WORLD_SCENES = List.of(
            simple(
                    "echo-item-gui.png",
                    64.5,
                    101.0,
                    12.5,
                    block("platform", new BlockPos(64, 100, 12), "afterlight:relay_stone")),
            simple(
                    "echo-item-first-person.png",
                    64.5,
                    101.0,
                    12.5,
                    block("platform", new BlockPos(64, 100, 12), "afterlight:relay_stone")),
            simple(
                    "echo-item-third-person.png",
                    64.5,
                    101.0,
                    12.5,
                    block("platform", new BlockPos(64, 100, 12), "afterlight:relay_stone")),
            simple(
                    "echo-item-dropped.png",
                    72.5,
                    101.0,
                    8.5,
                    block("platform", new BlockPos(72, 100, 8), "afterlight:relay_stone"),
                    new AnchorRequirement(
                            "echo_item_entity",
                            AnchorType.ECHO_ITEM_ENTITY,
                            new BlockPos(72, 102, 0),
                            null,
                            null)),
            simple(
                    "echo-item-frame.png",
                    80.5,
                    101.0,
                    8.5,
                    block("frame_support", new BlockPos(80, 103, -1), "afterlight:relay_stone"),
                    new AnchorRequirement(
                            "echo_item_frame",
                            AnchorType.ECHO_ITEM_FRAME,
                            new BlockPos(80, 103, 0),
                            null,
                            null)),
            gate("gate-idle.png", -24.5, 101.0, new BlockPos(-24, 101, 0), GateState.IDLE),
            gate("gate-open.png", 0.5, 101.0, new BlockPos(0, 101, 0), GateState.OPEN),
            gate("gate-fault.png", 24.5, 101.0, new BlockPos(24, 101, 0), GateState.FAULT),
            relay("far-relay-arrival.png", 0.5, 80.0, 14.5, RelaySite.CENTRAL),
            relay("far-relay-central.png", 15.5, 82.0, 15.5, RelaySite.CENTRAL),
            relay("far-relay-east.png", 241.5, 82.0, 0.5, RelaySite.EAST),
            relay("far-relay-west.png", -240.5, 82.0, 0.5, RelaySite.WEST),
            relay("far-relay-north.png", 0.5, 82.0, -240.5, RelaySite.NORTH),
            relay("far-relay-south.png", 0.5, 82.0, 241.5, RelaySite.SOUTH),
            gate("far-relay-return.png", 0.5, 103.0, new BlockPos(0, 101, 0), GateState.OPEN));

    private VisualSceneCatalog() {}

    public static List<WorldScene> worldScenes() {
        return WORLD_SCENES;
    }

    public static WorldScene scene(String artifact) {
        return WORLD_SCENES.stream()
                .filter(scene -> scene.artifact().equals(artifact))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown visual scene: " + artifact));
    }

    private static WorldScene simple(
            String artifact,
            double x,
            double y,
            double z,
            AnchorRequirement... anchors) {
        return scene(
                artifact,
                Level.OVERWORLD,
                x,
                y,
                z,
                List.of(anchors),
                null,
                null,
                Set.of());
    }

    private static WorldScene gate(
            String artifact, double x, double y, BlockPos controller, GateState state) {
        List<AnchorRequirement> anchors = GatePattern.expected(Direction.SOUTH).entrySet().stream()
                .map(entry -> {
                    BlockPos position = entry.getKey().toWorld(controller, Direction.SOUTH);
                    String block = switch (entry.getValue()) {
                        case FRAME -> "afterlight:gate_frame";
                        case SIGNAL_GLASS -> "afterlight:signal_glass";
                        case CONTROLLER -> "afterlight:gate_controller";
                    };
                    AnchorType type = entry.getValue() == GatePattern.GatePart.CONTROLLER
                            ? AnchorType.GATE_CONTROLLER
                            : AnchorType.BLOCK;
                    return new AnchorRequirement(
                            "gate_"
                                    + entry.getValue().name().toLowerCase(java.util.Locale.ROOT)
                                    + "_"
                                    + position.getX()
                                    + "_"
                                    + position.getY()
                                    + "_"
                                    + position.getZ(),
                            type,
                            position,
                            block,
                            null);
                })
                .sorted(Comparator.comparing(AnchorRequirement::name))
                .toList();
        return scene(
                artifact,
                Level.OVERWORLD,
                x,
                y,
                14.5,
                anchors,
                state,
                null,
                Set.of());
    }

    private static WorldScene relay(
            String artifact, double x, double y, double z, RelaySite site) {
        List<AnchorRequirement> anchors = FarRelayStructurePlan.forSite(site).anchors().stream()
                .map(anchor -> new AnchorRequirement(
                        anchor.name(),
                        AnchorType.RELAY_ANCHOR,
                        new BlockPos(anchor.x(), anchor.y(), anchor.z()),
                        null,
                        site))
                .toList();
        Set<ChunkPos> structureChunks = new LinkedHashSet<>();
        FarRelayStructurePlan.forSite(site).placements().forEach(placement -> structureChunks.add(
                new ChunkPos(new BlockPos(
                        site.x() + placement.x(),
                        0,
                        site.z() + placement.z()))));
        return scene(
                artifact,
                FarRelayKeys.LEVEL,
                x,
                y,
                z,
                anchors,
                null,
                site,
                structureChunks);
    }

    private static WorldScene scene(
            String artifact,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            List<AnchorRequirement> anchors,
            GateState gateState,
            RelaySite relaySite,
            Set<ChunkPos> additionalChunks) {
        Set<ChunkPos> chunks = new LinkedHashSet<>(additionalChunks);
        chunks.add(new ChunkPos(BlockPos.containing(x, y, z)));
        anchors.stream()
                .filter(anchor -> anchor.type() != AnchorType.RELAY_ANCHOR)
                .map(AnchorRequirement::position)
                .map(ChunkPos::new)
                .forEach(chunks::add);
        return new WorldScene(
                artifact,
                dimension,
                x,
                y,
                z,
                EXACT_POSITION_TOLERANCE,
                anchors,
                gateState,
                relaySite,
                chunks);
    }

    private static AnchorRequirement block(String name, BlockPos position, String expectedBlock) {
        return new AnchorRequirement(name, AnchorType.BLOCK, position, expectedBlock, null);
    }

    public enum AnchorType {
        BLOCK,
        GATE_CONTROLLER,
        ECHO_ITEM_ENTITY,
        ECHO_ITEM_FRAME,
        RELAY_ANCHOR
    }

    public record AnchorRequirement(
            String name,
            AnchorType type,
            BlockPos position,
            String expectedBlock,
            RelaySite relaySite) {
        public AnchorRequirement {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(position, "position");
        }
    }

    public record WorldScene(
            String artifact,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            double coordinateTolerance,
            List<AnchorRequirement> anchorRequirements,
            GateState gateState,
            RelaySite relaySite,
            Set<ChunkPos> requiredChunks) {
        public WorldScene {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(dimension, "dimension");
            anchorRequirements = List.copyOf(anchorRequirements);
            requiredChunks = Set.copyOf(requiredChunks);
        }

        public ChunkPos cameraChunk() {
            return new ChunkPos(BlockPos.containing(x, y, z));
        }
    }
}
