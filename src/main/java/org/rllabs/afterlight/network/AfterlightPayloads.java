package org.rllabs.afterlight.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.rllabs.afterlight.echo.EchoRuntimeService;
import org.rllabs.afterlight.echo.EchoRuntimeService.OpenStatus;

public final class AfterlightPayloads {
    private static Consumer<OpenEchoScreen> clientOpenHandler = ignored -> {
    };

    private AfterlightPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(OpenEchoRequest.TYPE, OpenEchoRequest.STREAM_CODEC, AfterlightPayloads::handleOpenRequest);
        registrar.playToClient(OpenEchoScreen.TYPE, OpenEchoScreen.STREAM_CODEC, AfterlightPayloads::handleOpenScreen);
    }

    public static void installClientOpenHandler(Consumer<OpenEchoScreen> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static void handleOpenRequest(OpenEchoRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        OpenStatus status = EchoRuntimeService.INSTANCE.validateHeldItem(player, request.hand());
        if (status == OpenStatus.APPROVED) {
            context.reply(OpenEchoScreen.INSTANCE);
            return;
        }
        player.displayClientMessage(Component.translatable(status.messageKey()), false);
    }

    private static void handleOpenScreen(OpenEchoScreen payload, IPayloadContext context) {
        clientOpenHandler.accept(payload);
    }
}
