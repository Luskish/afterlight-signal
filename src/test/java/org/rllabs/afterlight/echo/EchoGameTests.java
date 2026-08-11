package org.rllabs.afterlight.echo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.network.OpenEchoRequest;

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
        helper.runAfterDelay(2, () -> {
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
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            int generation = player.getData(EchoContent.ECHO_BOND).generation();
            var playerId = player.getUUID();
            var foreignOwner = new UUID(
                    ~playerId.getMostSignificantBits(),
                    ~playerId.getLeastSignificantBits());
            var foreignStack = echoStack(new EchoIdentity(foreignOwner, generation));
            player.setItemInHand(InteractionHand.MAIN_HAND, foreignStack);
            var context = new RecordingPayloadContext(player);

            AfterlightPayloads.handleOpenRequest(new OpenEchoRequest(InteractionHand.MAIN_HAND), context);

            helper.assertValueEqual(0, context.replies().size(), "foreign item approval count");
            succeed(helper, player);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 50)
    public static void staleItemRefusesOpen(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            EchoIdentity staleIdentity = echoStacks(player).getFirst().get(EchoContent.ECHO_IDENTITY.get());
            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack(), "echo recover");
            player.setItemInHand(InteractionHand.MAIN_HAND, echoStack(staleIdentity));
            var context = new RecordingPayloadContext(player);

            AfterlightPayloads.handleOpenRequest(new OpenEchoRequest(InteractionHand.MAIN_HAND), context);

            helper.assertValueEqual(0, context.replies().size(), "stale item approval count");
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

    private static void succeed(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
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
