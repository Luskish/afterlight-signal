package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class SignalTitleContractTest {
    private static final String SIGNAL_SCREEN = "org.rllabs.afterlight.client.SignalTitleScreen";
    private static final String TITLE_HOOK = "org.rllabs.afterlight.client.SignalTitleScreenHook";

    @Test
    void exposesExactlyTheFiveSignalReliquaryDestinations() {
        assertEquals(
                List.of("Solo Expedition", "Join Expedition", "Configuration", "Mods", "Disconnect"),
                invokeStatic(SIGNAL_SCREEN, "menuLabels"));
    }

    @Test
    void replacesOnlyVanillaTitleScreenWhenEnabled() {
        TitleScreen vanilla = new TitleScreen();

        Screen replacement = replacementFor(vanilla, true);

        assertNotSame(vanilla, replacement);
        assertEquals(SIGNAL_SCREEN, replacement.getClass().getName());
    }

    @Test
    void disabledReplacementYieldsToVanillaTitleScreen() {
        TitleScreen vanilla = new TitleScreen();

        assertSame(vanilla, replacementFor(vanilla, false));
    }

    @Test
    void replacementIgnoresAnExistingSignalTitleScreen() {
        Screen signal = newSignalScreen();

        assertSame(signal, replacementFor(signal, true));
    }

    @Test
    void replacementNeverChangesANonTitleScreen() {
        Screen other = new StubScreen();

        assertSame(other, replacementFor(other, true));
    }

    @Test
    void signalTitleUsesNativeScreenNarrationAndCannotCloseOnEscape() {
        Screen signal = newSignalScreen();

        assertEquals(signal.getTitle(), signal.getNarrationMessage());
        assertFalse(signal.shouldCloseOnEsc());
    }

    @Test
    void shipsTheApprovedDeterministicTitleArtwork() throws Exception {
        Path artwork = Path.of("src/main/resources/assets/afterlight/textures/gui/title.png");
        if (Files.notExists(artwork)) {
            fail("Missing Signal Reliquary title artwork: " + artwork);
        }

        byte[] bytes = Files.readAllBytes(artwork);
        assertEquals(
                "de2ae0500c98b2f9feffa1760b7a05745c0f3cd44e495dcf1125f7ea5fa34104",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private static Screen replacementFor(Screen screen, boolean enabled) {
        return (Screen) invokeStatic(TITLE_HOOK, "replacementFor", new Class<?>[] {Screen.class, boolean.class}, screen, enabled);
    }

    private static Screen newSignalScreen() {
        Class<?> type = requireClass(SIGNAL_SCREEN);
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Screen) constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            return fail("Signal title screen must have a no-argument constructor", exception);
        }
    }

    private static Object invokeStatic(String className, String methodName) {
        return invokeStatic(className, methodName, new Class<?>[0]);
    }

    private static Object invokeStatic(
            String className,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments) {
        Class<?> type = requireClass(className);
        try {
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (NoSuchMethodException exception) {
            return fail(className + " must define " + methodName, exception);
        } catch (IllegalAccessException exception) {
            return fail("Cannot invoke " + className + "." + methodName, exception);
        } catch (InvocationTargetException exception) {
            return fail(className + "." + methodName + " failed", exception.getCause());
        }
    }

    private static Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return fail("Missing production class: " + className, exception);
        }
    }

    private static final class StubScreen extends Screen {
        private StubScreen() {
            super(Component.literal("Stub"));
        }
    }
}
