package org.rllabs.afterlight;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.rllabs.afterlight.echo.EchoCommands;
import org.rllabs.afterlight.echo.EchoPlayerEvents;

@Mod(Afterlight.MOD_ID)
public final class Afterlight {
    public static final String MOD_ID = "afterlight";

    public Afterlight(IEventBus modBus) {
        EchoContent.register(modBus);
        NeoForge.EVENT_BUS.addListener(EchoCommands::register);
        NeoForge.EVENT_BUS.addListener(EchoPlayerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(EchoPlayerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EchoPlayerEvents::onServerTick);
    }
}
