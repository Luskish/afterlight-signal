package org.rllabs.afterlight.gate;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.rllabs.afterlight.EchoContent;

public final class GateFieldBlockEntity extends BlockEntity {
    private static final String OWNER_POSITION_TAG = "owner_position";
    private static final String OWNER_UUID_TAG = "owner_uuid";
    private static final String LINKED_TAG = "linked";

    private BlockPos ownerPosition;
    private UUID ownerId;
    private boolean linked;

    public GateFieldBlockEntity(BlockPos position, BlockState state) {
        super(EchoContent.GATE_FIELD_BLOCK_ENTITY.get(), position, state);
    }

    public void initializeOwnership(BlockPos controllerPosition, UUID fieldId) {
        Objects.requireNonNull(controllerPosition, "controllerPosition");
        Objects.requireNonNull(fieldId, "fieldId");
        if (ownerPosition != null || ownerId != null) {
            throw new IllegalStateException("Gate field ownership is already initialized");
        }
        linked = true;
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
                && Objects.equals(ownerId, fieldId)
                && linked;
    }

    boolean authorizesTravel(ServerLevel level, BlockPos position) {
        if (!linked) {
            removeField(level, position);
            return false;
        }
        if (ownerPosition == null
                || ownerId == null
                || !isPlausibleOwner(position, ownerPosition)) {
            removeField(level, position);
            return false;
        }
        if (!isChunkLoaded(level, ownerPosition)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(ownerPosition);
        if (blockEntity instanceof GateControllerBlockEntity controller
                && controller.authorizesFieldTravel(position, ownerId, level.getGameTime())) {
            return true;
        }
        removeField(level, position);
        return false;
    }

    static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            GateFieldBlockEntity field) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        field.authorizesTravel(serverLevel, position);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linked) {
            tag.putBoolean(LINKED_TAG, true);
        }
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
        linked = tag.contains(LINKED_TAG);
        ownerPosition = tag.contains(OWNER_POSITION_TAG)
                ? BlockPos.of(tag.getLong(OWNER_POSITION_TAG))
                : null;
        ownerId = tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
    }

    private static boolean isPlausibleOwner(BlockPos fieldPosition, BlockPos ownerPosition) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (GateLocalPos localPosition : GatePattern.interior(direction)) {
                if (localPosition.toWorld(ownerPosition, direction).equals(fieldPosition)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeField(ServerLevel level, BlockPos position) {
        level.setBlock(
                position,
                level.getFluidState(position).createLegacyBlock(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunk(
                        SectionPos.blockToSectionCoord(position.getX()),
                        SectionPos.blockToSectionCoord(position.getZ()),
                        ChunkStatus.FULL,
                        false)
                != null;
    }
}
