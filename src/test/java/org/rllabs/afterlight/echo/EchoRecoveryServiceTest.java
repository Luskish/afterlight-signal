package org.rllabs.afterlight.echo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.echo.EchoRecoveryService.RecoveryStatus;

class EchoRecoveryServiceTest {
    private final EchoRecoveryService service = new EchoRecoveryService();

    @Test
    void firstIssueCreatesGenerationOne() {
        var playerId = UUID.fromString("2b36a8e4-a03b-4c98-bfb2-d693466adef5");
        var inventory = new RecordingInventory(true, true);

        var result = service.issueFirst(playerId, EchoBond.UNISSUED, 10L, inventory);

        var expectedIdentity = new EchoIdentity(playerId, 1);
        assertEquals(RecoveryStatus.ISSUED, result.status());
        assertEquals(new EchoBond(true, 1, 10L), result.bond());
        assertEquals(Optional.of(expectedIdentity), result.identity());
        assertEquals(expectedIdentity, inventory.insertedIdentity());
        assertEquals(1, inventory.insertCalls());
        assertTrue(service.isValid(playerId, result.bond(), expectedIdentity));
    }

    @Test
    void recoveryIncrementsGenerationOnce() {
        var playerId = UUID.fromString("e5486bf1-8505-499f-a57f-5bf97ed080ba");
        var originalBond = new EchoBond(true, 4, 6L);
        var inventory = new RecordingInventory(true, true);

        var result = service.recover(playerId, originalBond, 10L, inventory);

        var expectedIdentity = new EchoIdentity(playerId, 5);
        assertEquals(RecoveryStatus.ISSUED, result.status());
        assertEquals(new EchoBond(true, 5, 10L), result.bond());
        assertEquals(Optional.of(expectedIdentity), result.identity());
        assertEquals(expectedIdentity, inventory.insertedIdentity());
        assertEquals(1, inventory.insertCalls());
    }

    @Test
    void fullInventoryLeavesBondUnchanged() {
        var playerId = UUID.fromString("1bd52f3b-f19b-4364-af8b-cb1ba39dd8e9");
        var originalBond = new EchoBond(true, 2, 5L);
        var inventoryWithoutSpace = new RecordingInventory(false, true);

        var result = service.recover(playerId, originalBond, 10L, inventoryWithoutSpace);

        assertEquals(RecoveryStatus.NO_SPACE, result.status());
        assertEquals(originalBond, result.bond());
        assertTrue(result.identity().isEmpty());
        assertEquals(0, inventoryWithoutSpace.insertCalls());

        var inventoryRejectingInsertion = new RecordingInventory(true, false);
        var insertionFailure = service.recover(playerId, originalBond, 10L, inventoryRejectingInsertion);
        assertEquals(RecoveryStatus.INSERT_FAILED, insertionFailure.status());
        assertEquals(originalBond, insertionFailure.bond());
        assertTrue(insertionFailure.identity().isEmpty());
        assertEquals(1, inventoryRejectingInsertion.insertCalls());
    }

    @Test
    void staleGenerationIsInvalid() {
        var playerId = UUID.fromString("565f4a5f-e7ad-40fd-b93f-1a487c9367c4");
        var bond = new EchoBond(true, 3, 10L);
        var staleIdentity = new EchoIdentity(playerId, 2);

        assertFalse(service.isValid(playerId, bond, staleIdentity));
    }

    @Test
    void foreignOwnerIsInvalid() {
        var playerId = UUID.fromString("fb43de64-3fab-445f-bbdf-fdc5b350a883");
        var foreignOwner = UUID.fromString("b8d3cb43-9a60-408c-8114-f74e0eb7508f");
        var bond = new EchoBond(true, 3, 10L);
        var foreignIdentity = new EchoIdentity(foreignOwner, 3);

        assertFalse(service.isValid(playerId, bond, foreignIdentity));
    }

    @Test
    void generationMustStayPositive() {
        var playerId = UUID.fromString("8196a557-cb79-4744-9a0d-adfd0e112d19");

        assertFalse(service.isValid(
                playerId,
                new EchoBond(true, 0, 10L),
                new EchoIdentity(playerId, 0)));
        assertFalse(service.isValid(
                playerId,
                new EchoBond(true, -1, 10L),
                new EchoIdentity(playerId, -1)));
        assertFalse(service.isValid(
                playerId,
                new EchoBond(false, 1, 10L),
                new EchoIdentity(playerId, 1)));
    }

    @Test
    void bondCodecRoundTrips() {
        var original = new EchoBond(true, 7, 1_723_371_234L);

        var encoded = EchoBond.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = EchoBond.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    void identityCodecRoundTrips() {
        var original = new EchoIdentity(
                UUID.fromString("4e358f03-9f9b-4076-b76a-e702f8e20f90"),
                7);

        var encoded = EchoIdentity.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = EchoIdentity.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
    }

    private static final class RecordingInventory implements EchoInventory {
        private final boolean hasFreeSlot;
        private final boolean acceptsInsertion;
        private int insertCalls;
        private EchoIdentity insertedIdentity;

        private RecordingInventory(boolean hasFreeSlot, boolean acceptsInsertion) {
            this.hasFreeSlot = hasFreeSlot;
            this.acceptsInsertion = acceptsInsertion;
        }

        @Override
        public boolean hasFreeSlot() {
            return hasFreeSlot;
        }

        @Override
        public boolean insert(EchoIdentity identity) {
            insertCalls++;
            insertedIdentity = identity;
            return acceptsInsertion;
        }

        private int insertCalls() {
            return insertCalls;
        }

        private EchoIdentity insertedIdentity() {
            return insertedIdentity;
        }
    }
}
