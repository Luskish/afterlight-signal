package org.rllabs.afterlight.gate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.relay.FarRelaySavedData;
import org.rllabs.afterlight.relay.FarRelayStructurePlan;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Material;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Placement;
import org.rllabs.afterlight.relay.SignalTerminalBlock;
import org.rllabs.afterlight.visual.VisualSceneCatalog;
import org.rllabs.afterlight.visual.VisualSceneCatalog.AnchorRequirement;
import org.rllabs.afterlight.visual.VisualSceneCatalog.WorldScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Failure;

final class VisualAcceptanceServerSceneValidator {
    private VisualAcceptanceServerSceneValidator() {}

    static List<Evaluation> evaluate(MinecraftServer server) {
        List<Evaluation> evaluations = new ArrayList<>();
        for (WorldScene scene : VisualSceneCatalog.worldScenes()) {
            EnumSet<Failure> failures = EnumSet.noneOf(Failure.class);
            ServerLevel level = server.getLevel(scene.dimension());
            if (level == null || !level.dimension().equals(scene.dimension())) {
                failures.add(Failure.DIMENSION);
                failures.add(Failure.CHUNKS);
                failures.add(Failure.ANCHORS);
                if (scene.gateState() != null) {
                    failures.add(Failure.GATE_STATE);
                }
            } else {
                if (!scene.requiredChunks().stream()
                        .allMatch(chunk -> level.getChunkSource().hasChunk(chunk.x, chunk.z))) {
                    failures.add(Failure.CHUNKS);
                }
                if (!anchorsValid(level, scene)) {
                    failures.add(Failure.ANCHORS);
                }
                if (scene.gateState() != null && !gateStateValid(level, scene)) {
                    failures.add(Failure.GATE_STATE);
                }
            }
            evaluations.add(new Evaluation(failures.isEmpty(), failures));
        }
        return List.copyOf(evaluations);
    }

    private static boolean anchorsValid(ServerLevel level, WorldScene scene) {
        Integer platformY = scene.relaySite() == null
                ? null
                : FarRelaySavedData.get(level).platformY(scene.relaySite()).orElse(Integer.MIN_VALUE);
        for (AnchorRequirement requirement : scene.anchorRequirements()) {
            if (!anchorValid(level, scene, requirement, platformY)) {
                return false;
            }
        }
        return true;
    }

    private static boolean anchorValid(
            ServerLevel level,
            WorldScene scene,
            AnchorRequirement requirement,
            Integer platformY) {
        return switch (requirement.type()) {
            case BLOCK -> blockMatches(level, requirement.position(), requirement.expectedBlock());
            case GATE_CONTROLLER -> blockMatches(
                            level, requirement.position(), requirement.expectedBlock())
                    && level.getBlockEntity(requirement.position())
                            instanceof GateControllerBlockEntity controller
                    && controller.state() == scene.gateState();
            case ECHO_ITEM_ENTITY -> !level.getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(requirement.position()).inflate(1.5, 2.0, 1.5),
                            item -> item.getItem().is(EchoContent.ECHO.get()))
                    .isEmpty();
            case ECHO_ITEM_FRAME -> !level.getEntitiesOfClass(
                            ItemFrame.class,
                            new AABB(requirement.position()).inflate(1.0),
                            frame -> frame.getItem().is(EchoContent.ECHO.get()))
                    .isEmpty();
            case RELAY_ANCHOR -> relayAnchorValid(level, requirement, platformY);
        };
    }

    private static boolean relayAnchorValid(
            ServerLevel level, AnchorRequirement requirement, Integer platformY) {
        if (platformY == null || platformY == Integer.MIN_VALUE) {
            return false;
        }
        Placement placement = FarRelayStructurePlan.forSite(requirement.relaySite())
                .placementAt(
                        requirement.position().getX(),
                        requirement.position().getY(),
                        requirement.position().getZ())
                .orElseThrow();
        BlockPos position = FarRelayStructurePlan.worldPosition(
                requirement.relaySite(), platformY, placement);
        BlockState state = level.getBlockState(position);
        if (!expectedBlock(placement.material()).equals(blockId(state))) {
            return false;
        }
        if (placement.material() == Material.LOOT_CHEST
                && !(level.getBlockEntity(position) instanceof ChestBlockEntity)) {
            return false;
        }
        return !isTerminal(placement.material())
                || state.hasProperty(SignalTerminalBlock.FACING)
                        && state.getValue(SignalTerminalBlock.FACING) == placement.facing()
                        && state.getValue(SignalTerminalBlock.ACTIVE) == placement.active();
    }

    private static boolean gateStateValid(ServerLevel level, WorldScene scene) {
        return scene.anchorRequirements().stream()
                .filter(anchor -> anchor.type() == VisualSceneCatalog.AnchorType.GATE_CONTROLLER)
                .map(AnchorRequirement::position)
                .map(level::getBlockEntity)
                .filter(GateControllerBlockEntity.class::isInstance)
                .map(GateControllerBlockEntity.class::cast)
                .anyMatch(controller -> controller.state() == scene.gateState());
    }

    private static boolean blockMatches(ServerLevel level, BlockPos position, String expected) {
        ChunkPos chunk = new ChunkPos(position);
        return level.getChunkSource().hasChunk(chunk.x, chunk.z)
                && expected.equals(blockId(level.getBlockState(position)));
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
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
}
