package org.rllabs.afterlight.gate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateActivationService.ActivationCode;
import org.rllabs.afterlight.gate.GateActivationService.ActivationDecision;
import org.rllabs.afterlight.gate.GateActivationService.ActivationRequest;
import org.rllabs.afterlight.gate.GatePattern.GatePart;
import org.rllabs.afterlight.gate.GatePatternMatcher.MatchResult;

@GameTestHolder(Afterlight.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class GateControllerGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";
    private static final BlockPos CONTROLLER_POSITION = new BlockPos(4, 1, 4);
    private static final Direction FACING = Direction.NORTH;
    private static final GateProgressGateway ALL_PROOFS_COMPLETE = (player, taskId) -> true;

    private GateControllerGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 240)
    public static void futureSavedOpeningClosesAtOriginalDeadline(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ActivationDecision decision = successfulDecision(helper);
        helper.assertTrue(controller.applyActivation(decision), "accepted opening was not applied");
        assertAllFieldsOwned(helper, controller);

        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        GateControllerBlockEntity reloaded = reloadController(helper, saved);
        helper.assertValueEqual(GateState.OPEN, reloaded.state(), "reloaded state");
        helper.assertValueEqual(FACING, reloaded.orientation(), "reloaded orientation");
        helper.assertValueEqual(decision.openDeadline(), reloaded.openDeadline(), "reloaded deadline");
        helper.assertValueEqual(controller.fieldId(), reloaded.fieldId(), "reloaded field UUID");

        long ticksUntilClose = decision.openDeadline() - helper.getLevel().getGameTime();
        helper.runAfterDelay(ticksUntilClose - 1L, () -> {
            helper.assertValueEqual(GateState.OPEN, reloaded.state(), "state before original deadline");
            assertAllFieldsPresent(helper);
        });
        helper.runAfterDelay(ticksUntilClose, () -> {
            helper.assertValueEqual(GateState.IDLE, reloaded.state(), "state at original deadline");
            assertAllFieldsAbsent(helper);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void staleSavedOpeningClosesOnLoad(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ActivationDecision staleDecision = new ActivationDecision(
                ActivationCode.OPENED,
                helper.getLevel().getGameTime());
        helper.assertTrue(controller.applyActivation(staleDecision), "stale fixture opening was not applied");

        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertValueEqual(GateState.IDLE, reloaded.state(), "stale reload state");
        assertAllFieldsAbsent(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void mismatchedFieldUuidFaultsOnLoad(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockPos fieldPosition = fieldPosition();
        replaceFieldOwner(
                helper,
                fieldPosition,
                helper.absolutePos(CONTROLLER_POSITION),
                UUID.randomUUID());

        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertValueEqual(GateState.FAULT, reloaded.state(), "mismatched field reload state");
        helper.assertBlockPresent(EchoContent.GATE_FIELD.get(), fieldPosition);
        helper.runAfterDelay(2L, () -> {
            helper.assertBlockNotPresent(EchoContent.GATE_FIELD.get(), fieldPosition);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void foreignControllerPositionSurvivesControllerClose(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        BlockPos fieldPosition = fieldPosition();
        replaceFieldOwner(
                helper,
                fieldPosition,
                helper.absolutePos(CONTROLLER_POSITION.offset(16, 0, 0)),
                controller.fieldId());

        controller.close();

        helper.assertBlockPresent(EchoContent.GATE_FIELD.get(), fieldPosition);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void malformedFrameFaultsOnLoad(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.setBlock(new GateLocalPos(-3, 4).toWorld(CONTROLLER_POSITION, FACING), Blocks.AIR);

        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertValueEqual(GateState.FAULT, reloaded.state(), "malformed frame reload state");
        assertAllFieldsAbsent(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void missingControllerFaultsSavedOpening(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        BlockState controllerState = controller.getBlockState();
        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.setBlock(CONTROLLER_POSITION, Blocks.AIR);
        GateControllerBlockEntity detached = loadDetachedController(
                helper,
                controllerState,
                saved);

        detached.onLoad();

        helper.assertValueEqual(GateState.FAULT, detached.state(), "missing controller reload state");
        assertAllFieldsAbsent(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void coreStorageTransfersWithoutChangingTotalCount(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ItemStack source = new ItemStack(Items.DIAMOND, 3);

        helper.assertTrue(controller.insertCore(source), "core storage rejected transfer fixture");
        helper.assertValueEqual(2, source.getCount(), "source count after insertion");
        helper.assertValueEqual(1, controller.coreStack().getCount(), "stored count after insertion");

        ItemStack removed = controller.removeCore();

        helper.assertValueEqual(1, removed.getCount(), "removed count");
        helper.assertTrue(controller.coreStack().isEmpty(), "core storage was not emptied");
        helper.assertValueEqual(3, source.getCount() + removed.getCount(), "total count after removal");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void savedCoreStackReloadsWithItsOriginalCount(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ItemStack source = new ItemStack(Items.DIAMOND, 2);
        helper.assertTrue(controller.insertCore(source), "core storage rejected persistence fixture");
        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());

        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertTrue(reloaded.coreStack().is(Items.DIAMOND), "reloaded core item changed");
        helper.assertValueEqual(1, reloaded.coreStack().getCount(), "reloaded core count");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void savedFieldReloadsWithControllerPositionAndUuid(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        BlockPos relativePosition = fieldPosition();
        GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
        CompoundTag saved = field.saveWithFullMetadata(helper.getLevel().registryAccess());

        GateFieldBlockEntity reloaded = reloadField(helper, relativePosition, saved);

        helper.assertValueEqual(
                helper.absolutePos(CONTROLLER_POSITION),
                reloaded.ownerPosition(),
                "reloaded field owner position");
        helper.assertValueEqual(controller.fieldId(), reloaded.ownerId(), "reloaded field owner UUID");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void ftbGatewayReadsCurrentServerTeamData(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ActivationRequest request = new ActivationRequest(
                new MatchResult(List.of()),
                1,
                true,
                GateState.IDLE,
                helper.getLevel().getGameTime());

        ActivationDecision decision = new GateActivationService().activate(
                request,
                player,
                new FtbGateProgressGateway());

        helper.assertValueEqual(
                ActivationCode.ENERGY_PROOF_INCOMPLETE,
                decision.code(),
                "server FTB decision without completed task");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static GateControllerBlockEntity buildAndOpenGate(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        helper.assertTrue(
                controller.applyActivation(successfulDecision(helper)),
                "accepted opening was not applied");
        return controller;
    }

    private static GateControllerBlockEntity buildGate(GameTestHelper helper) {
        for (Map.Entry<GateLocalPos, GatePart> entry : GatePattern.expected(FACING).entrySet()) {
            BlockPos position = entry.getKey().toWorld(CONTROLLER_POSITION, FACING);
            if (entry.getValue() == GatePart.CONTROLLER) {
                helper.setBlock(
                        position,
                        EchoContent.GATE_CONTROLLER.get()
                                .defaultBlockState()
                                .setValue(GateControllerBlock.FACING, FACING));
            } else {
                helper.setBlock(position, blockFor(entry.getValue()));
            }
        }
        return helper.getBlockEntity(CONTROLLER_POSITION);
    }

    private static ActivationDecision successfulDecision(GameTestHelper helper) {
        return new GateActivationService().activate(
                new ActivationRequest(
                        new MatchResult(List.of()),
                        1,
                        true,
                        GateState.IDLE,
                        helper.getLevel().getGameTime()),
                null,
                ALL_PROOFS_COMPLETE);
    }

    private static GateControllerBlockEntity reloadController(
            GameTestHelper helper,
            CompoundTag saved) {
        BlockPos absolutePosition = helper.absolutePos(CONTROLLER_POSITION);
        BlockState state = helper.getLevel().getBlockState(absolutePosition);
        BlockEntity loaded = BlockEntity.loadStatic(
                absolutePosition,
                state,
                saved,
                helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded instanceof GateControllerBlockEntity,
                "saved controller did not load through its registered block entity type");
        helper.getLevel().removeBlockEntity(absolutePosition);
        helper.getLevel().setBlockEntity(loaded);
        GateControllerBlockEntity controller = (GateControllerBlockEntity) loaded;
        controller.onLoad();
        return controller;
    }

    private static GateControllerBlockEntity loadDetachedController(
            GameTestHelper helper,
            BlockState controllerState,
            CompoundTag saved) {
        BlockPos absolutePosition = helper.absolutePos(CONTROLLER_POSITION);
        BlockEntity loaded = BlockEntity.loadStatic(
                absolutePosition,
                controllerState,
                saved,
                helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded instanceof GateControllerBlockEntity,
                "saved controller did not load through its registered block entity type");
        loaded.setLevel(helper.getLevel());
        return (GateControllerBlockEntity) loaded;
    }

    private static GateFieldBlockEntity reloadField(
            GameTestHelper helper,
            BlockPos relativePosition,
            CompoundTag saved) {
        BlockPos absolutePosition = helper.absolutePos(relativePosition);
        BlockState state = helper.getLevel().getBlockState(absolutePosition);
        BlockEntity loaded = BlockEntity.loadStatic(
                absolutePosition,
                state,
                saved,
                helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded instanceof GateFieldBlockEntity,
                "saved field did not load through its registered block entity type");
        helper.getLevel().removeBlockEntity(absolutePosition);
        helper.getLevel().setBlockEntity(loaded);
        return (GateFieldBlockEntity) loaded;
    }

    private static void replaceFieldOwner(
            GameTestHelper helper,
            BlockPos relativePosition,
            BlockPos ownerPosition,
            UUID ownerId) {
        BlockPos absolutePosition = helper.absolutePos(relativePosition);
        GateFieldBlockEntity field = new GateFieldBlockEntity(
                absolutePosition,
                helper.getLevel().getBlockState(absolutePosition));
        field.initializeOwnership(ownerPosition, ownerId);
        helper.getLevel().removeBlockEntity(absolutePosition);
        helper.getLevel().setBlockEntity(field);
    }

    private static void assertAllFieldsOwned(
            GameTestHelper helper,
            GateControllerBlockEntity controller) {
        BlockPos ownerPosition = helper.absolutePos(CONTROLLER_POSITION);
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            BlockPos relativePosition = localPosition.toWorld(CONTROLLER_POSITION, FACING);
            helper.assertBlockPresent(EchoContent.GATE_FIELD.get(), relativePosition);
            GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
            helper.assertValueEqual(ownerPosition, field.ownerPosition(), "field owner position");
            helper.assertValueEqual(controller.fieldId(), field.ownerId(), "field owner UUID");
        }
    }

    private static void assertAllFieldsPresent(GameTestHelper helper) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            helper.assertBlockPresent(
                    EchoContent.GATE_FIELD.get(),
                    localPosition.toWorld(CONTROLLER_POSITION, FACING));
        }
    }

    private static void assertAllFieldsAbsent(GameTestHelper helper) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            helper.assertBlockNotPresent(
                    EchoContent.GATE_FIELD.get(),
                    localPosition.toWorld(CONTROLLER_POSITION, FACING));
        }
    }

    private static Block blockFor(GatePart part) {
        return part == GatePart.SIGNAL_GLASS
                ? EchoContent.SIGNAL_GLASS.get()
                : EchoContent.GATE_FRAME.get();
    }

    private static BlockPos fieldPosition() {
        return new GateLocalPos(0, 4).toWorld(CONTROLLER_POSITION, FACING);
    }
}
