package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.gate.GateActivationService.ActivationCode;
import org.rllabs.afterlight.gate.GateActivationService.ActivationDecision;
import org.rllabs.afterlight.gate.GateActivationService.ActivationRequest;
import org.rllabs.afterlight.gate.GatePatternMatcher.MatchResult;
import org.rllabs.afterlight.gate.GatePatternMatcher.Mismatch;
import org.rllabs.afterlight.gate.GatePatternMatcher.MismatchKind;

class GateActivationServiceTest {
    private static final long ENERGY_PROOF_TASK_ID = 0x6E494144394F75AFL;
    private static final long GATE_CORE_PROOF_TASK_ID = 0x568026383F54186CL;
    private static final long NOW = 10_000L;
    private static final GateProgressGateway ALL_PROOFS_COMPLETE = completedTasks(
            ENERGY_PROOF_TASK_ID,
            GATE_CORE_PROOF_TASK_ID);

    private final GateActivationService service = new GateActivationService();

    @Test
    void malformedFrameIsRejected() {
        ActivationDecision decision = decide(request(malformedStructure(), 1, true, GateState.IDLE));

        assertEquals(ActivationCode.MALFORMED_STRUCTURE, decision.code());
    }

    @Test
    void blockedInteriorIsRejected() {
        ActivationDecision decision = decide(request(blockedInterior(), 1, true, GateState.IDLE));

        assertEquals(ActivationCode.INTERIOR_BLOCKED, decision.code());
    }

    @Test
    void absentCoreIsRejected() {
        ActivationDecision decision = decide(request(matchedStructure(), 0, true, GateState.IDLE));

        assertEquals(ActivationCode.MISSING_CORE, decision.code());
    }

    @Test
    void multipleCoresAreRejected() {
        ActivationDecision decision = decide(request(matchedStructure(), 2, true, GateState.IDLE));

        assertEquals(ActivationCode.WRONG_CORE_COUNT, decision.code());
    }

    @Test
    void incompleteEnergyProofIsRejected() {
        ActivationDecision decision = service.activate(
                request(matchedStructure(), 1, true, GateState.IDLE),
                null,
                completedTasks(GATE_CORE_PROOF_TASK_ID));

        assertEquals(ActivationCode.ENERGY_PROOF_INCOMPLETE, decision.code());
    }

    @Test
    void incompleteGateCoreProofIsRejected() {
        ActivationDecision decision = service.activate(
                request(matchedStructure(), 1, true, GateState.IDLE),
                null,
                completedTasks(ENERGY_PROOF_TASK_ID));

        assertEquals(ActivationCode.GATE_CORE_PROOF_INCOMPLETE, decision.code());
    }

    @Test
    void unavailableDestinationIsRejected() {
        ActivationDecision decision = decide(request(matchedStructure(), 1, false, GateState.IDLE));

        assertEquals(ActivationCode.DESTINATION_UNAVAILABLE, decision.code());
    }

    @Test
    void openingAlreadyInProgressIsRejected() {
        ActivationDecision decision = decide(request(matchedStructure(), 1, true, GateState.OPEN));

        assertEquals(ActivationCode.ALREADY_OPEN, decision.code());
    }

    @Test
    void completedExactFtbProofsOpenForTwoHundredTwentyTicks() {
        ActivationDecision decision = decide(request(matchedStructure(), 1, true, GateState.IDLE));

        assertTrue(decision.accepted());
        assertEquals(ActivationCode.OPENED, decision.code());
        assertEquals(10_220L, decision.openDeadline());
    }

    @Test
    void staleSavedDeadlineDoesNotResume() {
        assertFalse(service.shouldResumeOpen(GateState.OPEN, 10_000L, 10_000L));
    }

    @Test
    void futureSavedDeadlineStopsResumingAtItsOriginalTick() {
        assertTrue(service.shouldResumeOpen(GateState.OPEN, 10_220L, 10_219L));
        assertFalse(service.shouldResumeOpen(GateState.OPEN, 10_220L, 10_220L));
    }

    private ActivationDecision decide(ActivationRequest request) {
        return service.activate(request, null, ALL_PROOFS_COMPLETE);
    }

    private static ActivationRequest request(
            MatchResult structure,
            int coreCount,
            boolean destinationAvailable,
            GateState state) {
        return new ActivationRequest(structure, coreCount, destinationAvailable, state, NOW);
    }

    private static MatchResult matchedStructure() {
        return new MatchResult(List.of());
    }

    private static MatchResult malformedStructure() {
        BlockPos position = new BlockPos(4, 5, 6);
        return new MatchResult(List.of(new Mismatch(
                MismatchKind.WRONG_BLOCK,
                new GateLocalPos(-3, 4),
                position,
                id("gate_frame"),
                id("signal_glass"))));
    }

    private static MatchResult blockedInterior() {
        BlockPos position = new BlockPos(7, 8, 9);
        return new MatchResult(List.of(new Mismatch(
                MismatchKind.INTERIOR_BLOCKED,
                new GateLocalPos(0, 4),
                position,
                null,
                ResourceLocation.withDefaultNamespace("stone"))));
    }

    private static GateProgressGateway completedTasks(long... taskIds) {
        Set<Long> completed = Set.copyOf(Arrays.stream(taskIds).boxed().toList());
        return (player, taskId) -> completed.contains(taskId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }
}
