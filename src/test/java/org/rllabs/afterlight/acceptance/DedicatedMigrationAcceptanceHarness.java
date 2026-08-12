package org.rllabs.afterlight.acceptance;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateTravelService;
import org.rllabs.afterlight.relay.FarRelayInitializer;
import org.rllabs.afterlight.relay.FarRelayKeys;
import org.rllabs.afterlight.relay.FarRelaySavedData;
import org.rllabs.afterlight.relay.FarRelayStructurePlan;
import org.rllabs.afterlight.relay.RelaySite;
import org.rllabs.afterlight.relay.SignalTerminalBlock;

@EventBusSubscriber(modid = Afterlight.MOD_ID)
public final class DedicatedMigrationAcceptanceHarness {
    private static final String ENABLED_PROPERTY =
            "afterlight.dedicated.migration.acceptance";
    private static final String PHASE_PROPERTY =
            "afterlight.dedicated.migration.phase";
    private static final String TOKEN_PROPERTY =
            "afterlight.dedicated.migration.token";
    private static final String MARKER_PROPERTY =
            "afterlight.dedicated.migration.marker";
    private static final String PREPARE_MARKER_PROPERTY =
            "afterlight.dedicated.migration.prepare-marker";
    private static final BlockPos SOURCE_CONTROLLER = new BlockPos(32, 96, 32);
    private static final String PLAYER_NAME = "RelayProof";
    private static final UUID PLAYER_ID = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + PLAYER_NAME).getBytes(StandardCharsets.UTF_8));

    private DedicatedMigrationAcceptanceHarness() {}

    @SubscribeEvent
    public static void verifyMigrationRoute(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            require(server instanceof DedicatedServer,
                    "Dedicated migration acceptance did not start a dedicated server");
            DedicatedMigrationAcceptanceMarker.Phase phase =
                    DedicatedMigrationAcceptanceMarker.Phase.fromId(
                            requiredProperty(PHASE_PROPERTY));
            String token = requiredProperty(TOKEN_PROPERTY);
            DedicatedMigrationAcceptanceMarker.requireToken(token);
            Path marker = Path.of(requiredProperty(MARKER_PROPERTY));
            switch (phase) {
                case PREPARE -> prepare(server, marker, token);
                case VERIFY -> verify(server, marker, token);
            }
        } catch (RuntimeException exception) {
            System.err.println("AFTERLIGHT DEDICATED MIGRATION ACCEPTANCE: FAILED");
            exception.printStackTrace(System.err);
            server.halt(false);
            throw exception;
        }
    }

    private static void prepare(MinecraftServer server, Path marker, String token) {
        ServerLevel relay = requiredRelay(server);
        FarRelaySavedData data = FarRelaySavedData.get(relay);
        require(data.initializedSites().isEmpty(),
                "Dedicated migration prepare reused initialized Far Relay data");

        FarRelayInitializer.ensureAll(relay);
        require(data.initializedSites().size() == RelaySite.values().length,
                "Dedicated migration prepare did not generate every Relay site");
        for (RelaySite site : RelaySite.values()) {
            int platformY = data.platformY(site).orElseThrow(
                    () -> new IllegalStateException(
                            "Dedicated migration prepare has no platform height: " + site));
            reduceToPreV2(relay, site, platformY);
            data.markPresented(site, 0);
        }

        int floorY = data.platformY(RelaySite.CENTRAL).orElseThrow();
        BlockPos conflict = conflictPosition(floorY);
        BlockPos returnPosition = terminalPosition(floorY, 3);
        BlockPos consolePosition = terminalPosition(floorY, -3);
        BlockPos chestPosition = chestPosition(floorY);
        relay.setBlock(conflict, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        relay.setBlock(returnPosition, customReturnState(), Block.UPDATE_ALL);
        relay.setBlock(consolePosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ChestBlockEntity chest = requiredChest(relay, chestPosition);
        chest.getItem(0);
        chest.clearContent();
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        chest.setChanged();
        require(chest.getLootTable() == null, "Prepared Relay chest still has pending loot");

        server.saveEverything(false, true, false);
        require(!data.isDirty(), "Prepared migration state remained dirty after save");
        DedicatedMigrationAcceptanceMarker.write(
                marker,
                DedicatedMigrationAcceptanceMarker.Phase.PREPARE,
                token,
                Map.of(
                        "chest_state", "consumed_diamond_7",
                        "conflict", coordinate(conflict),
                        "dimension", relay.dimension().location().toString(),
                        "floor_y", Integer.toString(floorY),
                        "future_console", "missing",
                        "presentation_version", "0",
                        "return_terminal", "south_inactive",
                        "sites", Integer.toString(data.initializedSites().size())));
        System.out.println(
                "AFTERLIGHT DEDICATED MIGRATION PREPARE: OK level="
                        + relay.dimension().location()
                        + " floor_y="
                        + floorY
                        + " conflict="
                        + conflict
                        + " sites="
                        + data.initializedSites().size());
        server.halt(false);
    }

    private static void verify(MinecraftServer server, Path marker, String token) {
        Path prepareMarker = Path.of(requiredProperty(PREPARE_MARKER_PROPERTY));
        DedicatedMigrationAcceptanceMarker.Marker prepared =
                DedicatedMigrationAcceptanceMarker.readAndVerify(
                        prepareMarker,
                        DedicatedMigrationAcceptanceMarker.Phase.PREPARE,
                        token);
        ServerLevel relay = requiredRelay(server);
        ServerLevel overworld = server.overworld();
        require(
                relay.dimension() == FarRelayKeys.LEVEL,
                "Dedicated migration verify did not load afterlight:far_relay");
        require(
                relay.dimension().location().toString().equals(
                        prepared.metadata().get("dimension")),
                "Prepared marker dimension does not match the restarted Relay");
        FarRelaySavedData data = FarRelaySavedData.get(relay);
        require(data.initializedSites().size() == RelaySite.values().length,
                "Restarted migration world lost Relay site data");
        for (RelaySite site : RelaySite.values()) {
            require(data.presentationVersion(site) == 0,
                    "Restarted migration site was not pre-v2: " + site);
        }

        int floorY = data.platformY(RelaySite.CENTRAL).orElseThrow();
        require(
                Integer.toString(floorY).equals(prepared.metadata().get("floor_y")),
                "Restarted central floor does not match the prepared marker");
        BlockPos conflict = conflictPosition(floorY);
        BlockPos returnPosition = terminalPosition(floorY, 3);
        BlockPos consolePosition = terminalPosition(floorY, -3);
        BlockPos chestPosition = chestPosition(floorY);
        require(relay.getBlockState(conflict).is(Blocks.DIAMOND_BLOCK),
                "Restarted x=8 player conflict was not preserved");
        require(relay.getBlockState(returnPosition).equals(customReturnState()),
                "Restarted custom return terminal state was not preserved");
        require(relay.getBlockState(consolePosition).isAir(),
                "Restarted missing future console was restored before production travel");
        ChestBlockEntity chest = requiredChest(relay, chestPosition);
        requireConsumedChest(chest);
        CompoundTag chestBeforeTravel = chest.saveWithFullMetadata(relay.registryAccess());

        overworld.getChunkAt(SOURCE_CONTROLLER);
        prepareSafeSource(overworld);
        ConnectedPlayer connected = connectPlayer(server, overworld);
        ServerPlayer player = connected.player();
        try {
            BlockPos expectedReturn = SOURCE_CONTROLLER.above();
            player.moveTo(expectedReturn.getBottomCenter(), 37.0F, -11.0F);

            GateTravelService.TravelResult outbound =
                    GateTravelService.INSTANCE.travelToFarRelay(
                            player, SOURCE_CONTROLLER);

            require(outbound == GateTravelService.TravelResult.SUCCESS,
                    "Production outbound route failed: " + outbound);
            require(player.serverLevel() == relay,
                    "Production outbound route did not enter afterlight:far_relay");
            BlockPos arrival = FarRelayInitializer.centralArrival(relay).orElseThrow(
                    () -> new IllegalStateException(
                            "Production outbound route has no central arrival"));
            require(player.blockPosition().equals(arrival),
                    "Production outbound route missed the central safe arrival");
            for (RelaySite site : RelaySite.values()) {
                require(
                        data.presentationVersion(site)
                                == FarRelayStructurePlan.PRESENTATION_VERSION,
                        "Production travel did not complete migration: " + site);
            }
            require(relay.getBlockState(conflict).is(Blocks.DIAMOND_BLOCK),
                    "Production migration replaced the x=8 player conflict");
            require(relay.getBlockState(returnPosition).equals(customReturnState()),
                    "Production migration replaced the custom return terminal state");
            require(relay.getBlockState(consolePosition).equals(plannedConsoleState()),
                    "Production migration did not recover the exact future console state");
            require(
                    requiredChest(relay, chestPosition)
                            .saveWithFullMetadata(relay.registryAccess())
                            .equals(chestBeforeTravel),
                    "Production migration reset the consumed Relay chest");

            BlockPos safeUpgrade = FarRelayStructurePlan.worldPosition(
                    RelaySite.CENTRAL,
                    floorY,
                    FarRelayStructurePlan.forSite(RelaySite.CENTRAL)
                            .placementAt(0, 13, -10)
                            .orElseThrow());
            require(relay.getBlockState(safeUpgrade).is(EchoContent.GATE_FRAME.get()),
                    "Production migration skipped a safe cathedral upgrade");
            relay.setBlock(safeUpgrade, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            Map<BlockPos, BlockState> beforeSecondEnsure = snapshotAllPlans(relay, data);
            server.saveEverything(false, true, false);
            require(!data.isDirty(), "Migrated Relay data remained dirty after save");

            FarRelayInitializer.ensureAll(relay);

            require(snapshotAllPlans(relay, data).equals(beforeSecondEnsure),
                    "Current presentation performed repeated migration writes");
            require(relay.getBlockState(safeUpgrade).isAir(),
                    "Current presentation retried a decorative migration");
            require(!data.isDirty(), "Second production ensure dirtied migration data");
            require(relay.getBlockState(conflict).is(Blocks.DIAMOND_BLOCK),
                    "Second production ensure replaced the x=8 player conflict");
            require(
                    requiredChest(relay, chestPosition)
                            .saveWithFullMetadata(relay.registryAccess())
                            .equals(chestBeforeTravel),
                    "Second production ensure changed the consumed Relay chest");
            require(relay.getBlockState(returnPosition).equals(customReturnState()),
                    "Second production ensure changed the custom return terminal state");
            require(relay.getBlockState(consolePosition).equals(plannedConsoleState()),
                    "Second production ensure changed the recovered future console state");

            require(GateTravelService.INSTANCE.returnPlayer(player),
                    "Production return route failed");
            require(player.serverLevel() == overworld,
                    "Production return route did not reach the Overworld");
            require(player.blockPosition().equals(expectedReturn),
                    "Production return route missed the stored source position");
            require(player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                    "Production return route retained its stored target");

        } finally {
            server.getPlayerList().remove(player);
            connected.channel().finishAndReleaseAll();
        }
        server.saveEverything(false, true, false);
        DedicatedMigrationAcceptanceMarker.write(
                marker,
                DedicatedMigrationAcceptanceMarker.Phase.VERIFY,
                token,
                Map.ofEntries(
                        Map.entry("anchors", "conflict_chest_return_console_cathedral"),
                        Map.entry("chest_state", "consumed_diamond_7"),
                        Map.entry("conflict", coordinate(conflict)),
                        Map.entry("dimension", relay.dimension().location().toString()),
                        Map.entry("floor_y", Integer.toString(floorY)),
                        Map.entry("future_console", "east_active"),
                        Map.entry("migration_writes", "none_on_second_ensure"),
                        Map.entry("outbound", "overworld_to_far_relay"),
                        Map.entry(
                                "presentation_version",
                                Integer.toString(FarRelayStructurePlan.PRESENTATION_VERSION)),
                        Map.entry("return_route", "far_relay_to_overworld"),
                        Map.entry("return_terminal", "south_inactive_preserved")));
        System.out.println(
                "AFTERLIGHT DEDICATED MIGRATION VERIFY: OK level="
                        + relay.dimension().location()
                        + " outbound=SUCCESS return=SUCCESS conflict="
                        + conflict
                        + " terminal="
                        + relay.getBlockState(consolePosition)
                        + " repeated_writes=0");
        server.halt(false);
    }

    private static ServerLevel requiredRelay(MinecraftServer server) {
        ServerLevel relay = server.getLevel(FarRelayKeys.LEVEL);
        if (relay == null) {
            throw new IllegalStateException(
                    "Dedicated migration server did not create afterlight:far_relay");
        }
        relay.getChunk(0, 0);
        return relay;
    }

    private static void reduceToPreV2(ServerLevel level, RelaySite site, int platformY) {
        for (FarRelayStructurePlan.Placement placement :
                FarRelayStructurePlan.forSite(site).placements()) {
            if (!legacyCorePlacement(site, placement)) {
                level.setBlock(
                        FarRelayStructurePlan.worldPosition(site, platformY, placement),
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
            }
        }
    }

    private static boolean legacyCorePlacement(
            RelaySite site, FarRelayStructurePlan.Placement placement) {
        if (placement.y() == 0
                && Math.abs(placement.x()) <= 5
                && Math.abs(placement.z()) <= 5) {
            return true;
        }
        if (placement.x() == 0 && placement.y() == 1 && placement.z() == 3) {
            return true;
        }
        return site == RelaySite.CENTRAL
                && placement.y() == 1
                && placement.z() == 0
                && Math.abs(placement.x()) == 3;
    }

    private static ConnectedPlayer connectPlayer(
            MinecraftServer server, ServerLevel overworld) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(PLAYER_ID, PLAYER_NAME), false);
        ServerPlayer player = new ServerPlayer(
                server,
                overworld,
                cookie.gameProfile(),
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new ConnectedPlayer(player, channel);
    }

    private static void prepareSafeSource(ServerLevel overworld) {
        overworld.setBlock(SOURCE_CONTROLLER, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        overworld.setBlock(SOURCE_CONTROLLER.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        overworld.setBlock(SOURCE_CONTROLLER.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Map<BlockPos, BlockState> snapshotAllPlans(
            ServerLevel level, FarRelaySavedData data) {
        LinkedHashMap<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        for (RelaySite site : RelaySite.values()) {
            int platformY = data.platformY(site).orElseThrow();
            for (FarRelayStructurePlan.Placement placement :
                    FarRelayStructurePlan.forSite(site).placements()) {
                BlockPos position = FarRelayStructurePlan.worldPosition(
                        site, platformY, placement);
                snapshot.put(position, level.getBlockState(position));
            }
        }
        return Map.copyOf(snapshot);
    }

    private static ChestBlockEntity requiredChest(ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof ChestBlockEntity chest) {
            return chest;
        }
        throw new IllegalStateException("Relay chest is unavailable at " + position);
    }

    private static void requireConsumedChest(ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(0);
        require(chest.getLootTable() == null, "Restarted Relay chest regained pending loot");
        require(stack.is(Items.DIAMOND) && stack.getCount() == 7,
                "Restarted Relay chest lost its preserved contents");
    }

    private static BlockState customReturnState() {
        return EchoContent.RETURN_TERMINAL
                .get()
                .defaultBlockState()
                .setValue(SignalTerminalBlock.FACING, Direction.SOUTH)
                .setValue(SignalTerminalBlock.ACTIVE, false);
    }

    private static BlockState plannedConsoleState() {
        FarRelayStructurePlan.Placement placement = FarRelayStructurePlan
                .forSite(RelaySite.CENTRAL)
                .placementAt(-3, 1, 0)
                .orElseThrow();
        return EchoContent.FUTURE_CONSOLE
                .get()
                .defaultBlockState()
                .setValue(SignalTerminalBlock.FACING, placement.facing())
                .setValue(SignalTerminalBlock.ACTIVE, placement.active());
    }

    private static BlockPos conflictPosition(int floorY) {
        return new BlockPos(RelaySite.CENTRAL.x() + 8, floorY, RelaySite.CENTRAL.z());
    }

    private static BlockPos terminalPosition(int floorY, int x) {
        return new BlockPos(RelaySite.CENTRAL.x() + x, floorY + 1, RelaySite.CENTRAL.z());
    }

    private static BlockPos chestPosition(int floorY) {
        return new BlockPos(RelaySite.CENTRAL.x(), floorY + 1, RelaySite.CENTRAL.z() + 3);
    }

    private static String coordinate(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing dedicated migration property: " + name);
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {}
}
