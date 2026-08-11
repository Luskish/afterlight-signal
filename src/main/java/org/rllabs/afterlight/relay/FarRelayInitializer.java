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

public final class FarRelayInitializer {
    private static final int PLATFORM_RADIUS = 5;
    private static final int CONSTRUCTION_RADIUS = 7;
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

    private static void ensureSite(
            ServerLevel level, FarRelaySavedData data, RelaySite site) {
        loadConstructionChunks(level, site);
        if (data.isInitialized(site)) {
            int platformY = data.platformY(site).orElseGet(() -> rediscoverPlatformY(level, site)
                    .orElseGet(() -> findPlatformY(level, site)));
            repairMarkedSite(level, site, platformY);
            if (!isComplete(level, site, platformY, false)) {
                throw new IllegalStateException(
                        "Far Relay marked site repair blocked: "
                                + site
                                + " at Y "
                                + platformY
                                + ", "
                                + firstIncompleteRequirement(level, site, platformY));
            }
            data.markInitialized(site, platformY);
            return;
        }

        int platformY = findPlatformY(level, site);
        buildPlatform(level, site, platformY);
        placeLootChest(level, site, platformY);
        if (site == RelaySite.CENTRAL) {
            placeCentralBlocks(level, site, platformY);
        }
        if (!isComplete(level, site, platformY, true)) {
            throw new IllegalStateException("Far Relay site initialization incomplete: " + site);
        }
        data.markInitialized(site, platformY);
    }

    private static void loadConstructionChunks(ServerLevel level, RelaySite site) {
        int minChunkX = (site.x() - CONSTRUCTION_RADIUS) >> 4;
        int maxChunkX = (site.x() + CONSTRUCTION_RADIUS) >> 4;
        int minChunkZ = (site.z() - CONSTRUCTION_RADIUS) >> 4;
        int maxChunkZ = (site.z() + CONSTRUCTION_RADIUS) >> 4;
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

    private static int platformEvidenceScore(
            ServerLevel level, RelaySite site, int platformY) {
        if (platformY < level.getMinBuildHeight()
                || platformY + 2 >= level.getMaxBuildHeight()) {
            return 0;
        }
        int score = 0;
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
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

    private static void buildPlatform(ServerLevel level, RelaySite site, int platformY) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
                level.setBlock(
                        floor,
                        EchoContent.RELAY_STONE.get().defaultBlockState(),
                        Block.UPDATE_ALL);
                level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void repairMarkedSite(
            ServerLevel level, RelaySite site, int platformY) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
                replaceIfMissingOrReplaceable(
                        level, floor, EchoContent.RELAY_STONE.get().defaultBlockState());
                BlockPos above = floor.above();
                if (!isRequiredSiteBlock(site, platformY, above)) {
                    clearIfReplaceable(level, above);
                }
                clearIfReplaceable(level, floor.above(2));
            }
        }
        boolean placedChest = repairRequiredBlock(
                level,
                chestPosition(site, platformY),
                Blocks.CHEST.defaultBlockState());
        if (placedChest) {
            configureLootChest(level, site, platformY);
        }
        if (site == RelaySite.CENTRAL) {
            repairRequiredBlock(
                    level,
                    new BlockPos(site.x() + 3, platformY + 1, site.z()),
                    EchoContent.RETURN_TERMINAL.get().defaultBlockState());
            repairRequiredBlock(
                    level,
                    new BlockPos(site.x() - 3, platformY + 1, site.z()),
                    EchoContent.FUTURE_CONSOLE.get().defaultBlockState());
        }
    }

    private static boolean replaceIfMissingOrReplaceable(
            ServerLevel level,
            BlockPos position,
            BlockState required) {
        BlockState current = level.getBlockState(position);
        if (current.is(required.getBlock())) {
            return false;
        }
        if (current.canBeReplaced()) {
            level.setBlock(position, required, Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private static boolean repairRequiredBlock(
            ServerLevel level,
            BlockPos position,
            BlockState required) {
        return replaceIfMissingOrReplaceable(level, position, required);
    }

    private static void clearIfReplaceable(ServerLevel level, BlockPos position) {
        if (!level.getBlockState(position).isAir()
                && level.getBlockState(position).canBeReplaced()) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void placeLootChest(ServerLevel level, RelaySite site, int platformY) {
        BlockPos position = chestPosition(site, platformY);
        level.setBlock(position, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        configureLootChest(level, site, platformY);
    }

    private static void configureLootChest(ServerLevel level, RelaySite site, int platformY) {
        BlockPos position = chestPosition(site, platformY);
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setLootTable(FarRelayKeys.LOOT_TABLE);
            chest.setLootTableSeed(0xA17E0000L + site.ordinal());
            chest.setChanged();
        }
    }

    private static void placeCentralBlocks(ServerLevel level, RelaySite site, int platformY) {
        level.setBlock(
                new BlockPos(site.x() + 3, platformY + 1, site.z()),
                EchoContent.RETURN_TERMINAL.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                new BlockPos(site.x() - 3, platformY + 1, site.z()),
                EchoContent.FUTURE_CONSOLE.get().defaultBlockState(),
                Block.UPDATE_ALL);
    }

    private static boolean isComplete(
            ServerLevel level,
            RelaySite site,
            int platformY,
            boolean requirePendingLoot) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
                if (!level.getBlockState(floor).is(EchoContent.RELAY_STONE.get())) {
                    return false;
                }
                BlockPos above = floor.above();
                if (!isRequiredSiteBlock(site, platformY, above)
                        && !level.getBlockState(above).isAir()) {
                    return false;
                }
                if (!level.getBlockState(floor.above(2)).isAir()) {
                    return false;
                }
            }
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        if (!(blockEntity instanceof ChestBlockEntity chest)
                || requirePendingLoot && chest.getLootTable() != FarRelayKeys.LOOT_TABLE) {
            return false;
        }
        if (site != RelaySite.CENTRAL) {
            return true;
        }
        return level.getBlockState(new BlockPos(site.x() + 3, platformY + 1, site.z()))
                        .is(EchoContent.RETURN_TERMINAL.get())
                && level.getBlockState(new BlockPos(site.x() - 3, platformY + 1, site.z()))
                        .is(EchoContent.FUTURE_CONSOLE.get());
    }

    private static String firstIncompleteRequirement(
            ServerLevel level, RelaySite site, int platformY) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
                if (!level.getBlockState(floor).is(EchoContent.RELAY_STONE.get())) {
                    return "platform=" + floor + " state=" + level.getBlockState(floor);
                }
                BlockPos above = floor.above();
                if (!isRequiredSiteBlock(site, platformY, above)
                        && !level.getBlockState(above).isAir()) {
                    return "clearance=" + above + " state=" + level.getBlockState(above);
                }
                if (!level.getBlockState(floor.above(2)).isAir()) {
                    return "headroom=" + floor.above(2)
                            + " state="
                            + level.getBlockState(floor.above(2));
                }
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(chestPosition(site, platformY));
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            return "chest=" + chestPosition(site, platformY) + " state="
                    + level.getBlockState(chestPosition(site, platformY));
        }
        if (site == RelaySite.CENTRAL
                && !level.getBlockState(new BlockPos(site.x() + 3, platformY + 1, site.z()))
                        .is(EchoContent.RETURN_TERMINAL.get())) {
            return "return_terminal_missing";
        }
        return "future_console_missing";
    }

    private static boolean isRequiredSiteBlock(
            RelaySite site, int platformY, BlockPos position) {
        if (position.equals(chestPosition(site, platformY))) {
            return true;
        }
        return site == RelaySite.CENTRAL
                && (position.equals(new BlockPos(site.x() + 3, platformY + 1, site.z()))
                        || position.equals(new BlockPos(
                                site.x() - 3, platformY + 1, site.z())));
    }

    private static BlockPos chestPosition(RelaySite site, int platformY) {
        return new BlockPos(site.x(), platformY + 1, site.z() + 3);
    }
}
