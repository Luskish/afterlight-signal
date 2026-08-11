package org.rllabs.afterlight.relay;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;

@GameTestHolder(Afterlight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarRelayGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final int PLATFORM_RADIUS = 5;
    private static final int CONSTRUCTION_RADIUS = 7;

    private FarRelayGameTests() {}

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void initializerBuildsEverySiteExactlyOnce(GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        helper.assertTrue(
                relay.registryAccess()
                        .registryOrThrow(Registries.DIMENSION_TYPE)
                        .containsKey(FarRelayKeys.DIMENSION_TYPE.location()),
                "Far Relay dimension type did not load");
        helper.assertTrue(
                relay.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .containsKey(FarRelayKeys.BIOME.location()),
                "Far Relay biome did not load");
        helper.assertTrue(
                relay.registryAccess()
                        .registryOrThrow(Registries.NOISE_SETTINGS)
                        .containsKey(FarRelayKeys.NOISE_SETTINGS.location()),
                "Far Relay noise settings did not load");
        FarRelaySavedData initialData = FarRelaySavedData.get(relay);

        Map<RelaySite, Integer> expectedFloors = new LinkedHashMap<>();
        for (RelaySite site : RelaySite.values()) {
            expectedFloors.put(site, expectedPlatformY(relay, site.x(), site.z()));
        }
        int centralFloor = expectedFloors.get(RelaySite.CENTRAL);
        BlockPos partialFloor = new BlockPos(
                RelaySite.CENTRAL.x() + PLATFORM_RADIUS,
                centralFloor,
                RelaySite.CENTRAL.z() + PLATFORM_RADIUS);
        BlockPos incompleteChest = chestPosition(RelaySite.CENTRAL, centralFloor);
        BlockPos outsideConstruction = new BlockPos(
                RelaySite.CENTRAL.x() + CONSTRUCTION_RADIUS + 1,
                centralFloor,
                RelaySite.CENTRAL.z());
        BlockState originalOutsideState = relay.getBlockState(outsideConstruction);
        if (!initialData.isInitialized(RelaySite.CENTRAL)) {
            relay.setBlock(
                    partialFloor,
                    EchoContent.RELAY_STONE.get().defaultBlockState(),
                    Block.UPDATE_ALL);
            relay.setBlock(incompleteChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        }
        relay.setBlock(outsideConstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        FarRelayInitializer.ensureAll(relay);

        FarRelaySavedData data = FarRelaySavedData.get(relay);
        helper.assertValueEqual(
                data.initializedSites().size(), RelaySite.values().length, "initialized site count");
        for (RelaySite site : RelaySite.values()) {
            helper.assertTrue(data.isInitialized(site), "unmarked relay site: " + site);
            assertCompleteSite(helper, relay, site, expectedFloors.get(site));
        }
        helper.assertValueEqual(
                relay.getBlockState(outsideConstruction),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "block outside fixed construction box");

        Map<BlockPos, String> beforeSecondInitialization = snapshot(relay, expectedFloors);
        FarRelayInitializer.ensureAll(relay);
        helper.assertValueEqual(
                snapshot(relay, expectedFloors),
                beforeSecondInitialization,
                "second initialization snapshot");

        relay.setBlock(outsideConstruction, originalOutsideState, Block.UPDATE_ALL);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void markedSitesRepairReplaceableRequiredPiecesAtOriginalHeight(
            GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        FarRelayInitializer.ensureAll(relay);
        Map<RelaySite, Integer> floors = currentPlatformFloors(relay);

        for (RelaySite site : RelaySite.values()) {
            int floorY = floors.get(site);
            relay.setBlock(
                    new BlockPos(site.x(), floorY, site.z()),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
            relay.setBlock(
                    chestPosition(site, floorY),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        int centralFloor = floors.get(RelaySite.CENTRAL);
        relay.setBlock(
                new BlockPos(RelaySite.CENTRAL.x() + 3, centralFloor + 1, RelaySite.CENTRAL.z()),
                Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        relay.setBlock(
                new BlockPos(RelaySite.CENTRAL.x() - 3, centralFloor + 1, RelaySite.CENTRAL.z()),
                Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);

        FarRelayInitializer.ensureAll(relay);

        for (RelaySite site : RelaySite.values()) {
            assertCompleteSite(helper, relay, site, floors.get(site));
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void fullyMissingLegacyMarkedSiteRecoversAtFallbackHeight(
            GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        FarRelayInitializer.ensureAll(relay);
        FarRelaySavedData originalData = FarRelaySavedData.get(relay);
        CompoundTag legacyTag = originalData.save(new CompoundTag(), relay.registryAccess());
        legacyTag.getCompound("platform_heights").remove(RelaySite.NORTH.name());
        FarRelaySavedData legacyData = FarRelaySavedData.load(legacyTag, relay.registryAccess());
        relay.getDataStorage().set("afterlight_far_relay", legacyData);
        clearSiteSearchVolume(relay, RelaySite.NORTH);

        RuntimeException failure = null;
        try {
            FarRelayInitializer.ensureAll(relay);
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            if (failure != null) {
                relay.getDataStorage().set("afterlight_far_relay", originalData);
                FarRelayInitializer.ensureAll(relay);
            }
        }

        helper.assertTrue(
                failure == null,
                "fully missing legacy marked site did not recover: " + failure);
        FarRelaySavedData recoveredData = FarRelaySavedData.get(relay);
        helper.assertValueEqual(
                recoveredData.platformY(RelaySite.NORTH).orElseThrow(),
                72,
                "recovered legacy platform height");
        assertCompleteSite(helper, relay, RelaySite.NORTH, 72);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void markedSitePreservesPlayerObstructionAndFailsSafely(
            GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        FarRelayInitializer.ensureAll(relay);
        int centralFloor = currentPlatformFloors(relay).get(RelaySite.CENTRAL);
        BlockPos obstruction = new BlockPos(
                RelaySite.CENTRAL.x(), centralFloor, RelaySite.CENTRAL.z());
        relay.setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        boolean rejected = false;
        BlockState preserved;
        try {
            FarRelayInitializer.ensureAll(relay);
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            preserved = relay.getBlockState(obstruction);
            relay.setBlock(
                    obstruction,
                    EchoContent.RELAY_STONE.get().defaultBlockState(),
                    Block.UPDATE_ALL);
            FarRelayInitializer.ensureAll(relay);
        }

        helper.assertTrue(rejected, "marked site obstruction did not fail safely");
        helper.assertValueEqual(
                preserved,
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "marked site player obstruction");
        assertCompleteSite(helper, relay, RelaySite.CENTRAL, centralFloor);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void markedSitePreservesExistingChestContentsAndMetadata(
            GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        FarRelayInitializer.ensureAll(relay);
        int centralFloor = currentPlatformFloors(relay).get(RelaySite.CENTRAL);
        BlockPos chestPosition = chestPosition(RelaySite.CENTRAL, centralFloor);
        relay.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        relay.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ChestBlockEntity playerChest = (ChestBlockEntity) relay.getBlockEntity(chestPosition);
        playerChest.setItem(0, new ItemStack(Items.DIAMOND));
        playerChest.setChanged();
        CompoundTag before = playerChest.saveWithFullMetadata(relay.registryAccess());

        RuntimeException failure = null;
        try {
            FarRelayInitializer.ensureAll(relay);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        ChestBlockEntity preservedChest = (ChestBlockEntity) relay.getBlockEntity(chestPosition);
        CompoundTag after = preservedChest.saveWithFullMetadata(relay.registryAccess());
        relay.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        FarRelayInitializer.ensureAll(relay);

        helper.assertTrue(
                failure == null,
                "valid existing marked chest was rejected: " + failure);
        helper.assertValueEqual(after, before, "existing marked chest metadata");
        assertCompleteSite(helper, relay, RelaySite.CENTRAL, centralFloor);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_revalidation",
            timeoutTicks = 1200)
    public static void consumedMarkedLootChestRemainsValidAndUnchanged(
            GameTestHelper helper) {
        ServerLevel relay = helper.getLevel();
        restorePreviousTestObstruction(relay);
        FarRelayInitializer.ensureAll(relay);
        int floorY = FarRelaySavedData.get(relay)
                .platformY(RelaySite.SOUTH)
                .orElseThrow();
        BlockPos chestPosition = chestPosition(RelaySite.SOUTH, floorY);
        ChestBlockEntity chest = (ChestBlockEntity) relay.getBlockEntity(chestPosition);
        helper.assertValueEqual(
                chest.getLootTable(),
                FarRelayKeys.LOOT_TABLE,
                "generated chest pending loot table");
        long generatedSeed = chest.getLootTableSeed();
        chest.getItem(0);
        chest.clearContent();
        chest.setChanged();
        helper.assertTrue(chest.getLootTable() == null, "consumed chest retained pending loot");
        CompoundTag before = chest.saveWithFullMetadata(relay.registryAccess());

        RuntimeException failure = null;
        try {
            FarRelayInitializer.ensureAll(relay);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        ChestBlockEntity afterChest =
                (ChestBlockEntity) relay.getBlockEntity(chestPosition);
        CompoundTag after = afterChest.saveWithFullMetadata(relay.registryAccess());
        boolean remainedEmpty = afterChest.isEmpty();
        afterChest.setLootTable(FarRelayKeys.LOOT_TABLE);
        afterChest.setLootTableSeed(generatedSeed);
        afterChest.setChanged();
        FarRelayInitializer.ensureAll(relay);

        helper.assertTrue(
                failure == null,
                "consumed marked loot chest was rejected: " + failure);
        helper.assertValueEqual(after, before, "consumed chest metadata");
        helper.assertTrue(remainedEmpty, "consumed chest loot was duplicated");
        helper.succeed();
    }

    private static void assertCompleteSite(
            GameTestHelper helper, ServerLevel level, RelaySite site, int floorY) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                BlockPos floor = new BlockPos(site.x() + deltaX, floorY, site.z() + deltaZ);
                helper.assertValueEqual(
                        level.getBlockState(floor),
                        EchoContent.RELAY_STONE.get().defaultBlockState(),
                        site + " platform " + floor);
            }
        }
        BlockPos centerAbove = new BlockPos(site.x(), floorY + 1, site.z());
        helper.assertTrue(level.getBlockState(centerAbove).isAir(), site + " arrival is blocked");
        helper.assertTrue(
                level.getBlockState(centerAbove.above()).isAir(),
                site + " arrival headroom is blocked");

        BlockPos chestPosition = chestPosition(site, floorY);
        helper.assertValueEqual(
                level.getBlockState(chestPosition).getBlock(), Blocks.CHEST, site + " loot chest");
        BlockEntity blockEntity = level.getBlockEntity(chestPosition);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity, site + " chest block entity is missing");
        ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
        helper.assertValueEqual(chest.getLootTable(), FarRelayKeys.LOOT_TABLE, site + " loot table");

        if (site == RelaySite.CENTRAL) {
            helper.assertValueEqual(
                    level.getBlockState(new BlockPos(site.x() + 3, floorY + 1, site.z())).getBlock(),
                    EchoContent.RETURN_TERMINAL.get(),
                    "central return terminal");
            helper.assertValueEqual(
                    level.getBlockState(new BlockPos(site.x() - 3, floorY + 1, site.z())).getBlock(),
                    EchoContent.FUTURE_CONSOLE.get(),
                    "central future console");
        }
    }

    private static int expectedPlatformY(ServerLevel level, int x, int z) {
        for (int distance = 0; distance <= 32; distance++) {
            int above = 64 + distance;
            if (isSafeSurface(level, x, above, z)) {
                return above;
            }
            int below = 64 - distance;
            if (distance > 0 && isSafeSurface(level, x, below, z)) {
                return below;
            }
        }
        return 72;
    }

    private static Map<RelaySite, Integer> currentPlatformFloors(ServerLevel level) {
        Map<RelaySite, Integer> floors = new LinkedHashMap<>();
        for (RelaySite site : RelaySite.values()) {
            floors.put(site, expectedPlatformY(level, site.x(), site.z()));
        }
        return Map.copyOf(floors);
    }

    private static void restorePreviousTestObstruction(ServerLevel level) {
        for (int distance = 0; distance <= 32; distance++) {
            restoreDiamondCenter(level, 64 + distance);
            if (distance > 0) {
                restoreDiamondCenter(level, 64 - distance);
            }
        }
        restoreDiamondCenter(level, 72);
    }

    private static void clearSiteSearchVolume(ServerLevel level, RelaySite site) {
        for (int deltaX = -PLATFORM_RADIUS; deltaX <= PLATFORM_RADIUS; deltaX++) {
            for (int deltaZ = -PLATFORM_RADIUS; deltaZ <= PLATFORM_RADIUS; deltaZ++) {
                for (int y = 32; y <= 98; y++) {
                    level.setBlock(
                            new BlockPos(site.x() + deltaX, y, site.z() + deltaZ),
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void restoreDiamondCenter(ServerLevel level, int floorY) {
        BlockPos center = new BlockPos(RelaySite.CENTRAL.x(), floorY, RelaySite.CENTRAL.z());
        if (level.getBlockState(center).is(Blocks.DIAMOND_BLOCK)) {
            level.setBlock(
                    center,
                    EchoContent.RELAY_STONE.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    private static boolean isSafeSurface(ServerLevel level, int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y + 2 >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos floor = new BlockPos(x, y, z);
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(floor.above()).canBeReplaced()
                && level.getBlockState(floor.above(2)).canBeReplaced();
    }

    private static BlockPos chestPosition(RelaySite site, int floorY) {
        return new BlockPos(site.x(), floorY + 1, site.z() + 3);
    }

    private static Map<BlockPos, String> snapshot(
            ServerLevel level, Map<RelaySite, Integer> floors) {
        Map<BlockPos, String> snapshot = new LinkedHashMap<>();
        for (RelaySite site : RelaySite.values()) {
            int floorY = floors.get(site);
            for (int deltaX = -CONSTRUCTION_RADIUS; deltaX <= CONSTRUCTION_RADIUS; deltaX++) {
                for (int deltaZ = -CONSTRUCTION_RADIUS; deltaZ <= CONSTRUCTION_RADIUS; deltaZ++) {
                    for (int deltaY = -1; deltaY <= 3; deltaY++) {
                        BlockPos position = new BlockPos(
                                site.x() + deltaX, floorY + deltaY, site.z() + deltaZ);
                        BlockState state = level.getBlockState(position);
                        String value = BuiltInRegistries.BLOCK.getKey(state.getBlock()) + state.toString();
                        BlockEntity blockEntity = level.getBlockEntity(position);
                        if (blockEntity instanceof ChestBlockEntity chest) {
                            value += ":" + chest.getLootTable() + ":" + chest.getLootTableSeed();
                        }
                        snapshot.put(position, value);
                    }
                }
            }
        }
        return Map.copyOf(snapshot);
    }
}
