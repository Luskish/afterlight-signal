package org.rllabs.afterlight.gate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateActivationService.ActivationCode;
import org.rllabs.afterlight.gate.GateActivationService.ActivationDecision;
import org.rllabs.afterlight.relay.FarRelayInitializer;
import org.rllabs.afterlight.relay.FarRelayKeys;
import org.rllabs.afterlight.visual.VisualHarnessIdentity;
import org.rllabs.afterlight.visual.VisualReadyMarkerPolicy;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;

@EventBusSubscriber(modid = Afterlight.MOD_ID)
public final class VisualAcceptanceServerHarness {
    private static boolean prepared;

    private VisualAcceptanceServerHarness() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equals(System.getProperty("afterlight.visual.acceptance"))
                || !"server".equals(System.getProperty("afterlight.visual.role"))
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!VisualHarnessIdentity.isExpected(player.getGameProfile())) {
            player.connection.disconnect(Component.literal(
                    "AFTERLIGHT visual acceptance rejects unexpected offline identities"));
            return;
        }
        player.server.getPlayerList().op(player.getGameProfile());
        if (!prepared) {
            prepareScenes(player.serverLevel());
            ServerLevel farRelay = player.server.getLevel(FarRelayKeys.LEVEL);
            if (farRelay == null) {
                throw new IllegalStateException("Far Relay is unavailable to visual acceptance");
            }
            FarRelayInitializer.ensureAll(farRelay);
            List<Evaluation> evaluations =
                    VisualAcceptanceServerSceneValidator.evaluate(player.server);
            if (!VisualReadyMarkerPolicy.mayWrite(true, evaluations)) {
                throw new IllegalStateException(
                        "Visual server scenes are not ready: " + evaluations);
            }
            writeReadyMarker();
            prepared = true;
            System.out.println("VISUAL SERVER: READY");
        }
        player.getInventory().add(new ItemStack(EchoContent.ECHO.get()));
    }

    private static void prepareScenes(ServerLevel level) {
        for (int x = -36; x <= 88; x++) {
            for (int z = -4; z <= 20; z++) {
                level.setBlock(
                        new BlockPos(x, 100, z),
                        EchoContent.RELAY_STONE.get().defaultBlockState(),
                        Block.UPDATE_ALL);
                level.setBlock(new BlockPos(x, 101, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(new BlockPos(x, 102, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        buildGate(level, new BlockPos(-24, 101, 0), GateState.IDLE);
        buildGate(level, new BlockPos(0, 101, 0), GateState.OPEN);
        buildGate(level, new BlockPos(24, 101, 0), GateState.FAULT);
        prepareItemViews(level);
    }

    private static void buildGate(ServerLevel level, BlockPos controllerPosition, GateState state) {
        Direction facing = Direction.SOUTH;
        for (var expected : GatePattern.expected(facing).entrySet()) {
            BlockPos position = expected.getKey().toWorld(controllerPosition, facing);
            BlockState blockState = switch (expected.getValue()) {
                case FRAME -> EchoContent.GATE_FRAME.get().defaultBlockState();
                case SIGNAL_GLASS -> EchoContent.SIGNAL_GLASS.get().defaultBlockState();
                case CONTROLLER -> EchoContent.GATE_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(GateControllerBlock.FACING, facing);
            };
            level.setBlock(position, blockState, Block.UPDATE_ALL);
        }
        for (GateLocalPos localPosition : GatePattern.interior(facing)) {
            level.setBlock(
                    localPosition.toWorld(controllerPosition, facing),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(controllerPosition) instanceof GateControllerBlockEntity controller)) {
            throw new IllegalStateException("Visual Gate controller did not initialize");
        }
        if (state == GateState.OPEN) {
            boolean opened = controller.applyActivation(new ActivationDecision(
                    ActivationCode.OPENED, level.getGameTime() + 100_000L));
            if (!opened) {
                throw new IllegalStateException("Visual Gate did not open");
            }
        } else if (state == GateState.FAULT) {
            CompoundTag tag = controller.saveWithoutMetadata(level.registryAccess());
            tag.putString("orientation", facing.getSerializedName());
            tag.putString("state", GateState.FAULT.name());
            tag.putLong("open_deadline", 0L);
            controller.loadAdditional(tag, level.registryAccess());
            BlockState blockState = controller.getBlockState();
            level.sendBlockUpdated(controllerPosition, blockState, blockState, Block.UPDATE_CLIENTS);
        }
    }

    private static void prepareItemViews(ServerLevel level) {
        ItemStack echo = new ItemStack(EchoContent.ECHO.get());
        ItemEntity dropped =
                new ItemEntity(level, 72.5, 102.2, 0.5, echo.copy(), 0.0, 0.0, 0.0);
        dropped.setNoGravity(true);
        level.addFreshEntity(dropped);

        BlockPos framePosition = new BlockPos(80, 103, 0);
        level.setBlock(framePosition.north(), EchoContent.RELAY_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        ItemFrame frame = new ItemFrame(level, framePosition, Direction.SOUTH);
        frame.setItem(echo.copy());
        if (!level.addFreshEntity(frame)) {
            throw new IllegalStateException("Visual ECHO item frame did not spawn");
        }
    }

    private static void writeReadyMarker() {
        String marker = System.getProperty("afterlight.visual.server.marker");
        if (marker == null || marker.isBlank()) {
            throw new IllegalStateException("Missing visual server marker property");
        }
        try {
            Path path = Path.of(marker).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, "VISUAL SERVER: READY\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write visual server marker", exception);
        }
    }
}
