package org.rllabs.afterlight.route;

import java.util.List;
import java.util.Objects;

public record EchoQuestSnapshot(
        long questId,
        String title,
        String subtitle,
        boolean teamComplete,
        boolean startable,
        List<Long> unmetDependencyIds,
        List<TaskSnapshot> tasks,
        List<RewardSnapshot> rewards) {
    public EchoQuestSnapshot {
        title = Objects.requireNonNull(title);
        subtitle = Objects.requireNonNull(subtitle);
        unmetDependencyIds = List.copyOf(Objects.requireNonNull(unmetDependencyIds));
        tasks = List.copyOf(Objects.requireNonNull(tasks));
        rewards = List.copyOf(Objects.requireNonNull(rewards));
    }

    public record TaskSnapshot(
            long id,
            String title,
            long currentValue,
            long requiredValue,
            boolean complete,
            boolean manualSubmit,
            boolean submitEligible) {
        public TaskSnapshot {
            title = Objects.requireNonNull(title);
        }
    }

    public record RewardSnapshot(
            long id,
            String title,
            boolean claimed,
            boolean choice,
            boolean claimEligible) {
        public RewardSnapshot {
            title = Objects.requireNonNull(title);
        }
    }
}
