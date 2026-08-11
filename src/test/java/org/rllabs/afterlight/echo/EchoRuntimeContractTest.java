package org.rllabs.afterlight.echo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.client.AfterlightClient;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.network.OpenEchoRequest;
import org.rllabs.afterlight.network.OpenEchoScreen;

@SuppressWarnings("deprecation")
class EchoRuntimeContractTest {
    private static final ResourceLocation ECHO_ID = id("echo");
    private static final ResourceLocation IDENTITY_ID = id("echo_identity");
    private static final ResourceLocation BOND_ID = id("echo_bond");

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
        assertEquals(id("open_echo_request"), OpenEchoRequest.TYPE.id());
        assertEquals(id("open_echo_screen"), OpenEchoScreen.TYPE.id());
        assertEquals(OpenEchoRequest.TYPE, new OpenEchoRequest(InteractionHand.OFF_HAND).type());
        assertEquals(OpenEchoScreen.TYPE, OpenEchoScreen.INSTANCE.type());

        var requestComponents = OpenEchoRequest.class.getRecordComponents();
        assertEquals(1, requestComponents.length);
        assertEquals("hand", requestComponents[0].getName());
        assertEquals(InteractionHand.class, requestComponents[0].getType());
        assertEquals(0, OpenEchoScreen.class.getRecordComponents().length);
    }

    @Test
    void commandsExposeRequiredPermissionLevels() {
        assertEquals(0, EchoCommands.RECOVER_PERMISSION_LEVEL);
        assertEquals(2, EchoCommands.INSPECT_PERMISSION_LEVEL);
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
    void commonRuntimeClassesContainNoClientReferences() throws Exception {
        List<Class<?>> commonClasses = List.of(
                Afterlight.class,
                EchoContent.class,
                EchoItem.class,
                EchoRuntimeService.class,
                EchoCommands.class,
                EchoPlayerEvents.class,
                AfterlightPayloads.class,
                OpenEchoRequest.class,
                OpenEchoScreen.class);

        for (Class<?> commonClass : commonClasses) {
            String classBytes = new String(readClassBytes(commonClass), StandardCharsets.ISO_8859_1);
            assertFalse(
                    classBytes.contains("net/minecraft/client"),
                    () -> commonClass.getName() + " references a client class");
        }

        String echoItemBytes = new String(readClassBytes(EchoItem.class), StandardCharsets.ISO_8859_1);
        assertFalse(echoItemBytes.contains("getInstance"));

        Mod clientEntrypoint = AfterlightClient.class.getAnnotation(Mod.class);
        assertNotNull(clientEntrypoint);
        assertArrayEquals(new Dist[] {Dist.CLIENT}, clientEntrypoint.dist());
        assertTrue(Modifier.isPublic(AfterlightClient.class.getModifiers()));
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

    private static byte[] readClassBytes(Class<?> type) throws Exception {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resourceName)) {
            assertNotNull(input, resourceName);
            return input.readAllBytes();
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Afterlight.MOD_ID, path);
    }
}
