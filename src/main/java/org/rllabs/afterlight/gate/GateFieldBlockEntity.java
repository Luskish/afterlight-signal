package org.rllabs.afterlight.gate;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.rllabs.afterlight.EchoContent;

public final class GateFieldBlockEntity extends BlockEntity {
    private static final String OWNER_POSITION_TAG = "owner_position";
    private static final String OWNER_UUID_TAG = "owner_uuid";

    private BlockPos ownerPosition;
    private UUID ownerId;

    public GateFieldBlockEntity(BlockPos position, BlockState state) {
        super(EchoContent.GATE_FIELD_BLOCK_ENTITY.get(), position, state);
    }

    public void initializeOwnership(BlockPos controllerPosition, UUID fieldId) {
        Objects.requireNonNull(controllerPosition, "controllerPosition");
        Objects.requireNonNull(fieldId, "fieldId");
        if (ownerPosition != null || ownerId != null) {
            throw new IllegalStateException("Gate field ownership is already initialized");
        }
        ownerPosition = controllerPosition.immutable();
        ownerId = fieldId;
        setChanged();
    }

    public BlockPos ownerPosition() {
        return ownerPosition;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public boolean isOwnedBy(BlockPos controllerPosition, UUID fieldId) {
        return Objects.equals(ownerPosition, controllerPosition)
                && Objects.equals(ownerId, fieldId);
    }

    static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            GateFieldBlockEntity field) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (field.ownerPosition == null && field.ownerId == null) {
            return;
        }
        if (field.ownerPosition == null || field.ownerId == null) {
            serverLevel.removeBlock(position, false);
            return;
        }
        if (!serverLevel.isLoaded(field.ownerPosition)) {
            return;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(field.ownerPosition);
        if (!(blockEntity instanceof GateControllerBlockEntity controller)
                || !controller.ownsField(position, field.ownerId)) {
            serverLevel.removeBlock(position, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerPosition != null) {
            tag.putLong(OWNER_POSITION_TAG, ownerPosition.asLong());
        }
        if (ownerId != null) {
            tag.putUUID(OWNER_UUID_TAG, ownerId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerPosition = tag.contains(OWNER_POSITION_TAG)
                ? BlockPos.of(tag.getLong(OWNER_POSITION_TAG))
                : null;
        ownerId = tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
    }
}
