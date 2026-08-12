package org.rllabs.afterlight.client;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateControllerBlockEntity;
import org.rllabs.afterlight.relay.FarRelayStructurePlan;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Material;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Placement;
import org.rllabs.afterlight.relay.RelaySite;
import org.rllabs.afterlight.relay.SignalTerminalBlock;
import org.rllabs.afterlight.visual.VisualSceneCatalog;
import org.rllabs.afterlight.visual.VisualSceneCatalog.AnchorRequirement;
import org.rllabs.afterlight.visual.VisualSceneCatalog.WorldScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;
import org.rllabs.afterlight.visual.VisualSceneReadiness.ExpectedScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness.ObservedScene;

final class VisualSceneProbe {
    private VisualSceneProbe() {}

    static SceneSnapshot inspect(Minecraft minecraft, WorldScene scene) {
        if (minecraft.level == null || minecraft.player == null) {
            return unavailable(scene);
        }
        ClientLevel level = minecraft.level;
        List<ChunkObservation> chunks = scene.requiredChunks().stream()
                .sorted(Comparator.comparingInt((ChunkPos chunk) -> chunk.x)
                        .thenComparingInt(chunk -> chunk.z))
                .map(chunk -> new ChunkObservation(
                        chunk.x, chunk.z, level.hasChunk(chunk.x, chunk.z)))
                .toList();
        Integer relayPlatformY = scene.relaySite() == null
                ? null
                : discoverRelayPlatformY(level, scene.relaySite());
        List<AnchorObservation> anchors = scene.anchorRequirements().stream()
                .map(requirement -> inspectAnchor(level, scene, requirement, relayPlatformY))
                .toList();
        String gateState = anchors.stream()
                .map(AnchorObservation::gateState)
                .filter(state -> state != null)
                .findFirst()
                .orElse(null);
        boolean rendererReady = minecraft.levelRenderer.hasRenderedAllSections()
                && anchors.stream().allMatch(anchor -> minecraft.levelRenderer.isSectionCompiled(
                        new BlockPos(anchor.x(), anchor.y(), anchor.z())));
        SceneSnapshot snapshot = new SceneSnapshot(
                scene,
                level.dimension().location().toString(),
                minecraft.player.getX(),
                minecraft.player.getY(),
                minecraft.player.getZ(),
                relayPlatformY,
                chunks,
                anchors,
                rendererReady,
                gateState,
                null);
        return snapshot.withEvaluation(VisualSceneReadiness.evaluate(
                snapshot.expected(), snapshot.observed()));
    }

    private static SceneSnapshot unavailable(WorldScene scene) {
        List<ChunkObservation> chunks = scene.requiredChunks().stream()
                .map(chunk -> new ChunkObservation(chunk.x, chunk.z, false))
                .toList();
        List<AnchorObservation> anchors = scene.anchorRequirements().stream()
                .map(anchor -> new AnchorObservation(
                        anchor.name(),
                        anchor.position().getX(),
                        anchor.position().getY(),
                        anchor.position().getZ(),
                        anchor.expectedBlock(),
                        null,
                        false,
                        null,
                        null,
                        false))
                .toList();
        SceneSnapshot snapshot = new SceneSnapshot(
                scene,
                "unavailable",
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                chunks,
                anchors,
                false,
                null,
                null);
        return snapshot.withEvaluation(VisualSceneReadiness.evaluate(
                snapshot.expected(), snapshot.observed()));
    }

    private static AnchorObservation inspectAnchor(
            ClientLevel level,
            WorldScene scene,
            AnchorRequirement requirement,
            Integer relayPlatformY) {
        return switch (requirement.type()) {
            case BLOCK -> inspectBlock(level, requirement, requirement.position(), null, null);
            case GATE_CONTROLLER -> inspectGateController(level, scene, requirement);
            case ECHO_ITEM_ENTITY -> inspectEchoItem(level, requirement);
            case ECHO_ITEM_FRAME -> inspectEchoFrame(level, requirement);
            case RELAY_ANCHOR -> inspectRelayAnchor(level, requirement, relayPlatformY);
        };
    }

    private static AnchorObservation inspectBlock(
            ClientLevel level,
            AnchorRequirement requirement,
            BlockPos position,
            String requiredBlockEntity,
            String gateState) {
        ChunkPos chunk = new ChunkPos(position);
        boolean loaded = level.hasChunk(chunk.x, chunk.z);
        BlockState state = loaded ? level.getBlockState(position) : Blocks.VOID_AIR.defaultBlockState();
        String actualBlock = loaded ? blockId(state) : null;
        BlockEntity blockEntity = loaded ? level.getBlockEntity(position) : null;
        String actualBlockEntity = blockEntity == null
                ? null
                : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
        boolean valid = loaded
                && requirement.expectedBlock().equals(actualBlock)
                && (requiredBlockEntity == null || requiredBlockEntity.equals(actualBlockEntity));
        return new AnchorObservation(
                requirement.name(),
                position.getX(),
                position.getY(),
                position.getZ(),
                requirement.expectedBlock(),
                actualBlock,
                loaded,
                actualBlockEntity,
                gateState,
                valid);
    }

    private static AnchorObservation inspectGateController(
            ClientLevel level, WorldScene scene, AnchorRequirement requirement) {
        BlockPos position = requirement.position();
        ChunkPos chunk = new ChunkPos(position);
        boolean loaded = level.hasChunk(chunk.x, chunk.z);
        BlockState state = loaded ? level.getBlockState(position) : Blocks.VOID_AIR.defaultBlockState();
        BlockEntity blockEntity = loaded ? level.getBlockEntity(position) : null;
        String actualGateState = blockEntity instanceof GateControllerBlockEntity controller
                ? controller.state().name()
                : null;
        String actualBlockEntity = blockEntity == null
                ? null
                : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
        String actualBlock = loaded ? blockId(state) : null;
        boolean valid = loaded
                && requirement.expectedBlock().equals(actualBlock)
                && blockEntity instanceof GateControllerBlockEntity
                && scene.gateState() != null
                && scene.gateState().name().equals(actualGateState);
        return new AnchorObservation(
                requirement.name(),
                position.getX(),
                position.getY(),
                position.getZ(),
                requirement.expectedBlock(),
                actualBlock,
                loaded,
                actualBlockEntity,
                actualGateState,
                valid);
    }

    private static AnchorObservation inspectEchoItem(
            ClientLevel level, AnchorRequirement requirement) {
        BlockPos position = requirement.position();
        ChunkPos chunk = new ChunkPos(position);
        boolean loaded = level.hasChunk(chunk.x, chunk.z);
        boolean valid = loaded && !level.getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(position).inflate(1.5, 2.0, 1.5),
                        item -> item.getItem().is(EchoContent.ECHO.get()))
                .isEmpty();
        return new AnchorObservation(
                requirement.name(),
                position.getX(),
                position.getY(),
                position.getZ(),
                "afterlight:echo item entity",
                valid ? "afterlight:echo item entity" : null,
                loaded,
                null,
                null,
                valid);
    }

    private static AnchorObservation inspectEchoFrame(
            ClientLevel level, AnchorRequirement requirement) {
        BlockPos position = requirement.position();
        ChunkPos chunk = new ChunkPos(position);
        boolean loaded = level.hasChunk(chunk.x, chunk.z);
        boolean valid = loaded && !level.getEntitiesOfClass(
                        ItemFrame.class,
                        new AABB(position).inflate(1.0),
                        frame -> frame.getItem().is(EchoContent.ECHO.get()))
                .isEmpty();
        return new AnchorObservation(
                requirement.name(),
                position.getX(),
                position.getY(),
                position.getZ(),
                "afterlight:echo item frame",
                valid ? "afterlight:echo item frame" : null,
                loaded,
                null,
                null,
                valid);
    }

    private static AnchorObservation inspectRelayAnchor(
            ClientLevel level, AnchorRequirement requirement, Integer platformY) {
        RelaySite site = requirement.relaySite();
        Placement placement = FarRelayStructurePlan.forSite(site)
                .placementAt(
                        requirement.position().getX(),
                        requirement.position().getY(),
                        requirement.position().getZ())
                .orElseThrow();
        BlockPos position = platformY == null
                ? new BlockPos(
                        site.x() + placement.x(),
                        level.getMinBuildHeight() + placement.y(),
                        site.z() + placement.z())
                : FarRelayStructurePlan.worldPosition(site, platformY, placement);
        String expectedBlock = expectedBlock(placement.material());
        ChunkPos chunk = new ChunkPos(position);
        boolean loaded = platformY != null && level.hasChunk(chunk.x, chunk.z);
        BlockState state = loaded ? level.getBlockState(position) : Blocks.VOID_AIR.defaultBlockState();
        BlockEntity blockEntity = loaded ? level.getBlockEntity(position) : null;
        String actualBlock = loaded ? blockId(state) : null;
        String actualBlockEntity = blockEntity == null
                ? null
                : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
        boolean exactTerminalState = !isTerminal(placement.material())
                || state.hasProperty(SignalTerminalBlock.FACING)
                        && state.hasProperty(SignalTerminalBlock.ACTIVE)
                        && state.getValue(SignalTerminalBlock.FACING) == placement.facing()
                        && state.getValue(SignalTerminalBlock.ACTIVE) == placement.active();
        boolean valid = loaded
                && expectedBlock.equals(actualBlock)
                && exactTerminalState
                && (placement.material() != Material.LOOT_CHEST
                        || blockEntity instanceof ChestBlockEntity);
        return new AnchorObservation(
                requirement.name(),
                position.getX(),
                position.getY(),
                position.getZ(),
                expectedBlock,
                actualBlock,
                loaded,
                actualBlockEntity,
                null,
                valid);
    }

    private static Integer discoverRelayPlatformY(ClientLevel level, RelaySite site) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(site.x(), 0, site.z() + 3);
        ChunkPos chunk = new ChunkPos(position);
        if (!level.hasChunk(chunk.x, chunk.z)) {
            return null;
        }
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            position.setY(y);
            if (level.getBlockState(position).is(Blocks.CHEST)
                    && level.getBlockEntity(position) instanceof ChestBlockEntity) {
                return y - 1;
            }
        }
        return null;
    }

    private static String expectedBlock(Material material) {
        return switch (material) {
            case RELAY_STONE -> "afterlight:relay_stone";
            case GATE_FRAME -> "afterlight:gate_frame";
            case SIGNAL_GLASS -> "afterlight:signal_glass";
            case RETURN_TERMINAL -> "afterlight:return_terminal";
            case FUTURE_CONSOLE -> "afterlight:future_console";
            case LOOT_CHEST -> "minecraft:chest";
            case POLISHED_BLACKSTONE_BRICKS -> "minecraft:polished_blackstone_bricks";
            case POLISHED_BLACKSTONE_BRICK_WALL -> "minecraft:polished_blackstone_brick_wall";
            case SOUL_LANTERN -> "minecraft:soul_lantern";
        };
    }

    private static boolean isTerminal(Material material) {
        return material == Material.RETURN_TERMINAL || material == Material.FUTURE_CONSOLE;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    record ChunkObservation(int x, int z, boolean loaded) {
        String key() {
            return x + "," + z;
        }
    }

    record AnchorObservation(
            String name,
            int x,
            int y,
            int z,
            String expectedBlock,
            String actualBlock,
            boolean chunkLoaded,
            String blockEntity,
            String gateState,
            boolean valid) {}

    record SceneSnapshot(
            WorldScene scene,
            String dimension,
            double x,
            double y,
            double z,
            Integer relayPlatformY,
            List<ChunkObservation> chunks,
            List<AnchorObservation> anchors,
            boolean rendererReady,
            String gateState,
            Evaluation evaluation) {
        SceneSnapshot {
            chunks = List.copyOf(chunks);
            anchors = List.copyOf(anchors);
        }

        SceneSnapshot withEvaluation(Evaluation updatedEvaluation) {
            return new SceneSnapshot(
                    scene,
                    dimension,
                    x,
                    y,
                    z,
                    relayPlatformY,
                    chunks,
                    anchors,
                    rendererReady,
                    gateState,
                    updatedEvaluation);
        }

        ExpectedScene expected() {
            return new ExpectedScene(
                    scene.dimension().location().toString(),
                    scene.x(),
                    scene.y(),
                    scene.z(),
                    scene.coordinateTolerance(),
                    scene.requiredChunks().stream()
                            .map(chunk -> chunk.x + "," + chunk.z)
                            .collect(java.util.stream.Collectors.toSet()),
                    scene.anchorRequirements().stream()
                            .map(AnchorRequirement::name)
                            .collect(java.util.stream.Collectors.toSet()),
                    scene.gateState() == null ? null : scene.gateState().name());
        }

        ObservedScene observed() {
            Map<String, Boolean> observedChunks = new LinkedHashMap<>();
            chunks.forEach(chunk -> observedChunks.put(chunk.key(), chunk.loaded()));
            Map<String, Boolean> observedAnchors = new LinkedHashMap<>();
            anchors.forEach(anchor -> observedAnchors.put(anchor.name(), anchor.valid()));
            return new ObservedScene(
                    dimension,
                    x,
                    y,
                    z,
                    observedChunks,
                    observedAnchors,
                    rendererReady,
                    gateState);
        }
    }
}
