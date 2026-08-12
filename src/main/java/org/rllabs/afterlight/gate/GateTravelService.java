package org.rllabs.afterlight.gate;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.relay.FarRelayInitializer;
import org.rllabs.afterlight.relay.FarRelayKeys;

public final class GateTravelService {
    public static final GateTravelService INSTANCE = new GateTravelService();
    public static final int SEARCH_RADIUS = 5;
    public static final int VERTICAL_RANGE = 6;

    private static final long COLLISION_RATE_LIMIT_TICKS = 20L;
    private static final String LAST_COLLISION_TICK = "afterlight_gate_collision_tick";

    public enum TravelResult {
        SUCCESS,
        RATE_LIMITED,
        ACTIVE_RETURN_TARGET,
        DESTINATION_UNAVAILABLE,
        NO_SAFE_DESTINATION,
        TRANSFER_FAILED
    }

    public TravelResult travelToFarRelay(
            ServerPlayer player, BlockPos sourceControllerPosition) {
        if (!acquireCollisionAttempt(player)) {
            return TravelResult.RATE_LIMITED;
        }
        if (hasActiveReturnTarget(player)) {
            return TravelResult.ACTIVE_RETURN_TARGET;
        }
        ServerLevel destination = player.server.getLevel(FarRelayKeys.LEVEL);
        if (destination == null) {
            return TravelResult.DESTINATION_UNAVAILABLE;
        }
        return travelToFarRelayAfterRateLimit(player, sourceControllerPosition, destination);
    }

    TravelResult travelToFarRelay(
            ServerPlayer player,
            BlockPos sourceControllerPosition,
            ServerLevel destination) {
        if (!acquireCollisionAttempt(player)) {
            return TravelResult.RATE_LIMITED;
        }
        if (hasActiveReturnTarget(player)) {
            return TravelResult.ACTIVE_RETURN_TARGET;
        }
        return travelToFarRelayAfterRateLimit(player, sourceControllerPosition, destination);
    }

    public boolean returnPlayer(ServerPlayer player) {
        Optional<GateReturnTarget> stored = player.getExistingData(EchoContent.GATE_RETURN_TARGET);
        if (stored.isPresent()) {
            GateReturnTarget target = stored.orElseThrow();
            ServerLevel source = player.server.getLevel(target.level());
            if (source != null) {
                Optional<BlockPos> safeSource = findSafePosition(
                        target.position(), candidate -> isSafe(player, source, candidate));
                if (safeSource.isPresent()) {
                    if (!transfer(
                            player,
                            source,
                            safeSource.orElseThrow(),
                            target.yaw(),
                            target.pitch())) {
                        return false;
                    }
                    player.removeData(EchoContent.GATE_RETURN_TARGET);
                    return true;
                }
            }
        }

        ServerLevel overworld = player.server.overworld();
        BlockPos sharedSpawn = overworld.getSharedSpawnPos();
        Optional<BlockPos> safeFallback = findSafePosition(
                sharedSpawn, candidate -> isSafe(player, overworld, candidate));
        if (safeFallback.isEmpty()) {
            BlockPos adjusted = player.adjustSpawnLocation(overworld, sharedSpawn);
            safeFallback = findSafePosition(
                    adjusted, candidate -> isSafe(player, overworld, candidate));
        }
        if (safeFallback.isEmpty()
                || !transfer(
                        player,
                        overworld,
                        safeFallback.orElseThrow(),
                        overworld.getSharedSpawnAngle(),
                        0.0F)) {
            return false;
        }
        if (stored.isPresent()) {
            player.removeData(EchoContent.GATE_RETURN_TARGET);
        }
        return true;
    }

    static Optional<BlockPos> findSafePosition(
            BlockPos requested, Predicate<BlockPos> safety) {
        if (safety.test(requested)) {
            return Optional.of(requested.immutable());
        }
        for (int verticalDistance = 0; verticalDistance <= VERTICAL_RANGE; verticalDistance++) {
            int[] verticalOffsets = verticalDistance == 0
                    ? new int[] {0}
                    : new int[] {verticalDistance, -verticalDistance};
            for (int verticalOffset : verticalOffsets) {
                for (int horizontalDistance = 0;
                        horizontalDistance <= SEARCH_RADIUS;
                        horizontalDistance++) {
                    for (int deltaX = -horizontalDistance;
                            deltaX <= horizontalDistance;
                            deltaX++) {
                        for (int deltaZ = -horizontalDistance;
                                deltaZ <= horizontalDistance;
                                deltaZ++) {
                            if (Math.max(Math.abs(deltaX), Math.abs(deltaZ))
                                            != horizontalDistance
                                    || deltaX * deltaX + deltaZ * deltaZ
                                            > SEARCH_RADIUS * SEARCH_RADIUS
                                    || deltaX == 0 && deltaZ == 0 && verticalOffset == 0) {
                                continue;
                            }
                            BlockPos candidate = requested.offset(deltaX, verticalOffset, deltaZ);
                            if (safety.test(candidate)) {
                                return Optional.of(candidate.immutable());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static void grantGateOpened(ServerPlayer player) {
        grantAdvancement(player, FarRelayKeys.GATE_OPENED);
    }

    private TravelResult travelToFarRelayAfterRateLimit(
            ServerPlayer player,
            BlockPos sourceControllerPosition,
            ServerLevel destination) {
        if (player.isRemoved()) {
            return TravelResult.TRANSFER_FAILED;
        }
        try {
            FarRelayInitializer.ensureAll(destination);
        } catch (RuntimeException exception) {
            return TravelResult.DESTINATION_UNAVAILABLE;
        }
        Optional<BlockPos> arrival = FarRelayInitializer.centralArrival(destination);
        if (arrival.isEmpty()) {
            return TravelResult.NO_SAFE_DESTINATION;
        }

        GateReturnTarget target = new GateReturnTarget(
                player.serverLevel().dimension(),
                sourceControllerPosition.above(),
                player.getYRot(),
                player.getXRot());
        player.setData(EchoContent.GATE_RETURN_TARGET, target);
        boolean transferred;
        try {
            transferred = transfer(
                    player,
                    destination,
                    arrival.orElseThrow(),
                    player.getYRot(),
                    player.getXRot());
        } catch (RuntimeException exception) {
            player.removeData(EchoContent.GATE_RETURN_TARGET);
            return TravelResult.TRANSFER_FAILED;
        }
        if (!transferred) {
            player.removeData(EchoContent.GATE_RETURN_TARGET);
            return TravelResult.TRANSFER_FAILED;
        }
        grantAdvancement(player, FarRelayKeys.FAR_RELAY_ARRIVAL);
        return TravelResult.SUCCESS;
    }

    private boolean acquireCollisionAttempt(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        long currentTick = player.serverLevel().getGameTime();
        if (persistentData.contains(LAST_COLLISION_TICK, Tag.TAG_LONG)) {
            long previousTick = persistentData.getLong(LAST_COLLISION_TICK);
            if (currentTick >= previousTick
                    && currentTick - previousTick < COLLISION_RATE_LIMIT_TICKS) {
                return false;
            }
        }
        persistentData.putLong(LAST_COLLISION_TICK, currentTick);
        return true;
    }

    private static boolean transfer(
            ServerPlayer player,
            ServerLevel destination,
            BlockPos position,
            float yaw,
            float pitch) {
        Entity transferred = player.changeDimension(new DimensionTransition(
                destination,
                position.getBottomCenter(),
                Vec3.ZERO,
                yaw,
                pitch,
                DimensionTransition.DO_NOTHING));
        if (transferred != player || player.serverLevel() != destination) {
            return false;
        }
        player.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private static boolean hasActiveReturnTarget(ServerPlayer player) {
        return player.getExistingData(EchoContent.GATE_RETURN_TARGET).isPresent();
    }

    private static boolean isSafe(
            ServerPlayer player, ServerLevel level, BlockPos position) {
        BlockPos head = position.above();
        BlockPos floor = position.below();
        if (!level.isInWorldBounds(position)
                || !level.isInWorldBounds(head)
                || !level.isInWorldBounds(floor)
                || !level.getFluidState(position).isEmpty()
                || !level.getFluidState(head).isEmpty()) {
            return false;
        }
        BlockState floorState = level.getBlockState(floor);
        BlockState feetState = level.getBlockState(position);
        BlockState headState = level.getBlockState(head);
        if (!floorState.isFaceSturdy(level, floor, Direction.UP)
                || feetState.is(EchoContent.GATE_FIELD.get())
                || headState.is(EchoContent.GATE_FIELD.get())
                || !feetState.getCollisionShape(level, position).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }
        AABB bounds = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(Vec3.ZERO)
                .move(position.getBottomCenter());
        return level.getWorldBorder().isWithinBounds(bounds)
                && level.noCollision(player, bounds)
                && !level.containsAnyLiquid(bounds);
    }

    private static void grantAdvancement(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "server_transition");
        }
    }
}
