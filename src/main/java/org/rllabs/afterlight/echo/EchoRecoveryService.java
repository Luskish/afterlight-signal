package org.rllabs.afterlight.echo;

import java.util.Optional;
import java.util.UUID;

public final class EchoRecoveryService {
    public RecoveryResult issueFirst(
            UUID playerId,
            EchoBond currentBond,
            long issuedAtEpochSecond,
            EchoInventory inventory) {
        if (!inventory.hasFreeSlot()) {
            return failed(RecoveryStatus.NO_SPACE, currentBond);
        }
        return insert(playerId, currentBond, 1, issuedAtEpochSecond, inventory);
    }

    public RecoveryResult recover(
            UUID playerId,
            EchoBond currentBond,
            long issuedAtEpochSecond,
            EchoInventory inventory) {
        if (!inventory.hasFreeSlot()) {
            return failed(RecoveryStatus.NO_SPACE, currentBond);
        }
        if (currentBond.generation() == Integer.MAX_VALUE) {
            return failed(RecoveryStatus.GENERATION_EXHAUSTED, currentBond);
        }
        return insert(
                playerId,
                currentBond,
                currentBond.generation() + 1,
                issuedAtEpochSecond,
                inventory);
    }

    public boolean isValid(UUID playerId, EchoBond bond, EchoIdentity identity) {
        return playerId.equals(identity.owner())
                && bond.issued()
                && bond.generation() > 0
                && bond.generation() == identity.generation();
    }

    private RecoveryResult insert(
            UUID playerId,
            EchoBond currentBond,
            int generation,
            long issuedAtEpochSecond,
            EchoInventory inventory) {
        var identity = new EchoIdentity(playerId, generation);
        if (!inventory.insert(identity)) {
            return failed(RecoveryStatus.INSERT_FAILED, currentBond);
        }
        var updatedBond = new EchoBond(true, generation, issuedAtEpochSecond);
        return new RecoveryResult(RecoveryStatus.ISSUED, updatedBond, Optional.of(identity));
    }

    private RecoveryResult failed(RecoveryStatus status, EchoBond currentBond) {
        return new RecoveryResult(status, currentBond, Optional.empty());
    }

    public enum RecoveryStatus {
        ISSUED,
        NO_SPACE,
        INSERT_FAILED,
        GENERATION_EXHAUSTED
    }

    public record RecoveryResult(
            RecoveryStatus status,
            EchoBond bond,
            Optional<EchoIdentity> identity) {
    }
}
