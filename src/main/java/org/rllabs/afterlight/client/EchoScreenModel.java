package org.rllabs.afterlight.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRecommendation;
import org.rllabs.afterlight.route.EchoRoute;

public final class EchoScreenModel {
    private final EchoRecommendation.Kind kind;
    private final Component stateLabel;
    private final Component diagnostic;
    private final Component questTitle;
    private final Component questSubtitle;
    private final Component interactionTitle;
    private final int routePosition;
    private final int routeComplete;
    private final int routeTotal;
    private final long currentProgress;
    private final long requiredProgress;
    private final OptionalLong selectedQuestId;
    private final OptionalLong selectedTaskId;
    private final OptionalLong selectedRewardId;
    private final OptionalLong unmetDependencyId;
    private final boolean pinned;
    private final Map<Action, ActionState> actions;

    private EchoScreenModel(
            EchoRecommendation.Kind kind,
            Component stateLabel,
            Component diagnostic,
            Component questTitle,
            Component questSubtitle,
            Component interactionTitle,
            int routePosition,
            int routeComplete,
            int routeTotal,
            long currentProgress,
            long requiredProgress,
            OptionalLong selectedQuestId,
            OptionalLong selectedTaskId,
            OptionalLong selectedRewardId,
            OptionalLong unmetDependencyId,
            boolean pinned,
            Map<Action, ActionState> actions) {
        this.kind = Objects.requireNonNull(kind);
        this.stateLabel = Objects.requireNonNull(stateLabel);
        this.diagnostic = Objects.requireNonNull(diagnostic);
        this.questTitle = Objects.requireNonNull(questTitle);
        this.questSubtitle = Objects.requireNonNull(questSubtitle);
        this.interactionTitle = Objects.requireNonNull(interactionTitle);
        this.routePosition = routePosition;
        this.routeComplete = routeComplete;
        this.routeTotal = routeTotal;
        this.currentProgress = currentProgress;
        this.requiredProgress = requiredProgress;
        this.selectedQuestId = Objects.requireNonNull(selectedQuestId);
        this.selectedTaskId = Objects.requireNonNull(selectedTaskId);
        this.selectedRewardId = Objects.requireNonNull(selectedRewardId);
        this.unmetDependencyId = Objects.requireNonNull(unmetDependencyId);
        this.pinned = pinned;
        this.actions = Map.copyOf(actions);
    }

    public static EchoScreenModel from(
            EchoRoute route,
            Map<Long, EchoQuestSnapshot> snapshots,
            EchoRecommendation recommendation) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(snapshots);
        Objects.requireNonNull(recommendation);

        EchoQuestSnapshot quest = snapshots.get(recommendation.questId());
        Optional<TaskSnapshot> task = recommendation.taskId().stream()
                .mapToObj(taskId -> findTask(quest, taskId))
                .filter(Objects::nonNull)
                .findFirst();
        Optional<RewardSnapshot> reward = recommendation.rewardId().stream()
                .mapToObj(rewardId -> findReward(quest, rewardId))
                .filter(Objects::nonNull)
                .findFirst();

        int routePosition = route.questIds().indexOf(recommendation.questId()) + 1;
        int routeComplete = (int) route.questIds().stream()
                .map(snapshots::get)
                .filter(Objects::nonNull)
                .filter(EchoQuestSnapshot::teamComplete)
                .count();
        long currentProgress = task.map(TaskSnapshot::currentValue).orElseGet(() -> reward.isPresent() ? 0L : routeComplete);
        long requiredProgress = task.map(TaskSnapshot::requiredValue).orElseGet(() -> reward.isPresent() ? 1L : route.questIds().size());

        boolean exactQuestExists = quest != null;
        boolean submitEnabled = recommendation.kind() == EchoRecommendation.Kind.SUBMIT_TASK
                && !recommendation.requiresArchive()
                && task.filter(candidate -> quest.startable()
                                && !candidate.complete()
                                && candidate.manualSubmit()
                                && candidate.directInteractionSupported()
                                && candidate.submitEligible())
                        .isPresent();
        boolean claimEnabled = recommendation.kind() == EchoRecommendation.Kind.CLAIM_REWARD
                && !recommendation.requiresArchive()
                && reward.filter(candidate -> quest.teamComplete()
                                && !candidate.claimed()
                                && !candidate.choice()
                                && candidate.directInteractionSupported()
                                && candidate.claimEligible())
                        .isPresent();
        boolean signalUnavailable = recommendation.kind() == EchoRecommendation.Kind.SIGNAL_UNAVAILABLE;
        boolean routeCompleteState = recommendation.kind() == EchoRecommendation.Kind.ROUTE_COMPLETE;
        boolean pinEnabled = exactQuestExists && !signalUnavailable && !routeCompleteState;
        boolean archiveEnabled = exactQuestExists;
        boolean archiveEmphasized = archiveEnabled
                && recommendation.requiresArchive()
                && (recommendation.kind() == EchoRecommendation.Kind.SUBMIT_TASK
                        || recommendation.kind() == EchoRecommendation.Kind.CLAIM_REWARD);

        EnumMap<Action, ActionState> actions = new EnumMap<>(Action.class);
        actions.put(Action.SUBMIT, new ActionState(submitEnabled, false));
        actions.put(Action.CLAIM, new ActionState(claimEnabled, false));
        actions.put(Action.PIN, new ActionState(pinEnabled, false));
        actions.put(Action.ARCHIVE, new ActionState(archiveEnabled, archiveEmphasized));

        return new EchoScreenModel(
                recommendation.kind(),
                stateLabel(recommendation.kind(), archiveEmphasized),
                diagnostic(recommendation.kind(), archiveEmphasized),
                exactQuestExists
                        ? Component.literal(quest.title())
                        : Component.translatable("screen.afterlight.echo.route.unknown"),
                exactQuestExists
                        ? Component.literal(quest.subtitle())
                        : Component.translatable("screen.afterlight.echo.route.no_carrier"),
                interactionTitle(task, reward, recommendation.unmetDependencyId()),
                Math.max(0, routePosition),
                routeComplete,
                route.questIds().size(),
                Math.max(0L, currentProgress),
                Math.max(0L, requiredProgress),
                OptionalLong.of(recommendation.questId()),
                recommendation.taskId(),
                recommendation.rewardId(),
                recommendation.unmetDependencyId(),
                exactQuestExists && quest.pinned(),
                actions);
    }

    public static EchoScreenModel routeUnavailable() {
        EnumMap<Action, ActionState> actions = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            actions.put(action, ActionState.DISABLED);
        }
        return new EchoScreenModel(
                EchoRecommendation.Kind.SIGNAL_UNAVAILABLE,
                Component.translatable("screen.afterlight.echo.state.unavailable"),
                Component.translatable("screen.afterlight.echo.diagnostic.route_unavailable"),
                Component.translatable("screen.afterlight.echo.route.unknown"),
                Component.translatable("screen.afterlight.echo.route.no_carrier"),
                Component.translatable("screen.afterlight.echo.interaction.none"),
                0,
                0,
                0,
                0L,
                0L,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false,
                actions);
    }

    private static TaskSnapshot findTask(EchoQuestSnapshot quest, long taskId) {
        if (quest == null) {
            return null;
        }
        return quest.tasks().stream().filter(task -> task.id() == taskId).findFirst().orElse(null);
    }

    private static RewardSnapshot findReward(EchoQuestSnapshot quest, long rewardId) {
        if (quest == null) {
            return null;
        }
        return quest.rewards().stream().filter(reward -> reward.id() == rewardId).findFirst().orElse(null);
    }

    private static Component interactionTitle(
            Optional<TaskSnapshot> task,
            Optional<RewardSnapshot> reward,
            OptionalLong unmetDependencyId) {
        if (task.isPresent()) {
            return Component.literal(task.orElseThrow().title());
        }
        if (reward.isPresent()) {
            return Component.literal(reward.orElseThrow().title());
        }
        if (unmetDependencyId.isPresent()) {
            return Component.translatable(
                    "screen.afterlight.echo.interaction.dependency",
                    EchoRoute.formatQuestId(unmetDependencyId.getAsLong()));
        }
        return Component.translatable("screen.afterlight.echo.interaction.none");
    }

    private static Component stateLabel(EchoRecommendation.Kind kind, boolean archiveEmphasized) {
        if (archiveEmphasized) {
            return Component.translatable("screen.afterlight.echo.state.archive_required");
        }
        return switch (kind) {
            case SIGNAL_UNAVAILABLE -> Component.translatable("screen.afterlight.echo.state.unavailable");
            case CLAIM_REWARD, SUBMIT_TASK -> Component.translatable("screen.afterlight.echo.state.actionable");
            case LOCKED -> Component.translatable("screen.afterlight.echo.state.locked");
            case ROUTE_COMPLETE -> Component.translatable("screen.afterlight.echo.state.complete");
        };
    }

    private static Component diagnostic(EchoRecommendation.Kind kind, boolean archiveEmphasized) {
        if (archiveEmphasized) {
            return Component.translatable("screen.afterlight.echo.diagnostic.archive_required");
        }
        return switch (kind) {
            case SIGNAL_UNAVAILABLE -> Component.translatable("screen.afterlight.echo.diagnostic.signal_unavailable");
            case CLAIM_REWARD -> Component.translatable("screen.afterlight.echo.diagnostic.claim");
            case SUBMIT_TASK -> Component.translatable("screen.afterlight.echo.diagnostic.submit");
            case LOCKED -> Component.translatable("screen.afterlight.echo.diagnostic.locked");
            case ROUTE_COMPLETE -> Component.translatable("screen.afterlight.echo.diagnostic.complete");
        };
    }

    public EchoRecommendation.Kind kind() {
        return kind;
    }

    public Component stateLabel() {
        return stateLabel;
    }

    public Component diagnostic() {
        return diagnostic;
    }

    public Component questTitle() {
        return questTitle;
    }

    public Component questSubtitle() {
        return questSubtitle;
    }

    public Component interactionTitle() {
        return interactionTitle;
    }

    public int routePosition() {
        return routePosition;
    }

    public int routeComplete() {
        return routeComplete;
    }

    public int routeTotal() {
        return routeTotal;
    }

    public long currentProgress() {
        return currentProgress;
    }

    public long requiredProgress() {
        return requiredProgress;
    }

    public OptionalLong selectedQuestId() {
        return selectedQuestId;
    }

    public OptionalLong selectedTaskId() {
        return selectedTaskId;
    }

    public OptionalLong selectedRewardId() {
        return selectedRewardId;
    }

    public OptionalLong unmetDependencyId() {
        return unmetDependencyId;
    }

    public boolean pinned() {
        return pinned;
    }

    public Component pinLabel() {
        return Component.translatable(pinned
                ? "screen.afterlight.echo.action.unpin"
                : "screen.afterlight.echo.action.pin");
    }

    public ActionState action(Action action) {
        return actions.getOrDefault(Objects.requireNonNull(action), ActionState.DISABLED);
    }

    public enum Action {
        SUBMIT,
        CLAIM,
        PIN,
        ARCHIVE
    }

    public record ActionState(boolean enabled, boolean emphasized) {
        private static final ActionState DISABLED = new ActionState(false, false);
    }
}
