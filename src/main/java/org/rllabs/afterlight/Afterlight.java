package org.rllabs.afterlight;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Afterlight.MOD_ID)
public final class Afterlight {
    public static final String MOD_ID = "afterlight";

    public Afterlight(IEventBus modBus) {
        EchoContent.register(modBus);
    }
}
