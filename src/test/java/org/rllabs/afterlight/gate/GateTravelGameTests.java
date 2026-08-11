package org.rllabs.afterlight.gate;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
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
        FarRelayInitializer.ensureAll(relay);
        int initializedSites = org.rllabs.afterlight.relay.FarRelaySavedData.get(relay)
                .initializedSites()
                .size();
        BlockPos arrival = FarRelayInitializer.centralArrival(relay).orElseThrow(
                () -> new IllegalStateException("Dedicated Far Relay central arrival is unavailable"));
        if (initializedSites != org.rllabs.afterlight.relay.RelaySite.values().length) {
            throw new IllegalStateException(
                    "Dedicated Far Relay initialized " + initializedSites + " sites");
        }
        System.out.println(
                "AFTERLIGHT DEDICATED CUSTOM LEVEL ACCEPTANCE: OK level="
                        + relay.dimension().location()
                        + " chunk="
                        + chunk.getPos()
                        + " sites="
                        + initializedSites
                        + " arrival="
                        + arrival);
        server.halt(false);
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
        private FailingTransferPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "travel-failing-player"),
                    ClientInformation.createDefault());
        }

        @Override
        public Entity changeDimension(DimensionTransition transition) {
            return null;
        }
    }
}
