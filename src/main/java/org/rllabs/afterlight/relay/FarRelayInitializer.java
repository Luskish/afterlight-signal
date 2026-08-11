package org.rllabs.afterlight.relay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

    private static void ensureSite(
            ServerLevel level, FarRelaySavedData data, RelaySite site) {
        if (data.isInitialized(site)) {
            return;
        }

        loadConstructionChunks(level, site);
        int platformY = findPlatformY(level, site);
        buildPlatform(level, site, platformY);
        placeLootChest(level, site, platformY);
        if (site == RelaySite.CENTRAL) {
            placeCentralBlocks(level, site, platformY);
        }
        if (!isComplete(level, site, platformY)) {
            throw new IllegalStateException("Far Relay site initialization incomplete: " + site);
        }
        data.markInitialized(site);
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

    private static boolean isSafeSurface(ServerLevel level, RelaySite site, int y) {
        if (y < level.getMinBuildHeight() || y + 2 >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos floor = new BlockPos(site.x(), y, site.z());
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(floor.above()).canBeReplaced()
                && level.getBlockState(floor.above(2)).canBeReplaced();
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

    private static void placeLootChest(ServerLevel level, RelaySite site, int platformY) {
        BlockPos position = chestPosition(site, platformY);
        level.setBlock(position, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
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

    private static boolean isComplete(ServerLevel level, RelaySite site, int platformY) {
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
                || chest.getLootTable() != FarRelayKeys.LOOT_TABLE) {
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
