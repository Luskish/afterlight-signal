package org.rllabs.afterlight.gate;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateActivationService.ActivationDecision;
import org.rllabs.afterlight.relay.FarRelayInitializer;
import org.rllabs.afterlight.relay.FarRelayKeys;
import org.rllabs.afterlight.relay.FarRelaySavedData;
import org.rllabs.afterlight.relay.FarRelayStructurePlan;
import org.rllabs.afterlight.relay.RelaySite;
import org.rllabs.afterlight.relay.SignalTerminalBlock;

@GameTestHolder(Afterlight.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Afterlight.MOD_ID)
@SuppressWarnings("removal")
public final class GateTravelGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 4);
    private static final BlockPos TERMINAL = new BlockPos(7, 2, 4);

    private GateTravelGameTests() {}

    @SubscribeEvent
    public static void verifyDedicatedFarRelay(ServerStartedEvent event) {
        if (!Boolean.getBoolean("afterlight.dedicated.acceptance")) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!(server instanceof DedicatedServer)) {
            throw new IllegalStateException("Dedicated acceptance did not start a normal dedicated server");
        }
        ServerLevel relay = server.getLevel(FarRelayKeys.LEVEL);
        if (relay == null) {
            throw new IllegalStateException("Dedicated server did not create afterlight:far_relay");
        }
        var chunk = relay.getChunk(0, 0);
        FarRelaySavedData data = FarRelaySavedData.get(relay);
        int existingSites = data.initializedSites().size();
        if (existingSites != 0) {
            throw new IllegalStateException(
                    "Dedicated acceptance reused initialized world state: " + existingSites);
        }
        System.out.println(
                "AFTERLIGHT DEDICATED FRESH WORLD: OK existing_sites=" + existingSites);
        FarRelayInitializer.ensureAll(relay);
        int initializedSites = data.initializedSites().size();
        BlockPos arrival = FarRelayInitializer.centralArrival(relay).orElseThrow(
                () -> new IllegalStateException("Dedicated Far Relay central arrival is unavailable"));
        if (initializedSites != RelaySite.values().length) {
            throw new IllegalStateException(
                    "Dedicated Far Relay initialized " + initializedSites + " sites");
        }
        int physicalSites = 0;
        for (RelaySite site : RelaySite.values()) {
            int platformY = data.platformY(site).orElseThrow(() -> new IllegalStateException(
                    "Dedicated Far Relay platform height is unavailable: " + site));
            verifyPhysicalSite(relay, site, platformY);
            physicalSites++;
        }
        System.out.println(
                "AFTERLIGHT DEDICATED CUSTOM LEVEL ACCEPTANCE: OK level="
                        + relay.dimension().location()
                        + " chunk="
                        + chunk.getPos()
                        + " sites="
                        + initializedSites
                        + " physical_sites="
                        + physicalSites
                        + " arrival="
                        + arrival);
        writeDedicatedAcceptanceMarker();
        server.halt(false);
    }

    private static void writeDedicatedAcceptanceMarker() {
        String marker = System.getProperty("afterlight.dedicated.acceptance.marker");
        if (marker == null || marker.isBlank()) {
            throw new IllegalStateException("Dedicated acceptance marker path is unavailable");
        }
        try {
            Files.writeString(Path.of(marker), "ok\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Dedicated acceptance marker could not be written", exception);
        }
    }

    private static void verifyPhysicalSite(
            ServerLevel relay, RelaySite site, int platformY) {
        for (int deltaX = -5; deltaX <= 5; deltaX++) {
            for (int deltaZ = -5; deltaZ <= 5; deltaZ++) {
                BlockPos floor = new BlockPos(
                        site.x() + deltaX, platformY, site.z() + deltaZ);
                if (!relay.getBlockState(floor).is(EchoContent.RELAY_STONE.get())) {
                    throw new IllegalStateException(
                            "Dedicated Far Relay platform is incomplete: " + site + " at " + floor);
                }
            }
        }
        BlockPos center = new BlockPos(site.x(), platformY + 1, site.z());
        if (!relay.getBlockState(center).isAir()
                || !relay.getBlockState(center.above()).isAir()) {
            throw new IllegalStateException(
                    "Dedicated Far Relay safe arrival is blocked: " + site);
        }
        BlockPos chestPosition = new BlockPos(site.x(), platformY + 1, site.z() + 3);
        BlockEntity blockEntity = relay.getBlockEntity(chestPosition);
        if (!(blockEntity instanceof ChestBlockEntity chest)
                || chest.getLootTable() != FarRelayKeys.LOOT_TABLE) {
            throw new IllegalStateException(
                    "Dedicated Far Relay loot marker is incomplete: " + site);
        }
        if (site == RelaySite.CENTRAL
                && (!relay.getBlockState(new BlockPos(site.x() + 3, platformY + 1, site.z()))
                                .is(EchoContent.RETURN_TERMINAL.get())
                        || !relay.getBlockState(new BlockPos(
                                        site.x() - 3, platformY + 1, site.z()))
                                .is(EchoContent.FUTURE_CONSOLE.get()))) {
            throw new IllegalStateException("Dedicated Far Relay central markers are incomplete");
        }
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void outboundStoresOriginArrivesCentrallyAndGrantsAfterSuccess(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(helper.absolutePos(CONTROLLER.above()).getBottomCenter(), 31.5F, -14.25F);
        GateReturnTarget expected = new GateReturnTarget(
                helper.getLevel().dimension(),
                helper.absolutePos(CONTROLLER).above(),
                31.5F,
                -14.25F);
        helper.assertFalse(
                advancementDone(player, FarRelayKeys.FAR_RELAY_ARRIVAL),
                "arrival advancement changed before outbound transfer");

        GateTravelService.TravelResult result = GateTravelService.INSTANCE.travelToFarRelay(
                player, helper.absolutePos(CONTROLLER), helper.getLevel());

        helper.assertValueEqual(
                GateTravelService.TravelResult.SUCCESS, result, "outbound result");
        helper.assertValueEqual(
                expected,
                player.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "stored return target");
        BlockPos arrival = FarRelayInitializer.centralArrival(helper.getLevel()).orElseThrow();
        helper.assertValueEqual(arrival, player.blockPosition(), "central arrival position");
        helper.assertValueEqual(Vec3.ZERO, player.getDeltaMovement(), "arrival velocity");
        helper.assertTrue(
                advancementDone(player, FarRelayKeys.FAR_RELAY_ARRIVAL),
                "arrival advancement missing after outbound transfer");
        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_far_relay_migration",
            timeoutTicks = 1200)
    public static void preV2DecorativeConflictPreservesTravelAndCompletesOnce(
            GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel relay = source;
        FarRelayInitializer.ensureAll(relay);
        FarRelaySavedData data = FarRelaySavedData.get(relay);
        int floorY = data.platformY(RelaySite.CENTRAL).orElseThrow();
        BlockPos conflict = new BlockPos(RelaySite.CENTRAL.x() + 8, floorY, RelaySite.CENTRAL.z());
        BlockPos safeUpgrade = new BlockPos(
                RelaySite.CENTRAL.x(), floorY + 13, RelaySite.CENTRAL.z() - 10);
        BlockPos postMigrationEdit = new BlockPos(
                RelaySite.CENTRAL.x() - 9, floorY + 5, RelaySite.CENTRAL.z() - 6);
        BlockPos returnTerminal = new BlockPos(
                RelaySite.CENTRAL.x() + 3, floorY + 1, RelaySite.CENTRAL.z());
        BlockPos chestPosition = new BlockPos(
                RelaySite.CENTRAL.x(), floorY + 1, RelaySite.CENTRAL.z() + 3);
        ServerPlayer player = null;

        try {
            reduceCentralToPreV2(relay, floorY);
            data.markPresented(RelaySite.CENTRAL, 0);
            relay.setBlock(conflict, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            BlockState terminalBefore = EchoContent.RETURN_TERMINAL
                    .get()
                    .defaultBlockState()
                    .setValue(SignalTerminalBlock.FACING, Direction.SOUTH)
                    .setValue(SignalTerminalBlock.ACTIVE, false);
            relay.setBlock(returnTerminal, terminalBefore, Block.UPDATE_ALL);
            ChestBlockEntity chestBefore = (ChestBlockEntity) relay.getBlockEntity(chestPosition);
            chestBefore.getItem(0);
            chestBefore.clearContent();
            chestBefore.setChanged();
            CompoundTag chestBeforeTag = chestBefore.saveWithFullMetadata(relay.registryAccess());

            FarRelayInitializer.ensureAll(relay);

            helper.assertValueEqual(
                    Blocks.DIAMOND_BLOCK.defaultBlockState(),
                    relay.getBlockState(conflict),
                    "pre-v2 x=8 decorative conflict");
            helper.assertValueEqual(
                    FarRelayStructurePlan.PRESENTATION_VERSION,
                    data.presentationVersion(RelaySite.CENTRAL),
                    "conflict-tolerant presentation version");
            helper.assertValueEqual(
                    EchoContent.GATE_FRAME.get(),
                    relay.getBlockState(safeUpgrade).getBlock(),
                    "safe cathedral upgrade");
            helper.assertValueEqual(
                    terminalBefore,
                    relay.getBlockState(returnTerminal),
                    "pre-v2 terminal state");
            helper.assertValueEqual(
                    chestBeforeTag,
                    ((ChestBlockEntity) relay.getBlockEntity(chestPosition))
                            .saveWithFullMetadata(relay.registryAccess()),
                    "pre-v2 consumed chest metadata");
            helper.assertTrue(
                    FarRelayInitializer.centralArrival(relay).isPresent(),
                    "functional arrival after decorative conflict");

            relay.setBlock(postMigrationEdit, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            Map<BlockPos, BlockState> beforeReloadEnsure = snapshotCentralPlan(relay, floorY);
            CompoundTag persisted = data.save(new CompoundTag(), relay.registryAccess());
            FarRelaySavedData reloaded = FarRelaySavedData.load(persisted, relay.registryAccess());
            relay.getDataStorage().set("afterlight_far_relay", reloaded);

            FarRelayInitializer.ensureAll(relay);

            helper.assertValueEqual(
                    beforeReloadEnsure,
                    snapshotCentralPlan(relay, floorY),
                    "second ensure structure snapshot");
            helper.assertTrue(
                    relay.getBlockState(postMigrationEdit).isAir(),
                    "completed presentation migration retried decoration");
            helper.assertFalse(reloaded.isDirty(), "second ensure dirtied saved migration state");
            helper.assertValueEqual(
                    chestBeforeTag,
                    ((ChestBlockEntity) relay.getBlockEntity(chestPosition))
                            .saveWithFullMetadata(relay.registryAccess()),
                    "reloaded consumed chest metadata");

            BlockPos sourceController = helper.absolutePos(CONTROLLER);
            BlockPos expectedReturn = sourceController.above();
            prepareSafePosition(source, expectedReturn);
            player = helper.makeMockServerPlayerInLevel();
            player.moveTo(expectedReturn.getBottomCenter(), 37.0F, -11.0F);

            GateTravelService.TravelResult outbound =
                    GateTravelService.INSTANCE.travelToFarRelay(
                            player, sourceController, relay);

            helper.assertValueEqual(
                    GateTravelService.TravelResult.SUCCESS, outbound, "migration outbound result");
            helper.assertValueEqual(relay, player.serverLevel(), "migration outbound level");
            helper.assertValueEqual(
                    FarRelayInitializer.centralArrival(relay).orElseThrow(),
                    player.blockPosition(),
                    "migration outbound arrival");
            helper.assertTrue(
                    GateTravelService.INSTANCE.returnPlayer(player),
                    "migration return travel failed");
            helper.assertValueEqual(source, player.serverLevel(), "migration return level");
            helper.assertValueEqual(expectedReturn, player.blockPosition(), "migration return position");
            helper.assertTrue(
                    player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                    "migration return retained route");
        } finally {
            if (player != null) {
                removePlayer(helper, player);
            }
            restoreCentralAfterMigrationTest(relay, floorY, conflict, returnTerminal, chestPosition);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void repeatedOutboundCollisionIsRateLimited(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos controller = helper.absolutePos(CONTROLLER);

        GateTravelService.TravelResult first = GateTravelService.INSTANCE.travelToFarRelay(
                player, controller, helper.getLevel());
        GateReturnTarget firstTarget = player.getData(EchoContent.GATE_RETURN_TARGET);
        GateTravelService.TravelResult duplicate = GateTravelService.INSTANCE.travelToFarRelay(
                player, controller.offset(20, 0, 0), helper.getLevel());

        helper.assertValueEqual(GateTravelService.TravelResult.SUCCESS, first, "first collision");
        helper.assertValueEqual(
                GateTravelService.TravelResult.RATE_LIMITED,
                duplicate,
                "duplicate collision");
        helper.assertValueEqual(
                firstTarget,
                player.getData(EchoContent.GATE_RETURN_TARGET),
                "duplicate collision target");
        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void activeReturnTargetSurvivesOutboundAfterLimiterExpires(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos controller = helper.absolutePos(CONTROLLER);
        GateTravelService.TravelResult first = GateTravelService.INSTANCE.travelToFarRelay(
                player, controller, helper.getLevel());
        GateReturnTarget original = player.getData(EchoContent.GATE_RETURN_TARGET);

        helper.assertValueEqual(first, GateTravelService.TravelResult.SUCCESS, "first outbound");
        helper.runAfterDelay(21L, () -> {
            GateTravelService.TravelResult nested = GateTravelService.INSTANCE.travelToFarRelay(
                    player, controller.offset(20, 0, 0), helper.getLevel());

            helper.assertFalse(
                    nested == GateTravelService.TravelResult.SUCCESS,
                    "nested outbound succeeded after rate limit expired");
            helper.assertValueEqual(
                    player.getData(EchoContent.GATE_RETURN_TARGET),
                    original,
                    "active return target after limiter expiry");
            removePlayer(helper, player);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void failedNestedOutboundPreservesOriginalRoute(GameTestHelper helper) {
        FailingTransferPlayer player = new FailingTransferPlayer(helper.getLevel());
        GateReturnTarget original = new GateReturnTarget(
                Level.OVERWORLD, new BlockPos(12, 80, -44), 62.0F, -7.0F);
        player.setData(EchoContent.GATE_RETURN_TARGET, original);

        GateTravelService.TravelResult nested = GateTravelService.INSTANCE.travelToFarRelay(
                player, helper.absolutePos(CONTROLLER), helper.getLevel());

        helper.assertFalse(
                nested == GateTravelService.TravelResult.SUCCESS,
                "nested outbound reported success");
        helper.assertValueEqual(player.changeDimensionAttempts(), 0, "nested transfer attempts");
        helper.assertValueEqual(
                player.getData(EchoContent.GATE_RETURN_TARGET),
                original,
                "original route after failed nested outbound");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void failedFirstOutboundLeavesNoStaleRoute(GameTestHelper helper) {
        FailingTransferPlayer player = new FailingTransferPlayer(helper.getLevel());

        GateTravelService.TravelResult result = GateTravelService.INSTANCE.travelToFarRelay(
                player, helper.absolutePos(CONTROLLER), helper.getLevel());

        helper.assertValueEqual(
                result,
                GateTravelService.TravelResult.TRANSFER_FAILED,
                "failed first outbound result");
        helper.assertTrue(
                player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                "failed first outbound retained a stale route");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void exceptionalFirstOutboundLeavesNoStaleRoute(GameTestHelper helper) {
        ThrowingTransferPlayer player = new ThrowingTransferPlayer(helper.getLevel());

        GateTravelService.TravelResult result = GateTravelService.INSTANCE.travelToFarRelay(
                player, helper.absolutePos(CONTROLLER), helper.getLevel());

        helper.assertValueEqual(
                result,
                GateTravelService.TravelResult.TRANSFER_FAILED,
                "exceptional first outbound result");
        helper.assertTrue(
                player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                "exceptional first outbound retained a stale route");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void returnTerminalUsesExactSafeSourceAndClearsTarget(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos exact = helper.absolutePos(new BlockPos(4, 3, 8));
        prepareSafePosition(helper.getLevel(), exact);
        GateReturnTarget target = new GateReturnTarget(
                helper.getLevel().dimension(), exact, 83.0F, 11.5F);
        player.setData(EchoContent.GATE_RETURN_TARGET, target);
        helper.setBlock(TERMINAL, EchoContent.RETURN_TERMINAL.get());

        helper.useBlock(TERMINAL, player);

        helper.assertValueEqual(exact, player.blockPosition(), "exact return position");
        helper.assertValueEqual(83.0F, player.getYRot(), "return yaw");
        helper.assertValueEqual(11.5F, player.getXRot(), "return pitch");
        helper.assertTrue(
                player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                "successful return retained target");
        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_gate_travel_search",
            timeoutTicks = 80)
    public static void obstructedSourceUsesBoundedSafeSearch(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos exact = helper.absolutePos(new BlockPos(4, 3, 8));
        BlockPos fallback = exact.offset(5, 6, 0);
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> original =
                fillSearchVolume(helper.getLevel(), exact, Blocks.STONE);
        prepareSafePosition(helper.getLevel(), fallback);
        player.setData(
                EchoContent.GATE_RETURN_TARGET,
                new GateReturnTarget(helper.getLevel().dimension(), exact, 0.0F, 0.0F));

        try {
            helper.assertTrue(GateTravelService.INSTANCE.returnPlayer(player), "bounded return failed");
            helper.assertValueEqual(fallback, player.blockPosition(), "searched return position");
            helper.assertTrue(
                    player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                    "searched return retained target");
        } finally {
            restore(helper.getLevel(), original);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            batch = "afterlight_gate_travel_shared_spawn",
            timeoutTicks = 80)
    public static void missingSourceLevelUsesSafeOverworldSharedSpawn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceKey<Level> missing = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(Afterlight.MOD_ID, "missing_source"));
        player.setData(
                EchoContent.GATE_RETURN_TARGET,
                new GateReturnTarget(missing, new BlockPos(1, 2, 3), 12.0F, -4.0F));
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        BlockPos sharedSpawn = overworld.getSharedSpawnPos();
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> original = Map.of(
                sharedSpawn.below(), overworld.getBlockState(sharedSpawn.below()),
                sharedSpawn, overworld.getBlockState(sharedSpawn),
                sharedSpawn.above(), overworld.getBlockState(sharedSpawn.above()));
        prepareSafePosition(overworld, sharedSpawn);
        try {
            helper.assertTrue(
                    GateTravelService.INSTANCE.returnPlayer(player), "shared spawn return failed");
            helper.assertValueEqual(overworld, player.serverLevel(), "fallback level");
            helper.assertValueEqual(sharedSpawn, player.blockPosition(), "shared spawn position");
            helper.assertTrue(
                    player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                    "shared spawn return retained target");
        } finally {
            restore(overworld, original);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void failedReturnKeepsTargetUntilLaterSuccess(GameTestHelper helper) {
        FailingTransferPlayer player = new FailingTransferPlayer(helper.getLevel());
        BlockPos exact = helper.absolutePos(new BlockPos(4, 3, 8));
        prepareSafePosition(helper.getLevel(), exact);
        GateReturnTarget target = new GateReturnTarget(
                helper.getLevel().dimension(), exact, 45.0F, 9.0F);
        player.setData(EchoContent.GATE_RETURN_TARGET, target);

        helper.assertFalse(GateTravelService.INSTANCE.returnPlayer(player), "failed transfer reported success");
        helper.assertValueEqual(
                target,
                player.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "failed transfer target");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void savedPlayerReloadPreservesReturnTarget(GameTestHelper helper) {
        ServerPlayer original = newPlayer(helper.getLevel(), "travel-persistence-original");
        GateReturnTarget target = new GateReturnTarget(
                Level.OVERWORLD, new BlockPos(-88, 92, 144), 179.0F, -37.0F);
        original.setData(EchoContent.GATE_RETURN_TARGET, target);

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        ServerPlayer reloaded = newPlayer(helper.getLevel(), "travel-persistence-reloaded");
        reloaded.load(saved);

        helper.assertValueEqual(
                target,
                reloaded.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "reloaded target");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void twoPlayersKeepIndependentTargets(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        GateReturnTarget firstTarget = new GateReturnTarget(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 3, 7)), 1.0F, 2.0F);
        GateReturnTarget secondTarget = new GateReturnTarget(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(8, 3, 7)), 3.0F, 4.0F);

        first.setData(EchoContent.GATE_RETURN_TARGET, firstTarget);
        second.setData(EchoContent.GATE_RETURN_TARGET, secondTarget);

        helper.assertValueEqual(
                firstTarget,
                first.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "first player target");
        helper.assertValueEqual(
                secondTarget,
                second.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "second player target");
        first.removeData(EchoContent.GATE_RETURN_TARGET);
        helper.assertValueEqual(
                secondTarget,
                second.getExistingData(EchoContent.GATE_RETURN_TARGET).orElse(null),
                "second target after first clear");
        removePlayer(helper, first);
        removePlayer(helper, second);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void gateOpenedAdvancementFollowsSuccessfulOpen(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ActivationDecision decision = new GateActivationService().activate(
                new GateActivationService.ActivationRequest(
                        new GatePatternMatcher.MatchResult(java.util.List.of()),
                        1,
                        true,
                        GateState.IDLE,
                        helper.getLevel().getGameTime()),
                null,
                (ignoredPlayer, ignoredTask) -> true);
        helper.assertFalse(
                advancementDone(player, FarRelayKeys.GATE_OPENED),
                "gate advancement changed before open");

        helper.assertTrue(controller.applyActivation(decision, player), "successful open was rejected");

        helper.assertTrue(
                advancementDone(player, FarRelayKeys.GATE_OPENED),
                "gate advancement missing after open");
        controller.close();
        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void failedGateOpenDoesNotGrantAdvancement(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ActivationDecision decision = new GateActivationService().activate(
                new GateActivationService.ActivationRequest(
                        new GatePatternMatcher.MatchResult(java.util.List.of()),
                        1,
                        true,
                        GateState.IDLE,
                        helper.getLevel().getGameTime()),
                null,
                (ignoredPlayer, ignoredTask) -> true);
        BlockPos blockedInterior = GatePattern.interior(net.minecraft.core.Direction.NORTH)
                .iterator()
                .next()
                .toWorld(CONTROLLER, net.minecraft.core.Direction.NORTH);
        helper.setBlock(blockedInterior, Blocks.STONE);

        helper.assertFalse(controller.applyActivation(decision, player), "blocked open reported success");

        helper.assertFalse(
                advancementDone(player, FarRelayKeys.GATE_OPENED),
                "failed open granted gate advancement");
        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 1200)
    public static void failedOutboundDoesNotGrantArrivalAdvancement(GameTestHelper helper) {
        FailingTransferPlayer player = new FailingTransferPlayer(helper.getLevel());

        GateTravelService.TravelResult result = GateTravelService.INSTANCE.travelToFarRelay(
                player, helper.absolutePos(CONTROLLER), helper.getLevel());

        helper.assertValueEqual(
                GateTravelService.TravelResult.TRANSFER_FAILED,
                result,
                "failed outbound result");
        helper.assertFalse(
                advancementDone(player, FarRelayKeys.FAR_RELAY_ARRIVAL),
                "failed outbound granted arrival advancement");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 80)
    public static void gateFieldRejectsNonServerAuthorityAndMissingDestination(GameTestHelper helper) {
        BlockPos field = new BlockPos(4, 3, 4);
        helper.setBlock(field, EchoContent.GATE_FIELD.get());
        GateFieldBlockEntity fieldBlockEntity = helper.getBlockEntity(field);
        fieldBlockEntity.initializeOwnership(helper.absolutePos(CONTROLLER), UUID.randomUUID());
        Player clientAuthority = helper.makeMockPlayer(GameType.CREATIVE);
        ServerPlayer serverPlayer = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(field);

        helper.getBlockState(field).entityInside(helper.getLevel(), absolute, clientAuthority);
        helper.getBlockState(field).entityInside(helper.getLevel(), absolute, serverPlayer);

        helper.assertTrue(
                serverPlayer.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                "missing destination stored an outbound target");
        removePlayer(helper, serverPlayer);
        helper.succeed();
    }

    private static GateControllerBlockEntity buildGate(GameTestHelper helper) {
        for (var entry : GatePattern.expected(net.minecraft.core.Direction.NORTH).entrySet()) {
            BlockPos position = entry.getKey().toWorld(CONTROLLER, net.minecraft.core.Direction.NORTH);
            Block block = switch (entry.getValue()) {
                case CONTROLLER -> EchoContent.GATE_CONTROLLER.get();
                case FRAME -> EchoContent.GATE_FRAME.get();
                case SIGNAL_GLASS -> EchoContent.SIGNAL_GLASS.get();
            };
            helper.setBlock(position, block);
        }
        return helper.getBlockEntity(CONTROLLER);
    }

    private static void reduceCentralToPreV2(ServerLevel level, int floorY) {
        for (FarRelayStructurePlan.Placement placement :
                FarRelayStructurePlan.forSite(RelaySite.CENTRAL).placements()) {
            boolean legacyFloor = placement.y() == 0
                    && Math.abs(placement.x()) <= 5
                    && Math.abs(placement.z()) <= 5;
            boolean legacyChest = placement.x() == 0
                    && placement.y() == 1
                    && placement.z() == 3;
            boolean legacyTerminal = placement.y() == 1
                    && placement.z() == 0
                    && Math.abs(placement.x()) == 3;
            if (!legacyFloor && !legacyChest && !legacyTerminal) {
                level.setBlock(
                        FarRelayStructurePlan.worldPosition(
                                RelaySite.CENTRAL, floorY, placement),
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
            }
        }
    }

    private static Map<BlockPos, BlockState> snapshotCentralPlan(ServerLevel level, int floorY) {
        Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        for (FarRelayStructurePlan.Placement placement :
                FarRelayStructurePlan.forSite(RelaySite.CENTRAL).placements()) {
            BlockPos position = FarRelayStructurePlan.worldPosition(
                    RelaySite.CENTRAL, floorY, placement);
            snapshot.put(position, level.getBlockState(position));
        }
        return Map.copyOf(snapshot);
    }

    private static void restoreCentralAfterMigrationTest(
            ServerLevel level,
            int floorY,
            BlockPos conflict,
            BlockPos returnTerminal,
            BlockPos chestPosition) {
        level.setBlock(conflict, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        FarRelaySavedData data = FarRelaySavedData.get(level);
        data.markPresented(RelaySite.CENTRAL, 0);
        FarRelayInitializer.ensureAll(level);
        FarRelayStructurePlan.Placement terminalPlacement = FarRelayStructurePlan
                .forSite(RelaySite.CENTRAL)
                .placementAt(3, 1, 0)
                .orElseThrow();
        level.setBlock(
                returnTerminal,
                EchoContent.RETURN_TERMINAL
                        .get()
                        .defaultBlockState()
                        .setValue(SignalTerminalBlock.FACING, terminalPlacement.facing())
                        .setValue(SignalTerminalBlock.ACTIVE, terminalPlacement.active()),
                Block.UPDATE_ALL);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        chest.setLootTable(FarRelayKeys.LOOT_TABLE);
        chest.setLootTableSeed(0xA17E0000L + RelaySite.CENTRAL.ordinal());
        chest.setChanged();
    }

    private static boolean advancementDone(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement == null) {
            throw new AssertionError("missing advancement: " + id);
        }
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void prepareSafePosition(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> fillSearchVolume(
            ServerLevel level, BlockPos center, Block block) {
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> original =
                new LinkedHashMap<>();
        for (int deltaX = -GateTravelService.SEARCH_RADIUS;
                deltaX <= GateTravelService.SEARCH_RADIUS;
                deltaX++) {
            for (int deltaZ = -GateTravelService.SEARCH_RADIUS;
                    deltaZ <= GateTravelService.SEARCH_RADIUS;
                    deltaZ++) {
                if (deltaX * deltaX + deltaZ * deltaZ
                        > GateTravelService.SEARCH_RADIUS * GateTravelService.SEARCH_RADIUS) {
                    continue;
                }
                for (int deltaY = -GateTravelService.VERTICAL_RANGE;
                        deltaY <= GateTravelService.VERTICAL_RANGE + 1;
                        deltaY++) {
                    BlockPos position = center.offset(deltaX, deltaY, deltaZ);
                    original.put(position, level.getBlockState(position));
                    level.setBlock(
                            position,
                            block.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }
        return Map.copyOf(original);
    }

    private static void restore(
            ServerLevel level,
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> original) {
        original.forEach((position, state) -> level.setBlock(position, state, Block.UPDATE_ALL));
    }

    private static ServerPlayer newPlayer(ServerLevel level, String name) {
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static final class FailingTransferPlayer extends ServerPlayer {
        private int changeDimensionAttempts;

        private FailingTransferPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "travel-failing-player"),
                    ClientInformation.createDefault());
        }

        @Override
        public Entity changeDimension(DimensionTransition transition) {
            changeDimensionAttempts++;
            return null;
        }

        private int changeDimensionAttempts() {
            return changeDimensionAttempts;
        }
    }

    private static final class ThrowingTransferPlayer extends ServerPlayer {
        private ThrowingTransferPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "travel-throwing-player"),
                    ClientInformation.createDefault());
        }

        @Override
        public Entity changeDimension(DimensionTransition transition) {
            throw new IllegalStateException("test transfer failure");
        }
    }
}
