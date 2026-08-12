package org.rllabs.afterlight.gate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
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

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void futureSavedOpeningWithoutCoreFaultsOnLoad(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ActivationDecision decision = successfulDecision(helper);
        helper.assertTrue(controller.applyActivation(decision), "accepted opening was not applied");
        assertAllFieldsOwned(helper, controller);

        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());
        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertValueEqual(GateState.FAULT, reloaded.state(), "missing-core reload state");
        helper.assertValueEqual(0L, reloaded.openDeadline(), "missing-core reload deadline");
        helper.assertTrue(reloaded.fieldId() == null, "missing-core reload retained a field UUID");
        helper.assertTrue(reloaded.coreStack().isEmpty(), "missing-core reload gained an item");
        assertAllFieldsAbsent(helper);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            timeoutTicks = 80,
            batch = "afterlight_gate_cross_chunk_defer")
    public static void futureCrossChunkRecoveryDefersWithoutLoadingMissingChunk(GameTestHelper helper) {
        CrossChunkReloadFixture fixture = prepareCrossChunkReload(helper, 8);
        GateControllerBlockEntity reloaded = loadControllerChunk(helper, fixture);
        helper.assertValueEqual(reloaded.state(), GateState.OPEN, "cross-chunk reloaded state");
        helper.assertValueEqual(
                reloaded.openDeadline(),
                fixture.decision().openDeadline(),
                "cross-chunk reloaded deadline");
        helper.assertTrue(
                !isFullChunkAvailable(helper, fixture.deferredChunk()),
                "controller recovery loaded the deferred Gate chunk");
        reloaded.close();
        helper.assertTrue(
                !isFullChunkAvailable(helper, fixture.deferredChunk()),
                "controller cleanup loaded the deferred Gate chunk");
        releaseControllerChunk(helper, fixture);
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            timeoutTicks = 240,
            batch = "afterlight_gate_cross_chunk_deadline")
    public static void futureCrossChunkReloadFaultsWhenMissingCoreBecomesAvailable(
            GameTestHelper helper) {
        CrossChunkReloadFixture fixture = prepareCrossChunkReload(helper, 24);
        GateControllerBlockEntity reloaded = loadControllerChunk(helper, fixture);
        helper.assertValueEqual(reloaded.state(), GateState.OPEN, "cross-chunk reloaded state");
        helper.assertTrue(
                !isFullChunkAvailable(helper, fixture.deferredChunk()),
                "controller recovery loaded the deferred Gate chunk");

        helper.getLevel().setChunkForced(
                fixture.deferredChunk().x,
                fixture.deferredChunk().z,
                true);
        helper.getLevel().getChunk(
                fixture.deferredChunk().x,
                fixture.deferredChunk().z,
                ChunkStatus.FULL,
                true);
        restoreGateChunk(helper, fixture, fixture.deferredChunk());
        assertAllFieldsOwnedAtAbsolutePosition(helper, fixture.absoluteController(), reloaded);

        helper.runAfterDelay(2L, () -> {
            helper.assertValueEqual(
                    reloaded.state(),
                    GateState.FAULT,
                    "cross-chunk missing-core recovery state");
            assertAllFieldsAbsentAtAbsolutePosition(helper, fixture.absoluteController());
            releaseControllerChunk(helper, fixture);
            helper.getLevel().setChunkForced(
                    fixture.deferredChunk().x,
                    fixture.deferredChunk().z,
                    false);
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
    public static void breakingControllerDropsExactlyOneStoredCore(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        Component customName = Component.literal("Preserved Gate Core");
        ItemStack source = new ItemStack(Items.DIAMOND);
        source.set(DataComponents.CUSTOM_NAME, customName);
        helper.assertTrue(controller.insertCore(source), "core storage rejected destruction fixture");

        helper.destroyBlock(CONTROLLER_POSITION);

        BlockPos absolutePosition = helper.absolutePos(CONTROLLER_POSITION);
        List<ItemEntity> drops = helper.getLevel().getEntities(
                EntityType.ITEM,
                new AABB(absolutePosition).inflate(2.0),
                ItemEntity::isAlive);
        int survivingCount = drops.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.DIAMOND))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertTrue(
                survivingCount == 1,
                "expected exactly one stored core after destruction, found " + survivingCount);
        ItemStack survivingCore = drops.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.DIAMOND))
                .findFirst()
                .orElse(ItemStack.EMPTY);
        helper.assertValueEqual(
                survivingCore.get(DataComponents.CUSTOM_NAME),
                customName,
                "stored core components after controller destruction");
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
    public static void unexpectedSavedOpenItemFaultsAndRemainsRecoverable(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildGate(helper);
        ItemStack unexpected = new ItemStack(Items.DIAMOND);
        helper.assertTrue(controller.insertCore(unexpected), "unexpected item was not stored");
        helper.assertTrue(
                controller.applyActivation(successfulDecision(helper)),
                "unexpected-item fixture opening was not applied");
        CompoundTag saved = controller.saveWithFullMetadata(helper.getLevel().registryAccess());

        GateControllerBlockEntity reloaded = reloadController(helper, saved);

        helper.assertValueEqual(GateState.FAULT, reloaded.state(), "unexpected-item reload state");
        helper.assertTrue(reloaded.coreStack().is(Items.DIAMOND), "unexpected item changed on reload");
        helper.assertValueEqual(1, reloaded.coreStack().getCount(), "unexpected item count");
        ItemStack recovered = reloaded.removeCore();
        helper.assertTrue(recovered.is(Items.DIAMOND), "unexpected item was not recoverable");
        helper.assertValueEqual(1, recovered.getCount(), "recovered unexpected item count");
        helper.assertTrue(reloaded.coreStack().isEmpty(), "recovered item remained stored");
        assertAllFieldsAbsent(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void savedFieldReloadsWithControllerPositionAndUuid(GameTestHelper helper) {
        GateControllerBlockEntity controller = buildAndOpenGate(helper);
        BlockPos relativePosition = fieldPosition();
        GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
        CompoundTag saved = field.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.contains("linked"), "linked field marker was not persisted");

        GateFieldBlockEntity reloaded = reloadField(helper, relativePosition, saved);

        helper.assertValueEqual(
                helper.absolutePos(CONTROLLER_POSITION),
                reloaded.ownerPosition(),
                "reloaded field owner position");
        helper.assertValueEqual(controller.fieldId(), reloaded.ownerId(), "reloaded field owner UUID");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void linkedFieldMissingBothOwnerTagsRemovesItself(GameTestHelper helper) {
        buildAndOpenGate(helper);
        BlockPos relativePosition = fieldPosition();
        GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
        CompoundTag saved = field.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.putBoolean("linked", true);
        saved.remove("owner_position");
        saved.remove("owner_uuid");

        reloadField(helper, relativePosition, saved);

        helper.runAfterDelay(2L, () -> {
            helper.assertBlockNotPresent(EchoContent.GATE_FIELD.get(), relativePosition);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void implausibleUnloadedOwnerRemovesFieldWithoutLoadingChunk(GameTestHelper helper) {
        buildAndOpenGate(helper);
        BlockPos relativePosition = fieldPosition();
        BlockPos farOwner = helper.absolutePos(relativePosition).offset(1_000_000, 0, 1_000_000);
        ChunkPos farOwnerChunk = new ChunkPos(farOwner);
        helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(farOwnerChunk.x, farOwnerChunk.z) == null,
                "far owner chunk was unexpectedly loaded before recovery");
        GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
        CompoundTag saved = field.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.putBoolean("linked", true);
        saved.putLong("owner_position", farOwner.asLong());
        saved.putUUID("owner_uuid", UUID.randomUUID());

        reloadField(helper, relativePosition, saved);

        helper.runAfterDelay(2L, () -> {
            helper.assertTrue(
                    helper.getLevel().getChunkSource().getChunkNow(farOwnerChunk.x, farOwnerChunk.z) == null,
                    "field recovery loaded the implausible owner chunk");
            helper.assertBlockNotPresent(EchoContent.GATE_FIELD.get(), relativePosition);
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = TEMPLATE,
            timeoutTicks = 1600,
            batch = "afterlight_gate_cross_chunk_expired_collision")
    public static void expiredCrossChunkReloadDeniesTravelBeforeFieldTick(GameTestHelper helper) {
        CrossChunkReloadFixture fixture = prepareCrossChunkReload(helper, 40);
        BlockPos staleField = new GateLocalPos(2, 4)
                .toWorld(fixture.absoluteController(), FACING);
        helper.assertValueEqual(
                fixture.deferredChunk(), new ChunkPos(staleField), "stale field chunk");
        long delay = fixture.decision().openDeadline()
                - helper.getLevel().getGameTime()
                + 1L;

        helper.runAfterDelay(delay, () -> {
            helper.getLevel()
                    .getChunkSource()
                    .addRegionTicket(
                            TicketType.PORTAL,
                            fixture.deferredChunk(),
                            0,
                            staleField);
            helper.getLevel().getChunk(
                    fixture.deferredChunk().x,
                    fixture.deferredChunk().z,
                    ChunkStatus.FULL,
                    true);
            restoreGateChunk(helper, fixture, fixture.deferredChunk());
            helper.assertTrue(
                    !isFullChunkAvailable(helper, fixture.controllerChunk()),
                    "stale field reload force-loaded its owner chunk");
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.moveTo(staleField.getBottomCenter(), 0.0F, 0.0F);

            try {
                helper.getLevel()
                        .getBlockState(staleField)
                        .entityInside(helper.getLevel(), staleField, player);

                helper.assertValueEqual(
                        helper.getLevel(), player.serverLevel(), "stale field collision level");
                helper.assertTrue(
                        player.getExistingData(EchoContent.GATE_RETURN_TARGET).isEmpty(),
                        "stale field collision stored a return route");
                GateTravelService.TravelResult probe = GateTravelService.INSTANCE.travelToFarRelay(
                        player,
                        fixture.absoluteController(),
                        helper.getLevel());
                helper.assertValueEqual(
                        GateTravelService.TravelResult.SUCCESS,
                        probe,
                        "stale field collision authorization probe");
                helper.assertTrue(
                        !isFullChunkAvailable(helper, fixture.controllerChunk()),
                        "stale field collision force-loaded its owner chunk");
                helper.assertTrue(
                        helper.getLevel().getBlockState(staleField).is(EchoContent.GATE_FIELD.get()),
                        "stale field was removed while its owner chunk was unavailable");
            } finally {
                player.removeData(EchoContent.GATE_RETURN_TARGET);
                helper.getLevel().getServer().getPlayerList().remove(player);
                clearGateChunk(helper, fixture, fixture.deferredChunk());
                helper.getLevel()
                        .getChunkSource()
                        .removeRegionTicket(
                                TicketType.PORTAL,
                                fixture.deferredChunk(),
                                0,
                                staleField);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 40)
    public static void fieldWithoutLinkMarkerRemovesItself(GameTestHelper helper) {
        BlockPos relativePosition = fieldPosition();
        helper.setBlock(relativePosition, EchoContent.GATE_FIELD.get());
        GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
        CompoundTag saved = field.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.remove("linked");

        reloadField(helper, relativePosition, saved);

        helper.runAfterDelay(2L, () -> {
            helper.assertBlockNotPresent(EchoContent.GATE_FIELD.get(), relativePosition);
            helper.succeed();
        });
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
        return buildGate(helper, CONTROLLER_POSITION);
    }

    private static GateControllerBlockEntity buildGate(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (Map.Entry<GateLocalPos, GatePart> entry : GatePattern.expected(FACING).entrySet()) {
            BlockPos position = entry.getKey().toWorld(controllerPosition, FACING);
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
        return helper.getBlockEntity(controllerPosition);
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
        return reloadController(helper, CONTROLLER_POSITION, saved);
    }

    private static GateControllerBlockEntity reloadController(
            GameTestHelper helper,
            BlockPos controllerPosition,
            CompoundTag saved) {
        BlockPos absolutePosition = helper.absolutePos(controllerPosition);
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
        assertAllFieldsOwned(helper, CONTROLLER_POSITION, controller);
    }

    private static void assertAllFieldsOwned(
            GameTestHelper helper,
            BlockPos controllerPosition,
            GateControllerBlockEntity controller) {
        BlockPos ownerPosition = helper.absolutePos(controllerPosition);
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            BlockPos relativePosition = localPosition.toWorld(controllerPosition, FACING);
            helper.assertBlockPresent(EchoContent.GATE_FIELD.get(), relativePosition);
            GateFieldBlockEntity field = helper.getBlockEntity(relativePosition);
            helper.assertValueEqual(ownerPosition, field.ownerPosition(), "field owner position");
            helper.assertValueEqual(controller.fieldId(), field.ownerId(), "field owner UUID");
        }
    }

    private static void assertAllFieldsPresent(GameTestHelper helper) {
        assertAllFieldsPresent(helper, CONTROLLER_POSITION);
    }

    private static void assertAllFieldsPresent(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            helper.assertBlockPresent(
                    EchoContent.GATE_FIELD.get(),
                    localPosition.toWorld(controllerPosition, FACING));
        }
    }

    private static void assertAllFieldsAbsent(GameTestHelper helper) {
        assertAllFieldsAbsent(helper, CONTROLLER_POSITION);
    }

    private static void assertAllFieldsAbsent(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            helper.assertBlockNotPresent(
                    EchoContent.GATE_FIELD.get(),
                    localPosition.toWorld(controllerPosition, FACING));
        }
    }

    private static GateControllerBlockEntity buildGateAtAbsolutePosition(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (Map.Entry<GateLocalPos, GatePart> entry : GatePattern.expected(FACING).entrySet()) {
            BlockPos position = entry.getKey().toWorld(controllerPosition, FACING);
            BlockState state = entry.getValue() == GatePart.CONTROLLER
                    ? EchoContent.GATE_CONTROLLER.get()
                            .defaultBlockState()
                            .setValue(GateControllerBlock.FACING, FACING)
                    : blockFor(entry.getValue()).defaultBlockState();
            helper.getLevel().setBlock(position, state, Block.UPDATE_ALL);
        }
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(controllerPosition);
        helper.assertTrue(
                blockEntity instanceof GateControllerBlockEntity,
                "absolute Gate controller block entity was not created");
        return (GateControllerBlockEntity) blockEntity;
    }

    private static void assertAllFieldsOwnedAtAbsolutePosition(
            GameTestHelper helper,
            BlockPos controllerPosition,
            GateControllerBlockEntity controller) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            BlockPos fieldPosition = localPosition.toWorld(controllerPosition, FACING);
            helper.assertTrue(
                    helper.getLevel().getBlockState(fieldPosition).is(EchoContent.GATE_FIELD.get()),
                    "missing Gate field at " + fieldPosition.toShortString());
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(fieldPosition);
            helper.assertTrue(
                    blockEntity instanceof GateFieldBlockEntity,
                    "missing Gate field block entity at " + fieldPosition.toShortString());
            GateFieldBlockEntity field = (GateFieldBlockEntity) blockEntity;
            helper.assertValueEqual(field.ownerPosition(), controllerPosition, "field owner position");
            helper.assertValueEqual(field.ownerId(), controller.fieldId(), "field owner UUID");
        }
    }

    private static void assertAllFieldsPresentAtAbsolutePosition(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            BlockPos fieldPosition = localPosition.toWorld(controllerPosition, FACING);
            helper.assertTrue(
                    helper.getLevel().getBlockState(fieldPosition).is(EchoContent.GATE_FIELD.get()),
                    "missing Gate field at " + fieldPosition.toShortString());
        }
    }

    private static void assertAllFieldsAbsentAtAbsolutePosition(
            GameTestHelper helper,
            BlockPos controllerPosition) {
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            BlockPos fieldPosition = localPosition.toWorld(controllerPosition, FACING);
            helper.assertTrue(
                    !helper.getLevel().getBlockState(fieldPosition).is(EchoContent.GATE_FIELD.get()),
                    "Gate field remained at " + fieldPosition.toShortString());
        }
    }

    private static CrossChunkReloadFixture prepareCrossChunkReload(
            GameTestHelper helper,
            int chunkOffset) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int controllerChunkX = Math.floorDiv(origin.getX(), 16) + chunkOffset;
        int controllerChunkZ = Math.floorDiv(origin.getZ(), 16) + chunkOffset;
        BlockPos absoluteController = new BlockPos(
                controllerChunkX * 16 + 15,
                origin.getY() + 1,
                controllerChunkZ * 16 + 8);
        ChunkPos controllerChunk = new ChunkPos(absoluteController);
        ChunkPos deferredChunk = new ChunkPos(absoluteController.east(2));
        helper.assertTrue(
                !controllerChunk.equals(deferredChunk),
                "cross-chunk fixture did not cross a chunk boundary");
        helper.getLevel().setChunkForced(controllerChunk.x, controllerChunk.z, true);
        helper.getLevel().setChunkForced(deferredChunk.x, deferredChunk.z, true);
        GateControllerBlockEntity controller = buildGateAtAbsolutePosition(helper, absoluteController);
        ActivationDecision decision = successfulDecision(helper);
        helper.assertTrue(controller.applyActivation(decision), "cross-chunk opening was not applied");
        assertAllFieldsOwnedAtAbsolutePosition(helper, absoluteController, controller);
        Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> savedBlockEntities = new LinkedHashMap<>();
        for (GateLocalPos localPosition : GatePattern.expected(FACING).keySet()) {
            captureGatePosition(
                    helper,
                    localPosition.toWorld(absoluteController, FACING),
                    savedBlocks,
                    savedBlockEntities);
        }
        for (GateLocalPos localPosition : GatePattern.interior(FACING)) {
            captureGatePosition(
                    helper,
                    localPosition.toWorld(absoluteController, FACING),
                    savedBlocks,
                    savedBlockEntities);
        }
        savedBlocks.put(absoluteController, controller.getBlockState());
        savedBlockEntities.put(
                absoluteController,
                controller.saveWithFullMetadata(helper.getLevel().registryAccess()));
        for (BlockPos absolutePosition : savedBlocks.keySet()) {
            helper.getLevel().removeBlockEntity(absolutePosition);
        }
        for (BlockPos absolutePosition : savedBlocks.keySet()) {
            helper.getLevel().setBlock(absolutePosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.getLevel().getChunkSource().save(true);
        helper.getLevel().setChunkForced(controllerChunk.x, controllerChunk.z, false);
        helper.getLevel().setChunkForced(deferredChunk.x, deferredChunk.z, false);
        for (int attempt = 0; attempt < 4 && !gateChunksAreUnloaded(
                helper,
                controllerChunk,
                deferredChunk); attempt++) {
            helper.getLevel().getChunkSource().tick(() -> true, false);
            while (helper.getLevel().getChunkSource().pollTask()) {
            }
        }
        helper.assertTrue(
                gateChunksAreUnloaded(helper, controllerChunk, deferredChunk),
                "cross-chunk fixture did not unload both Gate chunks");
        return new CrossChunkReloadFixture(
                absoluteController,
                controllerChunk,
                deferredChunk,
                savedBlocks,
                savedBlockEntities,
                decision);
    }

    private static void captureGatePosition(
            GameTestHelper helper,
            BlockPos absolutePosition,
            Map<BlockPos, BlockState> savedBlocks,
            Map<BlockPos, CompoundTag> savedBlockEntities) {
        savedBlocks.put(absolutePosition, helper.getLevel().getBlockState(absolutePosition));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePosition);
        if (blockEntity != null) {
            savedBlockEntities.put(
                    absolutePosition,
                    blockEntity.saveWithFullMetadata(helper.getLevel().registryAccess()));
        }
    }

    private static boolean gateChunksAreUnloaded(
            GameTestHelper helper,
            CrossChunkReloadFixture fixture) {
        return gateChunksAreUnloaded(
                helper,
                fixture.controllerChunk(),
                fixture.deferredChunk());
    }

    private static boolean gateChunksAreUnloaded(
            GameTestHelper helper,
            ChunkPos controllerChunk,
            ChunkPos deferredChunk) {
        return helper.getLevel()
                                .getChunkSource()
                                .getChunkNow(controllerChunk.x, controllerChunk.z)
                        == null
                && helper.getLevel()
                                .getChunkSource()
                                .getChunkNow(deferredChunk.x, deferredChunk.z)
                        == null;
    }

    private static GateControllerBlockEntity loadControllerChunk(
            GameTestHelper helper,
            CrossChunkReloadFixture fixture) {
        helper.getLevel()
                .getChunkSource()
                .addRegionTicket(
                        TicketType.PORTAL,
                        fixture.controllerChunk(),
                        0,
                        fixture.absoluteController());
        helper.getLevel().getChunk(
                fixture.controllerChunk().x,
                fixture.controllerChunk().z,
                ChunkStatus.FULL,
                true);
        helper.assertTrue(
                !isFullChunkAvailable(helper, fixture.deferredChunk()),
                "controller-only chunk ticket made the deferred Gate chunk FULL");
        restoreGateChunk(helper, fixture, fixture.controllerChunk());
        helper.assertTrue(
                !isFullChunkAvailable(helper, fixture.deferredChunk()),
                "controller chunk restoration made the deferred Gate chunk FULL");
        MatchResult recoveryMatch = GatePatternMatcher.match(
                helper.getLevel(),
                fixture.absoluteController(),
                FACING);
        helper.assertTrue(
                recoveryMatch.mismatches().stream()
                        .anyMatch(mismatch -> mismatch.kind()
                                == GatePatternMatcher.MismatchKind.UNLOADED_CHUNK),
                "cross-chunk recovery fixture had no unloaded Gate positions");
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(fixture.absoluteController());
        helper.assertTrue(
                blockEntity instanceof GateControllerBlockEntity,
                "persisted controller did not reload with its chunk");
        GateControllerBlockEntity controller = (GateControllerBlockEntity) blockEntity;
        controller.onLoad();
        return controller;
    }

    private static void restoreGateChunk(
            GameTestHelper helper,
            CrossChunkReloadFixture fixture,
            ChunkPos chunk) {
        fixture.savedBlocks().forEach((absolutePosition, state) -> {
            if (new ChunkPos(absolutePosition).equals(chunk)) {
                helper.getLevel().setBlock(
                        absolutePosition,
                        state,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        });
        fixture.savedBlockEntities().forEach((absolutePosition, saved) -> {
            if (!new ChunkPos(absolutePosition).equals(chunk)) {
                return;
            }
            BlockState state = helper.getLevel().getBlockState(absolutePosition);
            BlockEntity loaded = BlockEntity.loadStatic(
                    absolutePosition,
                    state,
                    saved,
                    helper.getLevel().registryAccess());
            helper.assertTrue(loaded != null, "saved Gate block entity did not reload");
            helper.getLevel().removeBlockEntity(absolutePosition);
            helper.getLevel().setBlockEntity(loaded);
            helper.assertTrue(
                    helper.getLevel().getBlockEntity(absolutePosition) == loaded,
                    "restored Gate block entity was not installed at " + absolutePosition.toShortString());
        });
    }

    private static void clearGateChunk(
            GameTestHelper helper,
            CrossChunkReloadFixture fixture,
            ChunkPos chunk) {
        fixture.savedBlocks().keySet().stream()
                .filter(position -> new ChunkPos(position).equals(chunk))
                .forEach(position -> {
                    helper.getLevel().removeBlockEntity(position);
                    helper.getLevel().setBlock(
                            position,
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                });
    }

    private static void releaseControllerChunk(
            GameTestHelper helper,
            CrossChunkReloadFixture fixture) {
        helper.getLevel()
                .getChunkSource()
                .removeRegionTicket(
                        TicketType.PORTAL,
                        fixture.controllerChunk(),
                        0,
                        fixture.absoluteController());
    }

    private static boolean isFullChunkAvailable(GameTestHelper helper, ChunkPos chunk) {
        return helper.getLevel().getChunkSource().getChunk(
                        chunk.x,
                        chunk.z,
                        ChunkStatus.FULL,
                        false)
                != null;
    }

    private static Block blockFor(GatePart part) {
        return part == GatePart.SIGNAL_GLASS
                ? EchoContent.SIGNAL_GLASS.get()
                : EchoContent.GATE_FRAME.get();
    }

    private static BlockPos fieldPosition() {
        return new GateLocalPos(0, 4).toWorld(CONTROLLER_POSITION, FACING);
    }

    private record CrossChunkReloadFixture(
            BlockPos absoluteController,
            ChunkPos controllerChunk,
            ChunkPos deferredChunk,
            Map<BlockPos, BlockState> savedBlocks,
            Map<BlockPos, CompoundTag> savedBlockEntities,
            ActivationDecision decision) {}
}
