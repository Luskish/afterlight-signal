package org.rllabs.afterlight.route;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

public final class EchoRouteResolver {
    public EchoRecommendation resolve(EchoRoute route, Map<Long, EchoQuestSnapshot> snapshots) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(snapshots);

        for (long questId : route.questIds()) {
            EchoQuestSnapshot snapshot = snapshots.get(questId);
            if (snapshot == null || snapshot.questId() != questId) {
                return EchoRecommendation.signalUnavailable(questId);
            }
        }

        for (long questId : route.questIds()) {
            EchoQuestSnapshot snapshot = snapshots.get(questId);
            if (!snapshot.teamComplete()) {
                continue;
            }
            for (EchoQuestSnapshot.RewardSnapshot reward : snapshot.rewards()) {
                if (reward.claimed()) {
                    continue;
                }
                if (reward.choice() || !reward.directInteractionSupported()) {
                    return EchoRecommendation.claimReward(questId, reward.id(), true);
                }
                if (reward.claimEligible()) {
                    return EchoRecommendation.claimReward(questId, reward.id(), false);
                }
            }
        }

        for (long questId : route.questIds()) {
            EchoQuestSnapshot snapshot = snapshots.get(questId);
            if (snapshot == null || snapshot.teamComplete() || !snapshot.startable()) {
                continue;
            }
            OptionalLong taskId = snapshot.tasks().stream()
                    .filter(task -> !task.complete())
                    .filter(EchoQuestSnapshot.TaskSnapshot::manualSubmit)
                    .filter(EchoQuestSnapshot.TaskSnapshot::directInteractionSupported)
                    .filter(EchoQuestSnapshot.TaskSnapshot::submitEligible)
                    .mapToLong(EchoQuestSnapshot.TaskSnapshot::id)
                    .findFirst();
            return EchoRecommendation.submitTask(questId, taskId, taskId.isEmpty());
        }

        for (long questId : route.questIds()) {
            EchoQuestSnapshot snapshot = snapshots.get(questId);
            if (snapshot == null || snapshot.teamComplete() || snapshot.startable()) {
                continue;
            }
            OptionalLong unmetDependencyId = snapshot.unmetDependencyIds().stream()
                    .mapToLong(Long::longValue)
                    .findFirst();
            return EchoRecommendation.locked(questId, unmetDependencyId);
        }

        return EchoRecommendation.routeComplete(route.terminalQuestId());
    }
}
