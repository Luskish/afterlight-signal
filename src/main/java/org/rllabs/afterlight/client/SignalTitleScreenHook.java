package org.rllabs.afterlight.client;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

public final class SignalTitleScreenHook {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SignalTitleScreenHook() {
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        Screen original = event.getNewScreen();
        try {
            Screen replacement = replacementFor(original, SignalClientConfig.titleReplacementEnabled());
            if (replacement != original) {
                event.setNewScreen(replacement);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("AFTERLIGHT title replacement failed. Retaining the requested screen.", exception);
        }
    }

    @Nullable
    static Screen replacementFor(@Nullable Screen screen, boolean enabled) {
        if (!enabled || screen instanceof SignalTitleScreen) {
            return screen;
        }
        return screen != null && screen.getClass() == TitleScreen.class ? new SignalTitleScreen() : screen;
    }
}
