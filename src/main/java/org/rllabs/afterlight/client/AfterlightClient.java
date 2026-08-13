package org.rllabs.afterlight.client;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.client.integration.FtbQuestGateway;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.network.OpenEchoScreen;
import org.rllabs.afterlight.route.EchoRoute;
import org.rllabs.afterlight.route.EchoRouteLoader;
import org.rllabs.afterlight.route.EchoRouteLoader.RouteValidationException;

@Mod(value = Afterlight.MOD_ID, dist = Dist.CLIENT)
public final class AfterlightClient {
    public AfterlightClient(IEventBus modBus, ModContainer modContainer) {
        this(modContainer);
        modBus.addListener(AfterlightClient::registerDimensionEffects);
        modBus.addListener(AfterlightClient::registerRenderers);
    }

    AfterlightClient(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, SignalClientConfig.SPEC);
        registerTitleScreenHook(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(EchoTooltip::onTooltip);
        AfterlightPayloads.installClientOpenHandler(AfterlightClient::openApprovedScreen);
    }

    static void registerTitleScreenHook(IEventBus eventBus) {
        eventBus.addListener(EventPriority.LOWEST, SignalTitleScreenHook::onScreenOpening);
    }

    static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        FarRelayEffects.register(event);
    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                EchoContent.GATE_CONTROLLER_BLOCK_ENTITY.get(), GateRenderer::new);
    }

    private static void openApprovedScreen(OpenEchoScreen payload) {
        FtbQuestGateway gateway = new FtbQuestGateway();
        try {
            EchoRoute route = new EchoRouteLoader().load(EchoRouteLoader.DEFAULT_PATH);
            Minecraft.getInstance().setScreen(new EchoScreen(route, gateway));
        } catch (IOException | RouteValidationException exception) {
            Minecraft.getInstance().setScreen(EchoScreen.signalUnavailable(gateway));
        }
    }
}
