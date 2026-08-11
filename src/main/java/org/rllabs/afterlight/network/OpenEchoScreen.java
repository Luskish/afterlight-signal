package org.rllabs.afterlight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.rllabs.afterlight.Afterlight;

public record OpenEchoScreen() implements CustomPacketPayload {
    public static final OpenEchoScreen INSTANCE = new OpenEchoScreen();
    public static final Type<OpenEchoScreen> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Afterlight.MOD_ID, "open_echo_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEchoScreen> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
