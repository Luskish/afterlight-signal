package org.rllabs.afterlight.gate;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.gate.GateActivationService.ActivationCode;
import org.rllabs.afterlight.gate.GateActivationService.ActivationDecision;
import org.rllabs.afterlight.gate.GateActivationService.ActivationRequest;
import org.rllabs.afterlight.gate.GatePatternMatcher.MatchResult;
import org.rllabs.afterlight.gate.GatePatternMatcher.MismatchKind;

public final class GateControllerBlockEntity extends BlockEntity {
    private static final String ORIENTATION_TAG = "orientation";
    private static final String CORE_TAG = "core";
    private static final String STATE_TAG = "state";
    private static final String DEADLINE_TAG = "open_deadline";
    private static final String FIELD_UUID_TAG = "field_uuid";
    private static final ResourceLocation GATE_CORE_ID =
            ResourceLocation.fromNamespaceAndPath("kubejs", "gate_of_return_core");
    private static final GateActivationService ACTIVATION_SERVICE = new GateActivationService();

    private Direction orientation;
    private ItemStack coreStack = ItemStack.EMPTY;
    private GateState state = GateState.IDLE;
    private long openDeadline;
    private UUID fieldId;
    private boolean persistentStateValid = true;

    public GateControllerBlockEntity(BlockPos position, BlockState state) {
        super(EchoContent.GATE_CONTROLLER_BLOCK_ENTITY.get(), position, state);
        orientation = facing(state);
    }

    public GateState state() {
        return state;
    }

    public Direction orientation() {
        return orientation;
    }

    public long openDeadline() {
        return openDeadline;
    }

    public UUID fieldId() {
        return fieldId;
    }

    public ItemStack coreStack() {
        return coreStack.copy();
    }

    boolean insertCore(ItemStack source) {
        if (state == GateState.OPEN || !coreStack.isEmpty() || source.isEmpty()) {
            return false;
        }
        coreStack = source.split(1);
        setChanged();
        return true;
    }

    ItemStack removeCore() {
        if (state == GateState.OPEN || coreStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = coreStack;
        coreStack = ItemStack.EMPTY;
        setChanged();
        return removed;
    }

    ItemStack extractCoreForRemoval() {
        if (coreStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = coreStack;
        coreStack = ItemStack.EMPTY;
        setChanged();
        return removed;
    }

    ActivationDecision activate(
            ServerPlayer player,
            GateProgressGateway progressGateway,
            boolean destinationAvailable) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new ActivationDecision(ActivationCode.DESTINATION_UNAVAILABLE, -1L);
        }
        MatchResult structure = GatePatternMatcher.match(
                serverLevel,
                worldPosition,
                facing(getBlockState()));
        int coreCount = isGateCore(coreStack) ? coreStack.getCount() : 0;
        ActivationDecision decision = ACTIVATION_SERVICE.activate(
                new ActivationRequest(
                        structure,
                        coreCount,
                        destinationAvailable,
                        state,
                        serverLevel.getGameTime()),
                player,
                progressGateway);
        if (decision.accepted() && !applyActivation(decision)) {
            return new ActivationDecision(ActivationCode.MALFORMED_STRUCTURE, -1L);
        }
        return decision;
    }

    boolean applyActivation(ActivationDecision decision) {
        if (!decision.accepted()
                || state == GateState.OPEN
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Direction currentFacing = facing(getBlockState());
        for (GateLocalPos localPosition : GatePattern.interior(currentFacing)) {
            BlockPos fieldPosition = localPosition.toWorld(worldPosition, currentFacing);
            if (!serverLevel.getBlockState(fieldPosition).canBeReplaced()) {
                return false;
            }
        }

        orientation = currentFacing;
        state = GateState.OPEN;
        openDeadline = decision.openDeadline();
        fieldId = UUID.randomUUID();
        for (GateLocalPos localPosition : GatePattern.interior(orientation)) {
            BlockPos fieldPosition = localPosition.toWorld(worldPosition, orientation);
            if (!serverLevel.setBlock(
                    fieldPosition,
                    EchoContent.GATE_FIELD.get().defaultBlockState(),
                    Block.UPDATE_ALL)) {
                faultAndClose();
                return false;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(fieldPosition);
            if (!(blockEntity instanceof GateFieldBlockEntity field)) {
                faultAndClose();
                return false;
            }
            field.initializeOwnership(worldPosition, fieldId);
        }
        setChanged();
        return true;
    }

    public void close() {
        removeOwnedFields();
        state = GateState.IDLE;
        openDeadline = 0L;
        fieldId = null;
        setChanged();
    }

    boolean ownsField(BlockPos position, UUID candidateFieldId) {
        if (state != GateState.OPEN
                || fieldId == null
                || !fieldId.equals(candidateFieldId)
                || orientation == null) {
            return false;
        }
        for (GateLocalPos localPosition : GatePattern.interior(orientation)) {
            if (localPosition.toWorld(worldPosition, orientation).equals(position)) {
                return true;
            }
        }
        return false;
    }

    static void serverTick(
            Level level,
            BlockPos position,
            BlockState blockState,
            GateControllerBlockEntity controller) {
        if (!(level instanceof ServerLevel serverLevel)
                || controller.state != GateState.OPEN) {
            return;
        }
        if (!ACTIVATION_SERVICE.shouldResumeOpen(
                controller.state,
                controller.openDeadline,
                serverLevel.getGameTime())) {
            controller.close();
        } else if (controller.requiredGateChunksLoaded() && !controller.validOpenState()) {
            controller.faultAndClose();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel) || state != GateState.OPEN) {
            return;
        }
        if (!ACTIVATION_SERVICE.shouldResumeOpen(state, openDeadline, serverLevel.getGameTime())) {
            close();
        } else if (requiredGateChunksLoaded() && !validOpenState()) {
            faultAndClose();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(ORIENTATION_TAG, orientation.getSerializedName());
        if (!coreStack.isEmpty()) {
            tag.put(CORE_TAG, coreStack.save(registries));
        }
        tag.putString(STATE_TAG, state.name());
        tag.putLong(DEADLINE_TAG, openDeadline);
        if (fieldId != null) {
            tag.putUUID(FIELD_UUID_TAG, fieldId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        persistentStateValid = true;
        Direction savedOrientation = Direction.byName(tag.getString(ORIENTATION_TAG));
        if (savedOrientation == null || !savedOrientation.getAxis().isHorizontal()) {
            persistentStateValid = false;
            orientation = facing(getBlockState());
        } else {
            orientation = savedOrientation;
        }
        coreStack = tag.contains(CORE_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(CORE_TAG))
                : ItemStack.EMPTY;
        try {
            state = GateState.valueOf(tag.getString(STATE_TAG));
        } catch (IllegalArgumentException exception) {
            persistentStateValid = false;
            state = GateState.FAULT;
        }
        openDeadline = tag.getLong(DEADLINE_TAG);
        fieldId = tag.hasUUID(FIELD_UUID_TAG) ? tag.getUUID(FIELD_UUID_TAG) : null;
    }

    private boolean validOpenState() {
        if (!(level instanceof ServerLevel serverLevel)
                || !persistentStateValid
                || orientation == null
                || fieldId == null
                || !getBlockState().is(EchoContent.GATE_CONTROLLER.get())
                || facing(getBlockState()) != orientation) {
            return false;
        }
        MatchResult structure = GatePatternMatcher.match(serverLevel, worldPosition, orientation);
        for (GatePatternMatcher.Mismatch mismatch : structure.mismatches()) {
            if (mismatch.kind() != MismatchKind.INTERIOR_BLOCKED
                    || !isOwnedField(mismatch.worldPosition())) {
                return false;
            }
        }
        for (GateLocalPos localPosition : GatePattern.interior(orientation)) {
            if (!isOwnedField(localPosition.toWorld(worldPosition, orientation))) {
                return false;
            }
        }
        return true;
    }

    private boolean isOwnedField(BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)
                || !isChunkLoaded(serverLevel, position)
                || !serverLevel.getBlockState(position).is(EchoContent.GATE_FIELD.get())) {
            return false;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(position);
        return blockEntity instanceof GateFieldBlockEntity field
                && field.isOwnedBy(worldPosition, fieldId);
    }

    private void faultAndClose() {
        removeOwnedFields();
        state = GateState.FAULT;
        openDeadline = 0L;
        fieldId = null;
        setChanged();
    }

    private void removeOwnedFields() {
        if (!(level instanceof ServerLevel serverLevel)
                || fieldId == null
                || orientation == null) {
            return;
        }
        for (GateLocalPos localPosition : GatePattern.interior(orientation)) {
            BlockPos fieldPosition = localPosition.toWorld(worldPosition, orientation);
            if (!isChunkLoaded(serverLevel, fieldPosition)) {
                continue;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(fieldPosition);
            if (serverLevel.getBlockState(fieldPosition).is(EchoContent.GATE_FIELD.get())
                    && blockEntity instanceof GateFieldBlockEntity field
                    && field.isOwnedBy(worldPosition, fieldId)) {
                removeField(serverLevel, fieldPosition);
            }
        }
    }

    private static void removeField(ServerLevel level, BlockPos position) {
        level.setBlock(
                position,
                level.getFluidState(position).createLegacyBlock(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private boolean requiredGateChunksLoaded() {
        if (!(level instanceof ServerLevel serverLevel) || orientation == null) {
            return false;
        }
        for (GateLocalPos localPosition : GatePattern.expected(orientation).keySet()) {
            if (!isChunkLoaded(serverLevel, localPosition.toWorld(worldPosition, orientation))) {
                return false;
            }
        }
        for (GateLocalPos localPosition : GatePattern.interior(orientation)) {
            if (!isChunkLoaded(serverLevel, localPosition.toWorld(worldPosition, orientation))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunk(
                        SectionPos.blockToSectionCoord(position.getX()),
                        SectionPos.blockToSectionCoord(position.getZ()),
                        ChunkStatus.FULL,
                        false)
                != null;
    }

    static boolean isGateCore(ItemStack stack) {
        return !stack.isEmpty()
                && GATE_CORE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static Direction facing(BlockState state) {
        if (state.hasProperty(GateControllerBlock.FACING)) {
            return state.getValue(GateControllerBlock.FACING);
        }
        return Direction.NORTH;
    }
}
