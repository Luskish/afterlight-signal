package org.rllabs.afterlight.echo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.client.AfterlightClient;
import org.rllabs.afterlight.network.OpenEchoRequest;
import org.rllabs.afterlight.network.OpenEchoScreen;

@SuppressWarnings("deprecation")
class EchoRuntimeContractTest {
    private static final ResourceLocation ECHO_ID = ResourceLocation.parse("afterlight:echo");
    private static final ResourceLocation IDENTITY_ID = ResourceLocation.parse("afterlight:echo_identity");
    private static final ResourceLocation BOND_ID = ResourceLocation.parse("afterlight:echo_bond");
    private static final ResourceLocation OPEN_REQUEST_ID = ResourceLocation.parse("afterlight:open_echo_request");
    private static final ResourceLocation OPEN_SCREEN_ID = ResourceLocation.parse("afterlight:open_echo_screen");

    @Test
    void registersIdentityBondAndEchoItem() throws Exception {
        DataComponentType<EchoIdentity> identityType = EchoContent.ECHO_IDENTITY.get();
        AttachmentType<EchoBond> bondType = EchoContent.ECHO_BOND.get();
        var echoItem = EchoContent.ECHO.get();

        assertSame(identityType, BuiltInRegistries.DATA_COMPONENT_TYPE.get(IDENTITY_ID));
        assertSame(bondType, NeoForgeRegistries.ATTACHMENT_TYPES.get(BOND_ID));
        assertSame(echoItem, BuiltInRegistries.ITEM.get(ECHO_ID));
        assertEquals(ECHO_ID, BuiltInRegistries.ITEM.getKey(echoItem));
        assertSame(EchoIdentity.CODEC, identityType.codec());

        var identity = new EchoIdentity(
                UUID.fromString("a67d9398-f1f0-455f-9ce9-7fc74a409dfa"),
                7);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        identityType.streamCodec().encode(buffer, identity);
        assertEquals(17, buffer.readableBytes());
        assertEquals(identity, identityType.streamCodec().decode(buffer));

        var serializer = AttachmentType.class.getDeclaredField("serializer");
        serializer.setAccessible(true);
        assertNotNull(serializer.get(bondType));
        var copyOnDeath = AttachmentType.class.getDeclaredField("copyOnDeath");
        copyOnDeath.setAccessible(true);
        assertTrue(copyOnDeath.getBoolean(bondType));

        var stack = new ItemStack(echoItem);
        assertEquals(1, stack.getMaxStackSize());
        assertEquals(Rarity.EPIC, stack.getRarity());
    }

    @Test
    void payloadsUseExactIdsAndRequestOnlyTheHand() {
        assertEquals(OPEN_REQUEST_ID, OpenEchoRequest.TYPE.id());
        assertEquals(OPEN_SCREEN_ID, OpenEchoScreen.TYPE.id());
        assertEquals(OpenEchoRequest.TYPE, new OpenEchoRequest(InteractionHand.OFF_HAND).type());
        assertEquals(OpenEchoScreen.TYPE, OpenEchoScreen.INSTANCE.type());

        var requestComponents = OpenEchoRequest.class.getRecordComponents();
        assertEquals(1, requestComponents.length);
        assertEquals("hand", requestComponents[0].getName());
        assertEquals(InteractionHand.class, requestComponents[0].getType());
        assertEquals(0, OpenEchoScreen.class.getRecordComponents().length);
    }

    @Test
    void payloadsAreRegisteredForExactPlayDirections() {
        assertNotNull(NetworkRegistry.getCodec(OPEN_REQUEST_ID, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND));
        assertNull(NetworkRegistry.getCodec(OPEN_REQUEST_ID, ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND));
        assertNotNull(NetworkRegistry.getCodec(OPEN_SCREEN_ID, ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND));
        assertNull(NetworkRegistry.getCodec(OPEN_SCREEN_ID, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND));
    }

    @Test
    void commandsEnforceRequiredPermissionLevels() {
        assertEquals(0, EchoCommands.RECOVER_PERMISSION_LEVEL);
        assertEquals(2, EchoCommands.INSPECT_PERMISSION_LEVEL);

        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        EchoCommands.register(dispatcher);
        var echo = dispatcher.getRoot().getChild("echo");
        assertNotNull(echo);
        var recover = echo.getChild("recover");
        var inspect = echo.getChild("inspect");
        assertNotNull(recover);
        assertNotNull(inspect);

        assertFalse(recover.canUse(commandSource(-1)));
        assertTrue(recover.canUse(commandSource(0)));
        assertTrue(recover.canUse(commandSource(1)));
        assertFalse(inspect.canUse(commandSource(0)));
        assertFalse(inspect.canUse(commandSource(1)));
        assertTrue(inspect.canUse(commandSource(2)));
    }

    @Test
    void languageContainsEveryRuntimeState() throws Exception {
        JsonObject language = JsonParser.parseString(Files.readString(
                        Path.of("src/main/resources/assets/afterlight/lang/en_us.json")))
                .getAsJsonObject();

        assertTrue(language.keySet().containsAll(Set.of(
                "item.afterlight.echo",
                "message.afterlight.echo.no_space",
                "message.afterlight.echo.insertion_failed",
                "message.afterlight.echo.generation_exhausted",
                "message.afterlight.echo.foreign_unit",
                "message.afterlight.echo.superseded_unit",
                "message.afterlight.echo.recovery_success",
                "message.afterlight.echo.first_issue",
                "message.afterlight.echo.inspect",
                "message.afterlight.echo.signal_not_acquired",
                "screen.afterlight.echo.placeholder.title",
                "screen.afterlight.echo.placeholder.body")));
    }

    @Test
    void everyCompiledCommonProductionClassContainsNoClientReferences() throws Exception {
        Path productionClasses = Path.of("build/classes/java/main");
        List<Path> classFiles;
        try (var paths = Files.walk(productionClasses)) {
            classFiles = paths.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !normalizedClassName(productionClasses, path)
                            .startsWith("org/rllabs/afterlight/client/"))
                    .toList();
        }
        assertFalse(classFiles.isEmpty());

        for (Path classFile : classFiles) {
            String className = normalizedClassName(productionClasses, classFile);
            String classBytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            assertFalse(classBytes.contains("net/minecraft/client"), () -> className + " has slash client dependency");
            assertFalse(classBytes.contains("net.minecraft.client"), () -> className + " has dotted client dependency");
            assertFalse(
                    classBytes.contains("org/rllabs/afterlight/client"),
                    () -> className + " has slash project client dependency");
            assertFalse(
                    classBytes.contains("org.rllabs.afterlight.client"),
                    () -> className + " has dotted project client dependency");
        }

        String echoItemBytes = classBytes(EchoItem.class);
        assertFalse(echoItemBytes.contains("getInstance"));

        Mod clientEntrypoint = AfterlightClient.class.getAnnotation(Mod.class);
        assertNotNull(clientEntrypoint);
        assertArrayEquals(new Dist[] {Dist.CLIENT}, clientEntrypoint.dist());
        assertTrue(Modifier.isPublic(AfterlightClient.class.getModifiers()));
    }

    @Test
    void commonEntrypointRegistersLogoutLifecycle() throws Exception {
        assertNotNull(EchoPlayerEvents.class.getMethod(
                "onPlayerLoggedOut", PlayerEvent.PlayerLoggedOutEvent.class));
        assertTrue(classBytes(Afterlight.class).contains("onPlayerLoggedOut"));
    }

    @Test
    void pendingIssueRetainsSessionOnlyThroughWeakReference() {
        Class<?> pendingType = Arrays.stream(EchoPlayerEvents.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("PendingFirstIssue"))
                .findFirst()
                .orElseThrow();
        List<Field> instanceFields = Arrays.stream(pendingType.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertFalse(
                instanceFields.stream().anyMatch(field -> ServerPlayer.class.isAssignableFrom(field.getType())),
                "pending issue has strong ServerPlayer field");
        Field sessionField = instanceFields.stream()
                .filter(field -> field.getType() == WeakReference.class)
                .findFirst()
                .orElse(null);
        assertNotNull(sessionField, "pending issue has no weak session reference");
        assertEquals("session", sessionField.getName());
        assertEquals(
                "java.lang.ref.WeakReference<net.minecraft.server.level.ServerPlayer>",
                sessionField.getGenericType().getTypeName());
    }

    @Test
    void manualTickGameTestsUseDistinctSerialBatches() throws Exception {
        List<String> batchNames = List.of(
                EchoGameTests.class
                        .getMethod("firstLoginIssuesEcho", GameTestHelper.class)
                        .getAnnotation(GameTest.class)
                        .batch(),
                EchoGameTests.class
                        .getMethod("logoutCancelsPendingIssue", GameTestHelper.class)
                        .getAnnotation(GameTest.class)
                        .batch(),
                EchoGameTests.class
                        .getMethod("reconnectDelayResets", GameTestHelper.class)
                        .getAnnotation(GameTest.class)
                        .batch());

        assertFalse(batchNames.stream().anyMatch(String::isBlank), "manual tick batch names must be nonblank");
        assertFalse(
                batchNames.stream().anyMatch(GameTestBatch.DEFAULT_BATCH_NAME::equals),
                "manual tick GameTests must not use the default batch");
        assertEquals(3, Set.copyOf(batchNames).size(), "manual tick GameTests must use distinct batches");
    }

    @Test
    void invalidOrUnissuedIdentityNeverAuthorizesOpening() {
        var service = new EchoRuntimeService();
        var playerId = UUID.fromString("d5e17688-69bb-479c-aa86-9ccb0783a976");

        assertEquals(
                EchoRuntimeService.OpenStatus.SIGNAL_NOT_ACQUIRED,
                service.validateIdentity(playerId, EchoBond.UNISSUED, null));
        assertEquals(
                EchoRuntimeService.OpenStatus.SIGNAL_NOT_ACQUIRED,
                service.validateIdentity(
                        playerId,
                        EchoBond.UNISSUED,
                        new EchoIdentity(playerId, 1)));
    }

    @Test
    void recoveryFailuresMapToDistinctEchoMessages() {
        var service = new EchoRuntimeService();

        assertEquals(
                "message.afterlight.echo.no_space",
                service.failureMessageKey(EchoRecoveryService.RecoveryStatus.NO_SPACE));
        assertEquals(
                "message.afterlight.echo.insertion_failed",
                service.failureMessageKey(EchoRecoveryService.RecoveryStatus.INSERT_FAILED));
        assertEquals(
                "message.afterlight.echo.generation_exhausted",
                service.failureMessageKey(EchoRecoveryService.RecoveryStatus.GENERATION_EXHAUSTED));
    }

    private static String classBytes(Class<?> type) throws Exception {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resourceName)) {
            assertNotNull(input, resourceName);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String normalizedClassName(Path root, Path classFile) {
        return root.relativize(classFile).toString().replace('\\', '/');
    }

    private static CommandSourceStack commandSource(int permissionLevel) {
        return new CommandSourceStack(
                CommandSource.NULL,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                permissionLevel,
                "contract-test",
                Component.literal("contract-test"),
                null,
                null);
    }
}
