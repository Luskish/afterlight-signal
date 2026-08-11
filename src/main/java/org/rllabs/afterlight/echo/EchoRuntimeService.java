package org.rllabs.afterlight.echo;

import java.time.Instant;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.echo.EchoRecoveryService.RecoveryResult;
import org.rllabs.afterlight.echo.EchoRecoveryService.RecoveryStatus;

public final class EchoRuntimeService {
    public static final EchoRuntimeService INSTANCE = new EchoRuntimeService();

    private final EchoRecoveryService recoveryService = new EchoRecoveryService();

    public RecoveryResult issueFirst(ServerPlayer player) {
        EchoBond currentBond = currentBond(player);
        RecoveryResult result = recoveryService.issueFirst(
                player.getUUID(),
                currentBond,
                Instant.now().getEpochSecond(),
                new PlayerInventory(player));
        return updateBondAfterInsertion(player, result);
    }

    public RecoveryResult recover(ServerPlayer player) {
        EchoBond currentBond = currentBond(player);
        RecoveryResult result = recoveryService.recover(
                player.getUUID(),
                currentBond,
                Instant.now().getEpochSecond(),
                new PlayerInventory(player));
        return updateBondAfterInsertion(player, result);
    }

    public OpenStatus validateHeldItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(EchoContent.ECHO.get())) {
            return OpenStatus.SIGNAL_NOT_ACQUIRED;
        }
        return validateIdentity(
                player.getUUID(),
                player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED),
                stack.get(EchoContent.ECHO_IDENTITY.get()));
    }

    OpenStatus validateIdentity(UUID playerId, EchoBond bond, EchoIdentity identity) {
        if (identity == null) {
            return OpenStatus.SIGNAL_NOT_ACQUIRED;
        }
        if (!playerId.equals(identity.owner())) {
            return OpenStatus.FOREIGN_UNIT;
        }
        if (!bond.issued() || bond.generation() <= 0) {
            return OpenStatus.SIGNAL_NOT_ACQUIRED;
        }
        if (bond.generation() != identity.generation()) {
            return OpenStatus.SUPERSEDED_UNIT;
        }
        return recoveryService.isValid(playerId, bond, identity)
                ? OpenStatus.APPROVED
                : OpenStatus.SIGNAL_NOT_ACQUIRED;
    }

    public Component resultMessage(RecoveryResult result, boolean firstIssue) {
        if (result.status() == RecoveryStatus.ISSUED) {
            return firstIssue
                    ? Component.translatable("message.afterlight.echo.first_issue")
                    : Component.translatable("message.afterlight.echo.recovery_success", result.bond().generation());
        }
        return Component.translatable(failureMessageKey(result.status()));
    }

    String failureMessageKey(RecoveryStatus status) {
        return switch (status) {
            case NO_SPACE -> "message.afterlight.echo.no_space";
            case INSERT_FAILED -> "message.afterlight.echo.insertion_failed";
            case GENERATION_EXHAUSTED -> "message.afterlight.echo.generation_exhausted";
            case ISSUED -> throw new IllegalArgumentException("ISSUED is not a failure status");
        };
    }

    private EchoBond currentBond(ServerPlayer player) {
        return player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
    }

    private RecoveryResult updateBondAfterInsertion(ServerPlayer player, RecoveryResult result) {
        if (result.status() == RecoveryStatus.ISSUED) {
            player.setData(EchoContent.ECHO_BOND, result.bond());
        }
        return result;
    }

    public enum OpenStatus {
        APPROVED(null),
        SIGNAL_NOT_ACQUIRED("message.afterlight.echo.signal_not_acquired"),
        FOREIGN_UNIT("message.afterlight.echo.foreign_unit"),
        SUPERSEDED_UNIT("message.afterlight.echo.superseded_unit");

        private final String messageKey;

        OpenStatus(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    private static final class PlayerInventory implements EchoInventory {
        private final ServerPlayer player;

        private PlayerInventory(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public boolean hasFreeSlot() {
            return player.getInventory().getFreeSlot() >= 0;
        }

        @Override
        public boolean insert(EchoIdentity identity) {
            ItemStack stack = new ItemStack(EchoContent.ECHO.get());
            stack.set(EchoContent.ECHO_IDENTITY.get(), identity);
            return player.getInventory().add(stack);
        }
    }
}
