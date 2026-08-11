package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.authlib.minecraft.BanDetails;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;

class SignalTitleContractTest {
    private static final Path TITLE_ARTWORK =
            Path.of("src/main/resources/assets/afterlight/textures/gui/title.png");

    @Test
    void openingEventReplacesOnlyTheExactVanillaTitleScreen() {
        TitleScreen vanilla = new TitleScreen();
        ScreenEvent.Opening event = new ScreenEvent.Opening(null, vanilla);

        SignalTitleScreenHook.onScreenOpening(event);

        assertNotSame(vanilla, event.getNewScreen());
        assertInstanceOf(SignalTitleScreen.class, event.getNewScreen());
    }

    @Test
    void disabledReplacementPolicyLeavesOpeningEventVanilla() {
        TitleScreen vanilla = new TitleScreen();
        ScreenEvent.Opening event = new ScreenEvent.Opening(null, vanilla);

        invokeHook(event, () -> false, SignalTitleScreen::new);

        assertSame(vanilla, event.getNewScreen());
    }

    @Test
    void openingEventLeavesExistingSignalTitleUnchanged() {
        SignalTitleScreen signal = new SignalTitleScreen();
        ScreenEvent.Opening event = new ScreenEvent.Opening(null, signal);

        SignalTitleScreenHook.onScreenOpening(event);

        assertSame(signal, event.getNewScreen());
    }

    @Test
    void openingEventLeavesNonTitleScreenUnchanged() {
        Screen other = new StubScreen();
        ScreenEvent.Opening event = new ScreenEvent.Opening(null, other);

        SignalTitleScreenHook.onScreenOpening(event);

        assertSame(other, event.getNewScreen());
    }

    @Test
    void replacementFailureLeavesOpeningEventVanilla() {
        TitleScreen vanilla = new TitleScreen();
        ScreenEvent.Opening event = new ScreenEvent.Opening(null, vanilla);

        invokeHook(event, () -> true, () -> {
            throw new IllegalStateException("test replacement failure");
        });

        assertSame(vanilla, event.getNewScreen());
    }

    @Test
    void clientConstructorRegistersClientConfigWithExactKeyAndDefault() {
        RecordingModContainer container = new RecordingModContainer();

        new AfterlightClient(container);

        assertEquals(ModConfig.Type.CLIENT, container.type);
        assertSame(SignalClientConfig.SPEC, container.spec);
        ModConfigSpec.ConfigValue<?> value = configValue("replaceTitleScreen");
        assertEquals(List.of("replaceTitleScreen"), value.getPath());
        assertEquals(true, value.getDefault());
    }

    @Test
    void initializedScreenContainsExactlyFiveOrderedNativeButtons() {
        SignalTitleScreen screen = initializedScreen(new RecordingClient(), 854, 480);

        List<Button> buttons = buttons(screen);
        assertEquals(5, screen.children().size());
        assertEquals(5, buttons.size());
        assertEquals(
                List.of("Solo Expedition", "Join Expedition", "Configuration", "Mods", "Disconnect"),
                buttons.stream().map(button -> button.getMessage().getString()).toList());
    }

    @Test
    void destinationCallbacksOpenVanillaScreensAndDisconnect() {
        RecordingClient client = new RecordingClient();
        SignalTitleScreen screen = initializedScreen(client, 854, 480);
        List<Button> buttons = buttons(screen);

        buttons.get(0).onPress();
        assertInstanceOf(SelectWorldScreen.class, client.openedScreen);
        buttons.get(2).onPress();
        assertInstanceOf(OptionsScreen.class, client.openedScreen);
        buttons.get(3).onPress();
        assertInstanceOf(ModListScreen.class, client.openedScreen);
        buttons.get(4).onPress();
        assertTrue(client.stopped);
    }

    @Test
    void nativeTabFocusAndKeyboardActivationReachTheFirstDestination() throws Exception {
        RecordingClient client = new RecordingClient();
        SignalTitleScreen screen = initializedScreen(client, 854, 480);
        ComponentPath focusPath = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));

        assertNotNull(focusPath);
        focusPath.applyFocus(true);
        assertSame(buttons(screen).get(0), screen.getFocused());
        try (HeadlessMinecraft ignored = HeadlessMinecraft.install()) {
            assertTrue(assertDoesNotThrow(() -> screen.keyPressed(257, 0, 0)));
        }
        assertInstanceOf(SelectWorldScreen.class, client.openedScreen);
    }

    @Test
    void screenAndWidgetsUseNativeNarration() {
        SignalTitleScreen screen = initializedScreen(new RecordingClient(), 854, 480);
        NarrationCapture narration = new NarrationCapture();

        assertEquals(screen.getTitle(), screen.getNarrationMessage());
        buttons(screen).get(0).updateNarration(narration);
        assertTrue(narration.joined(NarratedElementType.TITLE).contains("Solo Expedition"));
        assertFalse(screen.shouldCloseOnEsc());
    }

    @Test
    void coverCropIsDeterministicForRequiredViewports() {
        assertCrop(1920, 1080, 0, 0, 1672, 941);
        assertCrop(3440, 1440, 0, 120, 1672, 700);
        assertCrop(854, 480, 0, 0, 1672, 940);
        assertCrop(1024, 768, 208, 0, 1255, 941);
    }

    @Test
    void customLayersRenderFromTheBackgroundOverrideBeforeInheritedWidgets() throws Exception {
        String descriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE,
                Type.getType(GuiGraphics.class),
                Type.INT_TYPE,
                Type.INT_TYPE,
                Type.FLOAT_TYPE);

        assertEquals(
                List.of("renderCoverBackground", "renderReliquaryFrame"),
                ownMethodCalls("renderBackground", descriptor));
        assertEquals(List.of(), ownMethodCalls("render", descriptor));
    }

    @Test
    void titleStatusReportsPackMinecraftNeoForgeAndEcho() throws Exception {
        SignalTitleScreen screen = initializedScreen(new RecordingClient(), 854, 480);

        assertEquals(
                List.of(
                        "PACK VERSION // 1.0.0-rc.1",
                        "MINECRAFT // 1.21.1",
                        "NEOFORGE // 21.1.248",
                        "ECHO CARRIER // STANDBY"),
                statusLines(screen));
        assertTrue(ownMethodCalls(
                        "renderReliquaryFrame",
                        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(GuiGraphics.class)))
                .contains("statusLines"));
    }

    @Test
    void productionAccessorReadsPackOwnedVersionAndFallsBackHonestly(@TempDir Path gameDirectory) throws Exception {
        Path versionFile = gameDirectory.resolve("config/afterlight/pack_version.txt");
        Files.createDirectories(versionFile.getParent());
        Files.writeString(versionFile, "0.9.0-rc.3\n");

        Class<?> accessType = Class.forName(
                "org.rllabs.afterlight.client.SignalTitleScreen$MinecraftClientAccess");
        Constructor<?> constructor = accessType.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        SignalTitleScreen.ClientAccess access = (SignalTitleScreen.ClientAccess) constructor.newInstance(gameDirectory);

        assertEquals("0.9.0-rc.3", access.packVersion());
        Files.writeString(versionFile, "1.0.0-rc.1\n");
        assertEquals("0.9.0-rc.3", access.packVersion());
        Files.writeString(versionFile, "   \n");
        access = (SignalTitleScreen.ClientAccess) constructor.newInstance(gameDirectory);
        assertEquals("UNAVAILABLE", access.packVersion());
        Files.delete(versionFile);
        access = (SignalTitleScreen.ClientAccess) constructor.newInstance(gameDirectory);
        assertEquals("UNAVAILABLE", access.packVersion());

        String descriptor = Type.getMethodDescriptor(Type.getType(String.class));
        assertFalse(methodCalls(accessType, "packVersion", descriptor).contains(
                "org/rllabs/afterlight/client/SignalTitleScreen$MinecraftClientAccess#modVersion"));
    }

    @Test
    void documentsThePackOwnedVersionFileForPublicDeliveryTaskTwo() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(readme.contains("config/afterlight/pack_version.txt"));
        assertTrue(readme.contains("Packwiz-managed UTF-8 file"));
        assertTrue(readme.contains("exactly match `pack.toml`'s `version` value"));
    }

    @Test
    void menuGeometryIsDeterministicAtMinimumAndNarrowFallback() {
        Geometry minimum = menuGeometry(854, 480);
        assertEquals(new Geometry(616, 182, 210, 20, 4, 116, 298), minimum);

        Geometry narrow = menuGeometry(128, 120);
        assertEquals(new Geometry(22, 11, 96, 18, 2, 98, 109), narrow);
        assertTrue(narrow.x() >= 0);
        assertTrue(narrow.x() + narrow.width() <= 128);
        assertTrue(narrow.bottom() <= 120);
    }

    @Test
    void titleArtworkDecodesAsTheApprovedReadableCinematicPng() throws Exception {
        byte[] bytes = Files.readAllBytes(TITLE_ARTWORK);
        BufferedImage image = ImageIO.read(TITLE_ARTWORK.toFile());
        assertNotNull(image);
        assertEquals(1672, image.getWidth());
        assertEquals(941, image.getHeight());
        assertFalse(image.getColorModel().hasAlpha());
        assertEquals(3, image.getColorModel().getNumComponents());
        assertEquals(8, Byte.toUnsignedInt(bytes[24]));
        assertEquals(2, Byte.toUnsignedInt(bytes[25]));
        assertEquals(0, Byte.toUnsignedInt(bytes[28]));
        assertTrue(Math.abs((double) image.getWidth() / image.getHeight() - 16.0 / 9.0) < 0.002);
        assertEquals(
                "de2ae0500c98b2f9feffa1760b7a05745c0f3cd44e495dcf1125f7ea5fa34104",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));

        try (ImageInputStream input = ImageIO.createImageInputStream(TITLE_ARTWORK.toFile())) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input);
                assertEquals("png", reader.getFormatName().toLowerCase());
                assertEquals(1672, reader.getWidth(0));
                assertEquals(941, reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private static void invokeHook(
            ScreenEvent.Opening event,
            BooleanSupplier enabled,
            Supplier<? extends Screen> factory) {
        try {
            Constructor<SignalTitleScreenHook> constructor = SignalTitleScreenHook.class.getDeclaredConstructor(
                    BooleanSupplier.class,
                    Supplier.class);
            constructor.setAccessible(true);
            SignalTitleScreenHook hook = constructor.newInstance(enabled, factory);
            Method method = SignalTitleScreenHook.class.getDeclaredMethod("handle", ScreenEvent.Opening.class);
            method.setAccessible(true);
            method.invoke(hook, event);
        } catch (NoSuchMethodException exception) {
            fail("Title hook must expose its production replacement policy as an event handler", exception);
        } catch (InvocationTargetException exception) {
            fail("Title hook must fail safe without escaping", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            fail("Unable to invoke title event handler", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static ModConfigSpec.ConfigValue<?> configValue(String key) {
        Object value = SignalClientConfig.SPEC.getValues().get(key);
        assertInstanceOf(ModConfigSpec.ConfigValue.class, value);
        return (ModConfigSpec.ConfigValue<?>) value;
    }

    private static SignalTitleScreen initializedScreen(
            SignalTitleScreen.ClientAccess client,
            int width,
            int height) {
        SignalTitleScreen screen = new SignalTitleScreen(client);
        screen.width = width;
        screen.height = height;
        screen.init();
        return screen;
    }

    private static List<Button> buttons(SignalTitleScreen screen) {
        return screen.children().stream().map(Button.class::cast).toList();
    }

    private static void assertCrop(
            int viewportWidth,
            int viewportHeight,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight) {
        try {
            Method method = SignalTitleScreen.class.getDeclaredMethod("coverCrop", int.class, int.class);
            method.setAccessible(true);
            Object crop = method.invoke(null, viewportWidth, viewportHeight);
            assertEquals(sourceX, intValue(crop, "sourceX"));
            assertEquals(sourceY, intValue(crop, "sourceY"));
            assertEquals(sourceWidth, intValue(crop, "sourceWidth"));
            assertEquals(sourceHeight, intValue(crop, "sourceHeight"));
        } catch (ReflectiveOperationException exception) {
            fail("Title rendering must use a deterministic cover crop", exception);
        }
    }

    private static Geometry menuGeometry(int width, int height) {
        try {
            Method method = SignalTitleScreen.class.getDeclaredMethod("menuGeometry", int.class, int.class);
            method.setAccessible(true);
            Object geometry = method.invoke(null, width, height);
            return new Geometry(
                    intValue(geometry, "x"),
                    intValue(geometry, "y"),
                    intValue(geometry, "width"),
                    intValue(geometry, "buttonHeight"),
                    intValue(geometry, "gap"),
                    intValue(geometry, "totalHeight"),
                    intValue(geometry, "bottom"));
        } catch (ReflectiveOperationException exception) {
            return fail("Title rendering must use deterministic menu geometry", exception);
        }
    }

    private static int intValue(Object record, String methodName) throws ReflectiveOperationException {
        Method method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (int) method.invoke(record);
    }

    private static List<String> ownMethodCalls(String methodName, String descriptor) throws Exception {
        return methodCalls(SignalTitleScreen.class, methodName, descriptor).stream()
                .filter(call -> call.startsWith("org/rllabs/afterlight/client/SignalTitleScreen#"))
                .map(call -> call.substring(call.indexOf('#') + 1))
                .toList();
    }

    private static List<String> methodCalls(Class<?> type, String methodName, String descriptor) throws Exception {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resourceName)) {
            assertNotNull(input, resourceName);
            List<String> calls = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String methodDescriptor,
                        String signature,
                        String[] exceptions) {
                    if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String invokedDescriptor,
                                boolean isInterface) {
                            calls.add(owner + "#" + name);
                        }
                    };
                }
            }, 0);
            return List.copyOf(calls);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> statusLines(SignalTitleScreen screen) throws Exception {
        Method method = SignalTitleScreen.class.getDeclaredMethod("statusLines");
        method.setAccessible(true);
        return ((List<Component>) method.invoke(screen)).stream().map(Component::getString).toList();
    }

    private record Geometry(
            int x,
            int y,
            int width,
            int buttonHeight,
            int gap,
            int totalHeight,
            int bottom) {
    }

    private static final class RecordingClient implements SignalTitleScreen.ClientAccess {
        private Screen openedScreen;
        private boolean stopped;

        @Override
        public boolean allowsMultiplayer() {
            return true;
        }

        @Override
        public boolean isNameBanned() {
            return false;
        }

        @Override
        public BanDetails multiplayerBan() {
            return null;
        }

        @Override
        public boolean skipMultiplayerWarning() {
            return true;
        }

        @Override
        public Options options() {
            return null;
        }

        @Override
        public void setScreen(Screen screen) {
            this.openedScreen = screen;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        public String packVersion() {
            return "1.0.0-rc.1";
        }

        public String minecraftVersion() {
            return "1.21.1";
        }

        public String neoForgeVersion() {
            return "21.1.248";
        }
    }

    private static final class NarrationCapture implements NarrationElementOutput {
        private final Map<NarratedElementType, List<String>> entries = new EnumMap<>(NarratedElementType.class);

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> thunk) {
            thunk.getText(text -> this.entries.computeIfAbsent(type, ignored -> new ArrayList<>()).add(text));
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        private String joined(NarratedElementType type) {
            return String.join(" ", this.entries.getOrDefault(type, List.of()));
        }
    }

    private static final class RecordingModContainer extends ModContainer {
        private ModConfig.Type type;
        private IConfigSpec spec;

        private RecordingModContainer() {
            super(modInfo());
        }

        @Override
        public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
            this.type = type;
            this.spec = spec;
        }

        @Override
        public IEventBus getEventBus() {
            return null;
        }

        private static IModInfo modInfo() {
            return (IModInfo) Proxy.newProxyInstance(
                    IModInfo.class.getClassLoader(),
                    new Class<?>[] {IModInfo.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getModId", "getNamespace" -> "afterlight";
                        case "getDisplayName" -> "AFTERLIGHT Signal";
                        case "toString" -> "TestModInfo";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }
    }

    private static final class HeadlessMinecraft implements AutoCloseable {
        private final Field instanceField;
        private final Minecraft previous;

        private HeadlessMinecraft(Field instanceField, Minecraft previous) {
            this.instanceField = instanceField;
            this.previous = previous;
        }

        private static HeadlessMinecraft install() throws Exception {
            Field instanceField = Minecraft.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Minecraft previous = (Minecraft) instanceField.get(null);
            if (previous == null) {
                Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Unsafe unsafe = (Unsafe) unsafeField.get(null);
                instanceField.set(null, unsafe.allocateInstance(Minecraft.class));
            }
            return new HeadlessMinecraft(instanceField, previous);
        }

        @Override
        public void close() throws IllegalAccessException {
            this.instanceField.set(null, this.previous);
        }
    }

    private static final class StubScreen extends Screen {
        private StubScreen() {
            super(Component.literal("Stub"));
        }
    }
}
