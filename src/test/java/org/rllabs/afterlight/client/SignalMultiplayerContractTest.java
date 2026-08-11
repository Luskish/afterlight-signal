package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.authlib.minecraft.BanDetails;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class SignalMultiplayerContractTest {
    private static final int SIGNAL_CYAN = 0xFF4FE6F2;
    private static final int OXIDIZED_METAL = 0xFF283238;

    @Test
    void profileRestrictionDisablesJoinWithNativeReasonNarration() {
        assertDisabledReason(
                new ClientState(false, false, null, true),
                "title.multiplayer.disabled");
    }

    @Test
    void nameBanDisablesJoinWithNativeReasonNarration() {
        assertDisabledReason(
                new ClientState(false, true, null, true),
                "title.multiplayer.disabled.banned.name");
    }

    @Test
    void temporaryBanDisablesJoinWithNativeReasonNarration() {
        assertDisabledReason(
                new ClientState(false, false, ban(Instant.parse("2030-01-01T00:00:00Z")), true),
                "title.multiplayer.disabled.banned.temporary");
    }

    @Test
    void permanentBanDisablesJoinWithNativeReasonNarration() {
        assertDisabledReason(
                new ClientState(false, false, ban(null), true),
                "title.multiplayer.disabled.banned.permanent");
    }

    @Test
    void enabledJoinHasNoDisabledReasonAndUsesActiveFocusDecoration() {
        ScreenFixture fixture = screen(new ClientState(true, true, ban(null), true));
        Button join = joinButton(fixture.screen());

        assertTrue(join.active);
        assertNull(join.getTooltip());
        join.setFocused(true);
        Decoration decoration = decoration(join);
        assertEquals(SIGNAL_CYAN, decoration.border());
        assertTrue(decoration.amberRail());
    }

    @Test
    void inactiveJoinNeverUsesActiveFocusDecoration() {
        ScreenFixture fixture = screen(new ClientState(false, false, null, true));
        Button join = joinButton(fixture.screen());

        join.setFocused(true);
        Decoration decoration = decoration(join);
        assertEquals(OXIDIZED_METAL, decoration.border());
        assertFalse(decoration.amberRail());
    }

    @Test
    void skipWarningOpensJoinMultiplayerDirectly() {
        ScreenFixture fixture = screen(new ClientState(true, false, null, true));

        joinButton(fixture.screen()).onPress();

        assertInstanceOf(JoinMultiplayerScreen.class, fixture.openedScreen());
    }

    @Test
    void enabledWarningOpensVanillaSafetyScreen() {
        ScreenFixture fixture = screen(new ClientState(true, false, null, false));

        joinButton(fixture.screen()).onPress();

        assertInstanceOf(SafetyScreen.class, fixture.openedScreen());
    }

    private static void assertDisabledReason(ClientState state, String translationKey) {
        ScreenFixture fixture = screen(state);
        Button join = joinButton(fixture.screen());
        NarrationCapture narration = new NarrationCapture();

        assertFalse(join.active);
        assertTrue(join.getTooltip() != null);
        join.setFocused(true);
        join.updateNarration(narration);
        assertEquals(
                List.of(Component.translatable(translationKey).getString()),
                narration.entries(NarratedElementType.HINT));
    }

    private static BanDetails ban(Instant expires) {
        return new BanDetails(
                UUID.fromString("87a93a0d-25d7-45f1-bb0f-9c5c2c22fe10"),
                expires,
                "reason",
                "reason message");
    }

    private static ScreenFixture screen(ClientState state) {
        try {
            Class<?> accessType = Class.forName(SignalTitleScreen.class.getName() + "$ClientAccess");
            ScreenFixture fixture = new ScreenFixture(state);
            Object access = Proxy.newProxyInstance(
                    accessType.getClassLoader(),
                    new Class<?>[] {accessType},
                    fixture);
            Constructor<SignalTitleScreen> constructor = SignalTitleScreen.class.getDeclaredConstructor(accessType);
            constructor.setAccessible(true);
            SignalTitleScreen screen = constructor.newInstance(access);
            screen.width = 854;
            screen.height = 480;
            screen.init();
            fixture.screen = screen;
            return fixture;
        } catch (ReflectiveOperationException exception) {
            return fail("Signal title must support a client boundary for behavioral tests", exception);
        }
    }

    private static Button joinButton(SignalTitleScreen screen) {
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Join Expedition"))
                .findFirst()
                .orElseThrow();
    }

    private static Decoration decoration(Button button) {
        try {
            Method method = button.getClass().getDeclaredMethod("decoration");
            method.setAccessible(true);
            Object value = method.invoke(button);
            Method border = value.getClass().getDeclaredMethod("border");
            Method amberRail = value.getClass().getDeclaredMethod("amberRail");
            border.setAccessible(true);
            amberRail.setAccessible(true);
            return new Decoration((int) border.invoke(value), (boolean) amberRail.invoke(value));
        } catch (ReflectiveOperationException exception) {
            return fail("Signal button must expose the render state it uses", exception);
        }
    }

    private record ClientState(
            boolean allowsMultiplayer,
            boolean nameBanned,
            BanDetails multiplayerBan,
            boolean skipMultiplayerWarning) {
    }

    private record Decoration(int border, boolean amberRail) {
    }

    private static final class ScreenFixture implements InvocationHandler {
        private final ClientState state;
        private SignalTitleScreen screen;
        private Screen openedScreen;

        private ScreenFixture(ClientState state) {
            this.state = state;
        }

        private SignalTitleScreen screen() {
            return this.screen;
        }

        private Screen openedScreen() {
            return this.openedScreen;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "allowsMultiplayer" -> this.state.allowsMultiplayer();
                case "isNameBanned" -> this.state.nameBanned();
                case "multiplayerBan" -> this.state.multiplayerBan();
                case "skipMultiplayerWarning" -> this.state.skipMultiplayerWarning();
                case "options" -> null;
                case "setScreen" -> {
                    this.openedScreen = (Screen) arguments[0];
                    yield null;
                }
                case "stop" -> null;
                case "toString" -> "ScreenFixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new AssertionError("Unexpected client access call: " + method.getName());
            };
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

        private List<String> entries(NarratedElementType type) {
            return this.entries.getOrDefault(type, List.of());
        }
    }
}
