package org.rllabs.afterlight.route;

import java.util.Objects;
import java.util.OptionalLong;

public record EchoRecommendation(
        Kind kind,
        long questId,
        OptionalLong taskId,
        OptionalLong rewardId,
        OptionalLong unmetDependencyId,
        boolean requiresArchive) {
    public EchoRecommendation {
        kind = Objects.requireNonNull(kind);
        taskId = Objects.requireNonNull(taskId);
        rewardId = Objects.requireNonNull(rewardId);
        unmetDependencyId = Objects.requireNonNull(unmetDependencyId);
    }

    public static EchoRecommendation claimReward(long questId, long rewardId, boolean requiresArchive) {
        return new EchoRecommendation(
                Kind.CLAIM_REWARD,
                questId,
                OptionalLong.empty(),
                OptionalLong.of(rewardId),
                OptionalLong.empty(),
                requiresArchive);
    }

    public static EchoRecommendation signalUnavailable(long questId) {
        return new EchoRecommendation(
                Kind.SIGNAL_UNAVAILABLE,
                questId,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                true);
    }

    public static EchoRecommendation submitTask(long questId, OptionalLong taskId, boolean requiresArchive) {
        return new EchoRecommendation(
                Kind.SUBMIT_TASK,
                questId,
                taskId,
                OptionalLong.empty(),
                OptionalLong.empty(),
                requiresArchive);
    }

    public static EchoRecommendation locked(long questId, OptionalLong unmetDependencyId) {
        return new EchoRecommendation(
                Kind.LOCKED,
                questId,
                OptionalLong.empty(),
                OptionalLong.empty(),
                unmetDependencyId,
                false);
    }

    public static EchoRecommendation routeComplete(long terminalQuestId) {
        return new EchoRecommendation(
                Kind.ROUTE_COMPLETE,
                terminalQuestId,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false);
    }

    public boolean canSubmitDirectly() {
        return kind == Kind.SUBMIT_TASK && taskId.isPresent() && !requiresArchive;
    }

    public boolean canClaimDirectly() {
        return kind == Kind.CLAIM_REWARD && rewardId.isPresent() && !requiresArchive;
    }

    public enum Kind {
        SIGNAL_UNAVAILABLE,
        CLAIM_REWARD,
        SUBMIT_TASK,
        LOCKED,
        ROUTE_COMPLETE
    }
}
