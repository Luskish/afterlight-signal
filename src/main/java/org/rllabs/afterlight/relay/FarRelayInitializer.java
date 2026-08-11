package org.rllabs.afterlight.relay;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Material;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Placement;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Plan;

public final class FarRelayInitializer {
    private static final int PLATFORM_RADIUS = 5;
    private static final int SURFACE_CENTER_Y = 64;
    private static final int SURFACE_SEARCH_RADIUS = 32;
    private static final int FALLBACK_PLATFORM_Y = 72;

    private FarRelayInitializer() {}

    public static void ensureAll(ServerLevel level) {
        FarRelaySavedData data = FarRelaySavedData.get(level);
        for (RelaySite site : RelaySite.values()) {
            ensureSite(level, data, site);
        }
    }

    public static Optional<BlockPos> centralArrival(ServerLevel level) {
        for (int distance = 0; distance <= SURFACE_SEARCH_RADIUS; distance++) {
            int above = SURFACE_CENTER_Y + distance;
            Optional<BlockPos> arrival = centralArrivalAt(level, above);
            if (arrival.isPresent()) {
                return arrival;
            }
            int below = SURFACE_CENTER_Y - distance;
            if (distance > 0) {
                arrival = centralArrivalAt(level, below);
                if (arrival.isPresent()) {
                    return arrival;
                }
            }
        }
        return centralArrivalAt(level, FALLBACK_PLATFORM_Y);
    }

    private static void ensureSite(ServerLevel level, FarRelaySavedData data, RelaySite site) {
        Plan plan = FarRelayStructurePlan.forSite(site);
        loadConstructionChunks(level, site, plan.constructionRadius());
        if (data.isInitialized(site)) {
            int platformY = data.platformY(site).orElseGet(() -> rediscoverPlatformY(level, site)
                    .orElseGet(() -> findPlatformY(level, site)));
            boolean legacyPresentation = data.presentationVersion(site)
                    < FarRelayStructurePlan.PRESENTATION_VERSION;
            repairMarkedSite(level, site, platformY, plan, legacyPresentation);
            if (!isComplete(level, site, platformY, plan, false, false)) {
                throw new IllegalStateException(
                        "Far Relay marked site repair blocked: "
                                + site
                                + " at Y "
                                + platformY
                                + ", "
                                + firstIncompleteRequirement(level, site, platformY, plan, false));
            }
            data.markInitialized(site, platformY);
            data.markPresented(site, FarRelayStructurePlan.PRESENTATION_VERSION);
            return;
        }

        int platformY = findPlatformY(level, site);
        buildSite(level, site, platformY, plan);
        if (!isComplete(level, site, platformY, plan, true, true)) {
            throw new IllegalStateException(
                    "Far Relay site initialization incomplete: "
                            + site
                            + ", "
                            + firstIncompleteRequirement(level, site, platformY, plan, true));
        }
        data.markInitialized(site, platformY);
        data.markPresented(site, FarRelayStructurePlan.PRESENTATION_VERSION);
    }

    private static void loadConstructionChunks(
            ServerLevel level, RelaySite site, int constructionRadius) {
        int minChunkX = (site.x() - constructionRadius) >> 4;
        int maxChunkX = (site.x() + constructionRadius) >> 4;
        int minChunkZ = (site.z() - constructionRadius) >> 4;
        int maxChunkZ = (site.z() + constructionRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static int findPlatformY(ServerLevel level, RelaySite site) {
        for (int distance = 0; distance <= SURFACE_SEARCH_RADIUS; distance++) {
            int above = SURFACE_CENTER_Y + distance;
            if (isSafeSurface(level, site, above)) {
                return above;
            }
            int below = SURFACE_CENTER_Y - distance;
            if (distance > 0 && isSafeSurface(level, site, below)) {
                return below;
            }
        }
        return FALLBACK_PLATFORM_Y;
    }

    private static OptionalInt rediscoverPlatformY(ServerLevel level, RelaySite site) {
        int bestY = 0;
        int bestScore = 0;
        for (int distance = 0; distance <= SURFACE_SEARCH_RADIUS; distance++) {
            int above = SURFACE_CENTER_Y + distance;
            int aboveScore = platformEvidenceScore(level, site, above);
            if (aboveScore > bestScore) {
                bestY = above;
                bestScore = aboveScore;
            }
            if (distance > 0) {
                int below = SURFACE_CENTER_Y - distance;
                int belowScore = platformEvidenceScore(level, site, below);
                if (belowScore > bestScore) {
                    bestY = below;
                    bestScore = belowScore;
                }
            }
        }
        int fallbackScore = platformEvidenceScore(level, site, FALLBACK_PLATFORM_Y);
        if (fallbackScore > bestScore) {
            bestY = FALLBACK_PLATFORM_Y;
            bestScore = fallbackScore;
        }
        return bestScore == 0 ? OptionalInt.empty() : OptionalInt.of(bestY);
    }

    private static int platformEvidenceScore(ServerLevel level, RelaySite site, int platformY) {
        if (platformY < level.getMinBuildHeight()
                || platformY + 2 >= level.getMaxBuildHeight()) {
            return 0;
        }
        int score = 0;
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(site.x() + deltaX, platformY, site.z() + deltaZ);
                if (level.getBlockState(floor).is(EchoContent.RELAY_STONE.get())) {
                    score++;
                }
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        if (blockEntity instanceof ChestBlockEntity chest
                && chest.getLootTable() == FarRelayKeys.LOOT_TABLE) {
            score += 256;
        }
        if (site == RelaySite.CENTRAL) {
            if (level.getBlockState(new BlockPos(site.x() + 3, platformY + 1, site.z()))
                    .is(EchoContent.RETURN_TERMINAL.get())) {
                score += 256;
            }
            if (level.getBlockState(new BlockPos(site.x() - 3, platformY + 1, site.z()))
                    .is(EchoContent.FUTURE_CONSOLE.get())) {
                score += 256;
            }
        }
        return score;
    }

    private static boolean isSafeSurface(ServerLevel level, RelaySite site, int y) {
        if (y < level.getMinBuildHeight() || y + 2 >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos floor = new BlockPos(site.x(), y, site.z());
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(floor.above()).canBeReplaced()
                && level.getBlockState(floor.above(2)).canBeReplaced();
    }

    private static Optional<BlockPos> centralArrivalAt(ServerLevel level, int floorY) {
        RelaySite central = RelaySite.CENTRAL;
        for (int distance = 0; distance <= PLATFORM_RADIUS; distance++) {
            for (int deltaX = -distance; deltaX <= distance; deltaX++) {
                for (int deltaZ = -distance; deltaZ <= distance; deltaZ++) {
                    if (Math.max(Math.abs(deltaX), Math.abs(deltaZ)) != distance) {
                        continue;
                    }
                    BlockPos floor = new BlockPos(
                            central.x() + deltaX, floorY, central.z() + deltaZ);
                    BlockPos feet = floor.above();
                    if (level.getBlockState(floor).is(EchoContent.RELAY_STONE.get())
                            && level.getBlockState(feet)
                                    .getCollisionShape(level, feet)
                                    .isEmpty()
                            && level.getFluidState(feet).isEmpty()
                            && level.getBlockState(feet.above())
                                    .getCollisionShape(level, feet.above())
                                    .isEmpty()
                            && level.getFluidState(feet.above()).isEmpty()) {
                        return Optional.of(feet);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static void buildSite(ServerLevel level, RelaySite site, int platformY, Plan plan) {
        clearPlannedHeadroom(level, site, platformY, plan, false);
        for (Placement placement : plan.placements()) {
            BlockPos position = FarRelayStructurePlan.worldPosition(site, platformY, placement);
            level.setBlock(position, stateFor(placement), Block.UPDATE_ALL);
        }
        configureLootChest(level, site, platformY);
    }

    private static void repairMarkedSite(
            ServerLevel level,
            RelaySite site,
            int platformY,
            Plan plan,
            boolean legacyPresentation) {
        clearPlannedHeadroom(level, site, platformY, plan, true);
        for (Placement placement : plan.placements()) {
            repairPlacement(level, site, platformY, placement, legacyPresentation);
        }
    }

    private static void clearPlannedHeadroom(
            ServerLevel level,
            RelaySite site,
            int platformY,
            Plan plan,
            boolean preserveNonReplaceable) {
        for (Placement placement : plan.placements()) {
            if (placement.y() != 0 || placement.material() != Material.RELAY_STONE) {
                continue;
            }
            for (int y = 1; y <= 2; y++) {
                if (plan.placementAt(placement.x(), y, placement.z()).isPresent()) {
                    continue;
                }
                BlockPos position = new BlockPos(
                        site.x() + placement.x(),
                        platformY + y,
                        site.z() + placement.z());
                BlockState current = level.getBlockState(position);
                if (!current.isAir()
                        && (!preserveNonReplaceable || current.canBeReplaced())) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void repairPlacement(
            ServerLevel level,
            RelaySite site,
            int platformY,
            Placement placement,
            boolean legacyPresentation) {
        BlockPos position = FarRelayStructurePlan.worldPosition(site, platformY, placement);
        BlockState required = stateFor(placement);
        BlockState current = level.getBlockState(position);
        if (current.is(required.getBlock())) {
            if (legacyPresentation
                    && isTerminal(placement.material())
                    && current.equals(required.getBlock().defaultBlockState())
                    && !current.equals(required)) {
                level.setBlock(position, required, Block.UPDATE_ALL);
            }
            return;
        }
        if (!current.canBeReplaced()) {
            return;
        }
        level.setBlock(position, required, Block.UPDATE_ALL);
        if (placement.material() == Material.LOOT_CHEST) {
            configureLootChest(level, site, platformY);
        }
    }

    private static boolean isComplete(
            ServerLevel level,
            RelaySite site,
            int platformY,
            Plan plan,
            boolean requirePendingLoot,
            boolean requireExactTerminalState) {
        for (Placement placement : plan.placements()) {
            BlockPos position = FarRelayStructurePlan.worldPosition(site, platformY, placement);
            BlockState required = stateFor(placement);
            BlockState actual = level.getBlockState(position);
            if (!actual.is(required.getBlock())) {
                return false;
            }
            if (requireExactTerminalState
                    && isTerminal(placement.material())
                    && !actual.equals(required)) {
                return false;
            }
        }
        for (Placement placement : plan.placements()) {
            if (placement.y() != 0 || placement.material() != Material.RELAY_STONE) {
                continue;
            }
            for (int y = 1; y <= 2; y++) {
                if (plan.placementAt(placement.x(), y, placement.z()).isEmpty()
                        && !level.getBlockState(new BlockPos(
                                        site.x() + placement.x(),
                                        platformY + y,
                                        site.z() + placement.z()))
                                .isAir()) {
                    return false;
                }
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        return blockEntity instanceof ChestBlockEntity chest
                && (!requirePendingLoot || chest.getLootTable() == FarRelayKeys.LOOT_TABLE);
    }

    private static String firstIncompleteRequirement(
            ServerLevel level,
            RelaySite site,
            int platformY,
            Plan plan,
            boolean requirePendingLoot) {
        for (Placement placement : plan.placements()) {
            BlockPos position = FarRelayStructurePlan.worldPosition(site, platformY, placement);
            BlockState required = stateFor(placement);
            BlockState actual = level.getBlockState(position);
            if (!actual.is(required.getBlock())) {
                return "anchor="
                        + placement.material()
                        + " position="
                        + position
                        + " state="
                        + actual;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return "chest="
                    + chestPosition(site, platformY)
                    + " state="
                    + level.getBlockState(chestPosition(site, platformY));
        }
        if (requirePendingLoot && chest.getLootTable() != FarRelayKeys.LOOT_TABLE) {
            return "chest_loot_table=" + chest.getLootTable();
        }
        return "platform_clearance";
    }

    private static BlockState stateFor(Placement placement) {
        return switch (placement.material()) {
            case RELAY_STONE -> EchoContent.RELAY_STONE.get().defaultBlockState();
            case GATE_FRAME -> EchoContent.GATE_FRAME.get().defaultBlockState();
            case SIGNAL_GLASS -> EchoContent.SIGNAL_GLASS.get().defaultBlockState();
            case RETURN_TERMINAL -> EchoContent.RETURN_TERMINAL
                    .get()
                    .defaultBlockState()
                    .setValue(SignalTerminalBlock.FACING, placement.facing())
                    .setValue(SignalTerminalBlock.ACTIVE, placement.active());
            case FUTURE_CONSOLE -> EchoContent.FUTURE_CONSOLE
                    .get()
                    .defaultBlockState()
                    .setValue(SignalTerminalBlock.FACING, placement.facing())
                    .setValue(SignalTerminalBlock.ACTIVE, placement.active());
            case LOOT_CHEST -> Blocks.CHEST.defaultBlockState();
            case POLISHED_BLACKSTONE_BRICKS -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case POLISHED_BLACKSTONE_BRICK_WALL ->
                Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
            case SOUL_LANTERN -> Blocks.SOUL_LANTERN.defaultBlockState();
        };
    }

    private static boolean isTerminal(Material material) {
        return material == Material.RETURN_TERMINAL || material == Material.FUTURE_CONSOLE;
    }

    private static void configureLootChest(ServerLevel level, RelaySite site, int platformY) {
        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setLootTable(FarRelayKeys.LOOT_TABLE);
            chest.setLootTableSeed(0xA17E0000L + site.ordinal());
            chest.setChanged();
        }
    }

    private static BlockPos chestPosition(RelaySite site, int platformY) {
        return new BlockPos(site.x(), platformY + 1, site.z() + 3);
    }
}
