package org.rllabs.afterlight;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.rllabs.afterlight.echo.EchoBond;
import org.rllabs.afterlight.echo.EchoIdentity;
import org.rllabs.afterlight.echo.EchoItem;
import org.rllabs.afterlight.network.AfterlightPayloads;

public final class EchoContent {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Afterlight.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Afterlight.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Afterlight.MOD_ID);

    private static final StreamCodec<RegistryFriendlyByteBuf, EchoIdentity> ECHO_IDENTITY_STREAM_CODEC = StreamCodec.of(
            (buffer, identity) -> {
                buffer.writeUUID(identity.owner());
                buffer.writeVarInt(identity.generation());
            },
            buffer -> new EchoIdentity(buffer.readUUID(), buffer.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, EchoBond> ECHO_BOND_STREAM_CODEC = StreamCodec.of(
            (buffer, bond) -> {
                buffer.writeBoolean(bond.issued());
                buffer.writeInt(bond.generation());
                buffer.writeLong(bond.issuedAtEpochSecond());
            },
            buffer -> new EchoBond(buffer.readBoolean(), buffer.readInt(), buffer.readLong()));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EchoIdentity>> ECHO_IDENTITY =
            DATA_COMPONENTS.registerComponentType("echo_identity", builder -> builder
                    .persistent(EchoIdentity.CODEC)
                    .networkSynchronized(ECHO_IDENTITY_STREAM_CODEC));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EchoBond>> ECHO_BOND =
            ATTACHMENTS.register("echo_bond", () -> AttachmentType.builder(() -> EchoBond.UNISSUED)
                    .serialize(EchoBond.CODEC)
                    .copyOnDeath()
                    .sync(
                            (holder, recipient) -> holder == recipient
                                    && recipient.connection != null
                                    && NetworkRegistry.hasChannel(
                                            recipient.connection,
                                            SyncAttachmentsPayload.TYPE.id()),
                            ECHO_BOND_STREAM_CODEC)
                    .build());

    public static final DeferredItem<EchoItem> ECHO = ITEMS.registerItem(
            "echo",
            EchoItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    private EchoContent() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(AfterlightPayloads::register);
    }
}
