package org.rllabs.afterlight.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SignalClientConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue REPLACE_TITLE_SCREEN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REPLACE_TITLE_SCREEN = builder
                .comment("Replace the vanilla title screen with the AFTERLIGHT Signal Reliquary.")
                .define("replaceTitleScreen", true);
        SPEC = builder.build();
    }

    private SignalClientConfig() {
    }

    static boolean titleReplacementEnabled() {
        try {
            return REPLACE_TITLE_SCREEN.get();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
