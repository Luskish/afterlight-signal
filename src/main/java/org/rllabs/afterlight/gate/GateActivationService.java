package org.rllabs.afterlight.gate;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.rllabs.afterlight.gate.GatePatternMatcher.MatchResult;
import org.rllabs.afterlight.gate.GatePatternMatcher.MismatchKind;

public final class GateActivationService {
    public static final long OPEN_TICKS = 220L;
    public static final long ENERGY_PROOF_TASK_ID = 0x6E494144394F75AFL;
    public static final long GATE_CORE_PROOF_TASK_ID = 0x568026383F54186CL;
    private static final long NO_DEADLINE = -1L;

    public ActivationDecision activate(
            ActivationRequest request,
            ServerPlayer player,
            GateProgressGateway progressGateway) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressGateway, "progressGateway");

        if (request.state() == GateState.OPEN) {
            return rejected(ActivationCode.ALREADY_OPEN);
        }
        boolean hasNonInteriorMismatch = request.structure().mismatches().stream()
                .anyMatch(mismatch -> mismatch.kind() != MismatchKind.INTERIOR_BLOCKED);
        if (hasNonInteriorMismatch) {
            return rejected(ActivationCode.MALFORMED_STRUCTURE);
        }
        if (!request.structure().matches()) {
            return rejected(ActivationCode.INTERIOR_BLOCKED);
        }
        if (request.coreCount() == 0) {
            return rejected(ActivationCode.MISSING_CORE);
        }
        if (request.coreCount() != 1) {
            return rejected(ActivationCode.WRONG_CORE_COUNT);
        }
        if (!progressGateway.completed(player, ENERGY_PROOF_TASK_ID)) {
            return rejected(ActivationCode.ENERGY_PROOF_INCOMPLETE);
        }
        if (!progressGateway.completed(player, GATE_CORE_PROOF_TASK_ID)) {
            return rejected(ActivationCode.GATE_CORE_PROOF_INCOMPLETE);
        }
        if (!request.destinationAvailable()) {
            return rejected(ActivationCode.DESTINATION_UNAVAILABLE);
        }
        return new ActivationDecision(
                ActivationCode.OPENED,
                request.currentGameTime() + OPEN_TICKS);
    }

    public boolean shouldResumeOpen(GateState state, long savedDeadline, long currentGameTime) {
        return state == GateState.OPEN && savedDeadline > currentGameTime;
    }

    private static ActivationDecision rejected(ActivationCode code) {
        return new ActivationDecision(code, NO_DEADLINE);
    }

    public enum ActivationCode {
        OPENED,
        MALFORMED_STRUCTURE,
        INTERIOR_BLOCKED,
        MISSING_CORE,
        WRONG_CORE_COUNT,
        ENERGY_PROOF_INCOMPLETE,
        GATE_CORE_PROOF_INCOMPLETE,
        DESTINATION_UNAVAILABLE,
        ALREADY_OPEN
    }

    public record ActivationDecision(ActivationCode code, long openDeadline) {
        public ActivationDecision {
            Objects.requireNonNull(code, "code");
        }

        public boolean accepted() {
            return code == ActivationCode.OPENED;
        }
    }

    public record ActivationRequest(
            MatchResult structure,
            int coreCount,
            boolean destinationAvailable,
            GateState state,
            long currentGameTime) {
        public ActivationRequest {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(state, "state");
        }
    }
}
