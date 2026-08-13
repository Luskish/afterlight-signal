package org.rllabs.afterlight.client;

import com.mojang.logging.LogUtils;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

public final class SignalTitleScreenHook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SignalTitleScreenHook DEFAULT = new SignalTitleScreenHook(
            SignalClientConfig::titleReplacementEnabled,
            SignalTitleScreen::new);
    private final BooleanSupplier enabled;
    private final Supplier<? extends Screen> factory;

    SignalTitleScreenHook(BooleanSupplier enabled, Supplier<? extends Screen> factory) {
        this.enabled = enabled;
        this.factory = factory;
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        DEFAULT.handle(event);
    }

    void handle(ScreenEvent.Opening event) {
        Screen original = event.getNewScreen();
        try {
            if (!this.enabled.getAsBoolean()
                    || original instanceof SignalTitleScreen
                    || original == null
                    || !(original instanceof TitleScreen)) {
                return;
            }
            event.setNewScreen(this.factory.get());
        } catch (RuntimeException exception) {
            LOGGER.error("AFTERLIGHT title replacement failed. Retaining the requested screen.", exception);
        }
    }
}
