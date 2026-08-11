package org.rllabs.afterlight.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRecommendation.Kind;

class EchoRouteResolverTest {
    private static final long FIRST_QUEST = 0x11L;
    private static final long SECOND_QUEST = 0x22L;
    private static final long THIRD_QUEST = 0x33L;
    private static final long TERMINAL_QUEST = 0x44L;
    private static final long SIDE_QUEST = 0x55L;

    private final EchoRouteResolver resolver = new EchoRouteResolver();

    @Test
    void earliestUnclaimedRewardPrecedesStartableAndLockedQuests() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, startable(FIRST_QUEST, manualTask(0x101L, true)),
                SECOND_QUEST, completeWithRewards(SECOND_QUEST,
                        reward(0x201L, true, false, true),
                        reward(0x202L, false, false, true)),
                THIRD_QUEST, locked(THIRD_QUEST, 0x901L),
                TERMINAL_QUEST, incomplete(TERMINAL_QUEST)));

        assertRecommendation(
                recommendation,
                Kind.CLAIM_REWARD,
                SECOND_QUEST,
                OptionalLong.empty(),
                OptionalLong.of(0x202L),
                OptionalLong.empty(),
                false);
    }

    @Test
    void earliestRouteRewardAndConfiguredRewardOrderWin() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, completeWithRewards(FIRST_QUEST,
                        reward(0x102L, false, false, true),
                        reward(0x103L, false, false, true)),
                SECOND_QUEST, completeWithRewards(SECOND_QUEST,
                        reward(0x202L, false, false, true))));

        assertEquals(FIRST_QUEST, recommendation.questId());
        assertEquals(OptionalLong.of(0x102L), recommendation.rewardId());
    }

    @Test
    void unavailableRewardOnIncompleteQuestDoesNotPrecedeSubmission() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, snapshot(
                        FIRST_QUEST,
                        false,
                        true,
                        List.of(),
                        List.of(manualTask(0x101L, true)),
                        List.of(reward(0x102L, false, false, false)))));

        assertEquals(Kind.SUBMIT_TASK, recommendation.kind());
        assertEquals(OptionalLong.of(0x101L), recommendation.taskId());
    }

    @Test
    void choiceRewardRequiresArchiveAndCannotDirectClaim() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, completeWithRewards(FIRST_QUEST,
                        reward(0x102L, false, true, true))));

        assertEquals(Kind.CLAIM_REWARD, recommendation.kind());
        assertEquals(FIRST_QUEST, recommendation.questId());
        assertEquals(OptionalLong.of(0x102L), recommendation.rewardId());
        assertTrue(recommendation.requiresArchive());
        assertFalse(recommendation.canClaimDirectly());
    }

    @Test
    void unsupportedRewardRequiresArchiveAndCannotDirectClaim() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, completeWithRewards(FIRST_QUEST,
                        reward(0x102L, false, false, false))));

        assertEquals(Kind.CLAIM_REWARD, recommendation.kind());
        assertEquals(OptionalLong.of(0x102L), recommendation.rewardId());
        assertTrue(recommendation.requiresArchive());
        assertFalse(recommendation.canClaimDirectly());
    }

    @Test
    void earliestStartableIncompleteQuestSelectsDirectManualTask() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, startable(FIRST_QUEST,
                        automaticTask(0x101L),
                        manualTask(0x102L, true),
                        manualTask(0x103L, true)),
                SECOND_QUEST, startable(SECOND_QUEST, manualTask(0x202L, true))));

        assertRecommendation(
                recommendation,
                Kind.SUBMIT_TASK,
                FIRST_QUEST,
                OptionalLong.of(0x102L),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false);
    }

    @Test
    void unsupportedStartableInteractionRequiresArchive() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, startable(FIRST_QUEST,
                        automaticTask(0x101L),
                        manualTask(0x102L, false))));

        assertRecommendation(
                recommendation,
                Kind.SUBMIT_TASK,
                FIRST_QUEST,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                true);
        assertFalse(recommendation.canSubmitDirectly());
    }

    @Test
    void teamCompleteQuestDoesNotBecomeSubmitRecommendation() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, snapshot(FIRST_QUEST, true, true, List.of(),
                        List.of(manualTask(0x102L, true)), List.of()),
                SECOND_QUEST, startable(SECOND_QUEST, manualTask(0x202L, true))));

        assertEquals(Kind.SUBMIT_TASK, recommendation.kind());
        assertEquals(SECOND_QUEST, recommendation.questId());
        assertEquals(OptionalLong.of(0x202L), recommendation.taskId());
    }

    @Test
    void teamCompleteQuestMayStillExposeIndividualReward() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, completeWithRewards(FIRST_QUEST,
                        reward(0x102L, false, false, true)),
                SECOND_QUEST, startable(SECOND_QUEST, manualTask(0x202L, true))));

        assertEquals(Kind.CLAIM_REWARD, recommendation.kind());
        assertEquals(FIRST_QUEST, recommendation.questId());
    }

    @Test
    void earliestLockedQuestCopiesEarliestUnmetDependency() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, complete(FIRST_QUEST),
                SECOND_QUEST, locked(SECOND_QUEST, 0x901L, 0x902L),
                THIRD_QUEST, locked(THIRD_QUEST, 0x903L)));

        assertRecommendation(
                recommendation,
                Kind.LOCKED,
                SECOND_QUEST,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.of(0x901L),
                false);
    }

    @Test
    void configuredSegmentAndQuestOrderControlsPrecedence() {
        EchoRoute configured = new EchoRoute(1, TERMINAL_QUEST, List.of(
                new EchoRoute.Segment("later_dependency", List.of("root"), List.of(THIRD_QUEST, SECOND_QUEST)),
                new EchoRoute.Segment("root", List.of(), List.of(FIRST_QUEST, TERMINAL_QUEST))));
        EchoRecommendation recommendation = resolver.resolve(configured, Map.of(
                FIRST_QUEST, locked(FIRST_QUEST, 0x901L),
                SECOND_QUEST, locked(SECOND_QUEST, 0x902L),
                THIRD_QUEST, locked(THIRD_QUEST, 0x903L),
                TERMINAL_QUEST, locked(TERMINAL_QUEST, 0x904L)));

        assertEquals(THIRD_QUEST, recommendation.questId());
        assertEquals(OptionalLong.of(0x903L), recommendation.unmetDependencyId());
    }

    @Test
    void sideQuestSnapshotsNeverDisplaceConfiguredRoute() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, startable(FIRST_QUEST, manualTask(0x101L, true)),
                SIDE_QUEST, completeWithRewards(SIDE_QUEST,
                        reward(0x501L, false, false, true))));

        assertEquals(Kind.SUBMIT_TASK, recommendation.kind());
        assertEquals(FIRST_QUEST, recommendation.questId());
        assertEquals(OptionalLong.of(0x101L), recommendation.taskId());
    }

    @Test
    void missingSnapshotsAreIgnoredWithoutMutatingInput() {
        Map<Long, EchoQuestSnapshot> snapshots = new HashMap<>();
        snapshots.put(SECOND_QUEST, locked(SECOND_QUEST, 0x902L));
        Map<Long, EchoQuestSnapshot> before = Map.copyOf(snapshots);

        EchoRecommendation recommendation = resolver.resolve(route(), snapshots);

        assertEquals(Kind.LOCKED, recommendation.kind());
        assertEquals(SECOND_QUEST, recommendation.questId());
        assertEquals(before, snapshots);
    }

    @Test
    void entirelyMissingSnapshotsReturnRouteCompleteSafely() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of());

        assertRecommendation(
                recommendation,
                Kind.ROUTE_COMPLETE,
                TERMINAL_QUEST,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false);
    }

    @Test
    void allKnownRouteQuestsCompleteReturnsRouteComplete() {
        EchoRecommendation recommendation = resolver.resolve(route(), Map.of(
                FIRST_QUEST, complete(FIRST_QUEST),
                SECOND_QUEST, complete(SECOND_QUEST),
                THIRD_QUEST, complete(THIRD_QUEST),
                TERMINAL_QUEST, complete(TERMINAL_QUEST),
                SIDE_QUEST, startable(SIDE_QUEST, manualTask(0x501L, true))));

        assertRecommendation(
                recommendation,
                Kind.ROUTE_COMPLETE,
                TERMINAL_QUEST,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false);
    }

    @Test
    void snapshotCollectionsAreImmutableCopies() {
        var dependencies = new ArrayList<>(List.of(0x901L));
        var tasks = new ArrayList<>(List.of(manualTask(0x101L, true)));
        var rewards = new ArrayList<>(List.of(reward(0x201L, false, false, true)));

        EchoQuestSnapshot snapshot = snapshot(
                FIRST_QUEST, false, true, dependencies, tasks, rewards);
        dependencies.clear();
        tasks.clear();
        rewards.clear();

        assertEquals(List.of(0x901L), snapshot.unmetDependencyIds());
        assertEquals(1, snapshot.tasks().size());
        assertEquals(1, snapshot.rewards().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unmetDependencyIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.tasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rewards().clear());
    }

    @Test
    void recommendationKindContainsExactlyFourStates() {
        assertEquals(
                List.of("CLAIM_REWARD", "SUBMIT_TASK", "LOCKED", "ROUTE_COMPLETE"),
                java.util.Arrays.stream(Kind.values()).map(Enum::name).toList());
    }

    private static EchoRoute route() {
        return new EchoRoute(1, TERMINAL_QUEST, List.of(
                new EchoRoute.Segment("cold_boot", List.of(), List.of(FIRST_QUEST, SECOND_QUEST)),
                new EchoRoute.Segment("memory", List.of("cold_boot"), List.of(THIRD_QUEST, TERMINAL_QUEST))));
    }

    private static EchoQuestSnapshot complete(long questId) {
        return snapshot(questId, true, true, List.of(), List.of(), List.of());
    }

    private static EchoQuestSnapshot completeWithRewards(long questId, RewardSnapshot... rewards) {
        return snapshot(questId, true, true, List.of(), List.of(), List.of(rewards));
    }

    private static EchoQuestSnapshot incomplete(long questId) {
        return snapshot(questId, false, true, List.of(), List.of(), List.of());
    }

    private static EchoQuestSnapshot startable(long questId, TaskSnapshot... tasks) {
        return snapshot(questId, false, true, List.of(), List.of(tasks), List.of());
    }

    private static EchoQuestSnapshot locked(long questId, Long... unmetDependencies) {
        return snapshot(questId, false, false, List.of(unmetDependencies), List.of(), List.of());
    }

    private static EchoQuestSnapshot snapshot(
            long questId,
            boolean teamComplete,
            boolean startable,
            List<Long> unmetDependencies,
            List<TaskSnapshot> tasks,
            List<RewardSnapshot> rewards) {
        return new EchoQuestSnapshot(
                questId,
                "Quest " + questId,
                "Subtitle " + questId,
                teamComplete,
                startable,
                unmetDependencies,
                tasks,
                rewards);
    }

    private static TaskSnapshot automaticTask(long taskId) {
        return new TaskSnapshot(taskId, "Automatic", 0L, 1L, false, false, false);
    }

    private static TaskSnapshot manualTask(long taskId, boolean submitEligible) {
        return new TaskSnapshot(taskId, "Manual", 0L, 1L, false, true, submitEligible);
    }

    private static RewardSnapshot reward(
            long rewardId,
            boolean claimed,
            boolean choice,
            boolean claimEligible) {
        return new RewardSnapshot(rewardId, "Reward " + rewardId, claimed, choice, claimEligible);
    }

    private static void assertRecommendation(
            EchoRecommendation recommendation,
            Kind kind,
            long questId,
            OptionalLong taskId,
            OptionalLong rewardId,
            OptionalLong unmetDependencyId,
            boolean requiresArchive) {
        assertEquals(kind, recommendation.kind());
        assertEquals(questId, recommendation.questId());
        assertEquals(taskId, recommendation.taskId());
        assertEquals(rewardId, recommendation.rewardId());
        assertEquals(unmetDependencyId, recommendation.unmetDependencyId());
        assertEquals(requiresArchive, recommendation.requiresArchive());
    }
}
