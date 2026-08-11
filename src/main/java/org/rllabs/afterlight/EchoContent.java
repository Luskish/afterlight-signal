package org.rllabs.afterlight;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.rllabs.afterlight.echo.EchoBond;
import org.rllabs.afterlight.echo.EchoIdentity;
import org.rllabs.afterlight.echo.EchoItem;
import org.rllabs.afterlight.gate.GateControllerBlock;
import org.rllabs.afterlight.gate.GateControllerBlockEntity;
import org.rllabs.afterlight.gate.GateFieldBlock;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.relay.FutureConsoleBlock;
import org.rllabs.afterlight.relay.ReturnTerminalBlock;

public final class EchoContent {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Afterlight.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Afterlight.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Afterlight.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Afterlight.MOD_ID);
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

    public static final DeferredBlock<Block> GATE_FRAME = BLOCKS.registerSimpleBlock(
            "gate_frame", BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN));
    public static final DeferredBlock<Block> SIGNAL_GLASS = BLOCKS.registerSimpleBlock(
            "signal_glass",
            BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)
                    .strength(5.0F, 12.0F)
                    .sound(SoundType.AMETHYST));
    public static final DeferredBlock<GateControllerBlock> GATE_CONTROLLER = BLOCKS.registerBlock(
            "gate_controller",
            GateControllerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE));
    public static final DeferredBlock<GateFieldBlock> GATE_FIELD = BLOCKS.registerBlock(
            "gate_field",
            GateFieldBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 15)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable());
    public static final DeferredBlock<Block> RELAY_STONE = BLOCKS.registerSimpleBlock(
            "relay_stone", BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE));
    public static final DeferredBlock<ReturnTerminalBlock> RETURN_TERMINAL = BLOCKS.registerBlock(
            "return_terminal",
            ReturnTerminalBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.REINFORCED_DEEPSLATE));
    public static final DeferredBlock<FutureConsoleBlock> FUTURE_CONSOLE = BLOCKS.registerBlock(
            "future_console",
            FutureConsoleBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.REINFORCED_DEEPSLATE));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateControllerBlockEntity>>
            GATE_CONTROLLER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "gate_controller",
                    () -> BlockEntityType.Builder.of(
                                    GateControllerBlockEntity::new, GATE_CONTROLLER.get())
                            .build(null));

    public static final DeferredItem<?> GATE_FRAME_ITEM = ITEMS.registerSimpleBlockItem(GATE_FRAME);
    public static final DeferredItem<?> SIGNAL_GLASS_ITEM = ITEMS.registerSimpleBlockItem(SIGNAL_GLASS);
    public static final DeferredItem<?> GATE_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem(GATE_CONTROLLER);
    public static final DeferredItem<?> RELAY_STONE_ITEM = ITEMS.registerSimpleBlockItem(RELAY_STONE);
    public static final DeferredItem<?> RETURN_TERMINAL_ITEM =
            ITEMS.registerSimpleBlockItem(RETURN_TERMINAL);
    public static final DeferredItem<?> FUTURE_CONSOLE_ITEM =
            ITEMS.registerSimpleBlockItem(FUTURE_CONSOLE);

    private EchoContent() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(AfterlightPayloads::register);
    }
}
