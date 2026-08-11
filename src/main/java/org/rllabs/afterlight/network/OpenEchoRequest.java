package org.rllabs.afterlight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.rllabs.afterlight.Afterlight;

public record OpenEchoRequest(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<OpenEchoRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Afterlight.MOD_ID, "open_echo_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEchoRequest> STREAM_CODEC = StreamCodec.of(
            (buffer, request) -> buffer.writeEnum(request.hand()),
            buffer -> new OpenEchoRequest(buffer.readEnum(InteractionHand.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
