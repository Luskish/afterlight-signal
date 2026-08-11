package org.rllabs.afterlight.echo;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.network.OpenEchoRequest;
import org.rllabs.afterlight.network.OpenEchoScreen;

@GameTestHolder(Afterlight.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class EchoGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";

    private EchoGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void firstLoginIssuesEcho(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.assertTrue(player.getExistingData(EchoContent.ECHO_BOND).isEmpty(), "bond changed before the scheduled tick");
        helper.assertValueEqual(0, echoStacks(player).size(), "ECHO item count before the scheduled tick");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(
                    player.getExistingData(EchoContent.ECHO_BOND).isEmpty(),
                    "bond changed before exactly one server post-tick");
            helper.assertValueEqual(
                    0,
                    echoStacks(player).size(),
                    "ECHO item count before exactly one server post-tick");

            fireServerPostTick(helper);

            EchoBond bond = player.getData(EchoContent.ECHO_BOND);
            List<ItemStack> stacks = echoStacks(player);

            helper.assertTrue(bond.issued(), "bond was not issued");
            helper.assertValueEqual(1, bond.generation(), "first issue generation");
            helper.assertValueEqual(1, stacks.size(), "first issue item count");
            helper.assertValueEqual(
                    new EchoIdentity(player.getUUID(), 1),
                    stacks.getFirst().get(EchoContent.ECHO_IDENTITY.get()),
                    "first issue identity");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void logoutCancelsPendingIssue(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();

        helper.assertTrue(hasPendingIssue(server, player.getUUID()), "login did not schedule pending issue");
        server.getPlayerList().remove(player);
        helper.assertFalse(hasPendingIssue(server, player.getUUID()), "logout retained pending issue");

        helper.runAfterDelay(1, () -> {
            fireServerPostTick(helper);
            helper.assertTrue(player.getExistingData(EchoContent.ECHO_BOND).isEmpty(), "logged-out session received bond");
            helper.assertValueEqual(0, echoStacks(player).size(), "logged-out session received ECHO item");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 50)
    public static void reconnectDelayResets(GameTestHelper helper) {
        ServerPlayer sessionA = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        UUID playerId = sessionA.getUUID();

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(
                    sessionA.getExistingData(EchoContent.ECHO_BOND).isEmpty(),
                    "session A issued before its pending post-tick");
            server.getPlayerList().remove(sessionA);
            RecordingServerPlayer sessionB = makeRecordingServerPlayer(helper, playerId, "echo-reconnect-b");

            fireServerPostTick(helper);

            helper.assertTrue(
                    sessionA.getExistingData(EchoContent.ECHO_BOND).isEmpty(),
                    "session A issued session B's unit");
            helper.assertTrue(
                    sessionB.getExistingData(EchoContent.ECHO_BOND).isEmpty(),
                    "session B inherited session A's due tick");
            helper.assertValueEqual(0, echoStacks(sessionB).size(), "session B item count on session A's due tick");

            helper.runAfterDelay(1, () -> {
                helper.assertTrue(
                        sessionB.getExistingData(EchoContent.ECHO_BOND).isEmpty(),
                        "session B issued before its own post-tick");
                helper.assertValueEqual(0, echoStacks(sessionB).size(), "session B item count before its own post-tick");

                fireServerPostTick(helper);

                helper.assertTrue(sessionB.getData(EchoContent.ECHO_BOND).issued(), "session B did not issue after its own post-tick");
                helper.assertValueEqual(1, echoStacks(sessionB).size(), "session B item count after its own post-tick");
                succeed(helper, sessionB);
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 50)
    public static void secondLoginDoesNotIssueAgain(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            EchoBond originalBond = player.getData(EchoContent.ECHO_BOND);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.runAfterDelay(2, () -> {
                helper.assertValueEqual(originalBond, player.getData(EchoContent.ECHO_BOND), "bond after second login");
                helper.assertValueEqual(1, echoStacks(player).size(), "item count after second login");
                succeed(helper, player);
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 50)
    public static void recoveryIncrementsGeneration(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), "echo recover");
            EchoBond bond = player.getData(EchoContent.ECHO_BOND);
            List<Integer> generations = echoStacks(player).stream()
                    .map(stack -> stack.get(EchoContent.ECHO_IDENTITY.get()))
                    .map(EchoIdentity::generation)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            helper.assertValueEqual(2, bond.generation(), "recovery generation");
            helper.assertValueEqual(List.of(1, 2), generations, "issued item generations");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void fullInventoryRefusesIssue(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE));
        }

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(player.getExistingData(EchoContent.ECHO_BOND).isEmpty(), "bond changed after refused issue");
            helper.assertValueEqual(0, echoStacks(player).size(), "ECHO item count after refused issue");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void foreignItemRefusesOpen(GameTestHelper helper) {
        RecordingServerPlayer player = makeRecordingServerPlayer(helper);

        helper.runAfterDelay(2, () -> {
            int generation = player.getData(EchoContent.ECHO_BOND).generation();
            var playerId = player.getUUID();
            var foreignOwner = new UUID(
                    ~playerId.getMostSignificantBits(),
                    ~playerId.getLeastSignificantBits());
            var foreignStack = echoStack(new EchoIdentity(foreignOwner, generation));
            player.setItemInHand(InteractionHand.MAIN_HAND, foreignStack);
            var context = new RecordingPayloadContext(player);
            player.clearMessages();

            AfterlightPayloads.handleOpenRequest(new OpenEchoRequest(InteractionHand.MAIN_HAND), context);

            helper.assertValueEqual(0, context.replies().size(), "foreign item approval count");
            helper.assertValueEqual(
                    List.of(new ReceivedMessage(
                            Component.translatable("message.afterlight.echo.foreign_unit"),
                            false)),
                    player.receivedMessages(),
                    "foreign item rejection recipient and message");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 50)
    public static void staleItemRefusesOpen(GameTestHelper helper) {
        RecordingServerPlayer player = makeRecordingServerPlayer(helper);

        helper.runAfterDelay(2, () -> {
            EchoIdentity staleIdentity = echoStacks(player).getFirst().get(EchoContent.ECHO_IDENTITY.get());
            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), "echo recover");
            player.setItemInHand(InteractionHand.MAIN_HAND, echoStack(staleIdentity));
            var context = new RecordingPayloadContext(player);
            player.clearMessages();

            AfterlightPayloads.handleOpenRequest(new OpenEchoRequest(InteractionHand.MAIN_HAND), context);

            helper.assertValueEqual(0, context.replies().size(), "stale item approval count");
            helper.assertValueEqual(
                    List.of(new ReceivedMessage(
                            Component.translatable("message.afterlight.echo.superseded_unit"),
                            false)),
                    player.receivedMessages(),
                    "stale item rejection recipient and message");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void validItemReceivesApproval(GameTestHelper helper) {
        RecordingServerPlayer player = makeRecordingServerPlayer(helper);

        helper.runAfterDelay(2, () -> {
            ItemStack validStack = echoStacks(player).getFirst();
            player.setItemInHand(InteractionHand.MAIN_HAND, validStack);
            var context = new RecordingPayloadContext(player);
            player.clearMessages();

            AfterlightPayloads.handleOpenRequest(new OpenEchoRequest(InteractionHand.MAIN_HAND), context);

            helper.assertTrue(
                    context.replies().size() == 1 && context.replies().getFirst() == OpenEchoScreen.INSTANCE,
                    "valid item did not receive exactly OpenEchoScreen.INSTANCE");
            helper.assertValueEqual(List.of(), player.receivedMessages(), "valid item received rejection message");
            succeed(helper, player);
        });
    }

    private static ItemStack echoStack(EchoIdentity identity) {
        var stack = new ItemStack(EchoContent.ECHO.get());
        stack.set(EchoContent.ECHO_IDENTITY.get(), identity);
        return stack;
    }

    private static List<ItemStack> echoStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(EchoContent.ECHO.get())) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    private static RecordingServerPlayer makeRecordingServerPlayer(GameTestHelper helper) {
        return makeRecordingServerPlayer(helper, UUID.randomUUID(), "echo-recording-player");
    }

    private static RecordingServerPlayer makeRecordingServerPlayer(
            GameTestHelper helper,
            UUID playerId,
            String playerName) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(playerId, playerName), false);
        RecordingServerPlayer player = new RecordingServerPlayer(
                server,
                helper.getLevel(),
                cookie.gameProfile(),
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void fireServerPostTick(GameTestHelper helper) {
        EchoPlayerEvents.onServerTick(new ServerTickEvent.Post(() -> true, helper.getLevel().getServer()));
    }

    private static boolean hasPendingIssue(MinecraftServer server, UUID playerId) {
        try {
            Field field = EchoPlayerEvents.class.getDeclaredField("PENDING_FIRST_ISSUES");
            field.setAccessible(true);
            Map<?, ?> pendingByServer = (Map<?, ?>) field.get(null);
            Map<?, ?> pendingByPlayer = (Map<?, ?>) pendingByServer.get(server);
            return pendingByPlayer != null && pendingByPlayer.containsKey(playerId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect pending ECHO issues", exception);
        }
    }

    private static void succeed(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private record ReceivedMessage(Component component, boolean actionBar) {
    }

    private static final class RecordingServerPlayer extends ServerPlayer {
        private final List<ReceivedMessage> receivedMessages = new ArrayList<>();

        private RecordingServerPlayer(
                MinecraftServer server,
                ServerLevel level,
                GameProfile profile,
                ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return true;
        }

        @Override
        public void displayClientMessage(Component component, boolean actionBar) {
            receivedMessages.add(new ReceivedMessage(component, actionBar));
        }

        private List<ReceivedMessage> receivedMessages() {
            return List.copyOf(receivedMessages);
        }

        private void clearMessages() {
            receivedMessages.clear();
        }
    }

    private static final class RecordingPayloadContext implements IPayloadContext {
        private final ServerPlayer player;
        private final List<CustomPacketPayload> replies = new ArrayList<>();

        private RecordingPayloadContext(ServerPlayer player) {
            this.player = player;
        }

        private List<CustomPacketPayload> replies() {
            return List.copyOf(replies);
        }

        @Override
        public ICommonPacketListener listener() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            replies.add(payload);
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public ConnectionProtocol protocol() {
            return ConnectionProtocol.PLAY;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new UnsupportedOperationException();
        }
    }
}
