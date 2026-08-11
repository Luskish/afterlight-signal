package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.client.EchoScreenModel.Action;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRecommendation;
import org.rllabs.afterlight.route.EchoRoute;

class EchoScreenModelTest {
    private static final long QUEST_ID = 0x11L;
    private static final long TASK_ID = 0x21L;
    private static final long REWARD_ID = 0x31L;

    @Test
    void enablesSubmitOnlyForManualStartableTask() {
        TaskSnapshot eligible = task(false, true, true, true);

        EchoScreenModel model = model(
                snapshot(false, true, false, List.of(eligible), List.of()),
                EchoRecommendation.submitTask(QUEST_ID, OptionalLong.of(TASK_ID), false));

        assertTrue(model.action(Action.SUBMIT).enabled());
        assertFalse(model.action(Action.CLAIM).enabled());
        assertTrue(model.action(Action.PIN).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());

        assertFalse(model(
                        snapshot(false, false, false, List.of(eligible), List.of()),
                        EchoRecommendation.submitTask(QUEST_ID, OptionalLong.of(TASK_ID), false))
                .action(Action.SUBMIT).enabled());
        assertFalse(model(
                        snapshot(false, true, false, List.of(task(false, false, true, true)), List.of()),
                        EchoRecommendation.submitTask(QUEST_ID, OptionalLong.of(TASK_ID), false))
                .action(Action.SUBMIT).enabled());
        assertFalse(model(
                        snapshot(false, true, false, List.of(task(false, true, true, false)), List.of()),
                        EchoRecommendation.submitTask(QUEST_ID, OptionalLong.of(TASK_ID), false))
                .action(Action.SUBMIT).enabled());
    }

    @Test
    void enablesClaimOnlyForUnclaimedNonChoiceReward() {
        RewardSnapshot eligible = reward(false, false, true, true);

        EchoScreenModel model = model(
                snapshot(true, false, false, List.of(), List.of(eligible)),
                EchoRecommendation.claimReward(QUEST_ID, REWARD_ID, false));

        assertTrue(model.action(Action.CLAIM).enabled());
        assertFalse(model.action(Action.SUBMIT).enabled());

        assertFalse(claimModel(reward(true, false, true, true)).action(Action.CLAIM).enabled());
        assertFalse(claimModel(reward(false, true, false, false)).action(Action.CLAIM).enabled());
        assertFalse(claimModel(reward(false, false, false, false)).action(Action.CLAIM).enabled());
        assertFalse(claimModel(reward(false, false, true, false)).action(Action.CLAIM).enabled());
    }

    @Test
    void sendsChoiceRewardToArchive() {
        EchoScreenModel model = model(
                snapshot(true, false, false, List.of(), List.of(reward(false, true, false, false))),
                EchoRecommendation.claimReward(QUEST_ID, REWARD_ID, true));

        assertFalse(model.action(Action.CLAIM).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());
        assertTrue(model.action(Action.ARCHIVE).emphasized());
    }

    @Test
    void unsupportedTaskSendsInteractionToArchive() {
        EchoScreenModel model = model(
                snapshot(false, true, false, List.of(task(false, true, false, false)), List.of()),
                EchoRecommendation.submitTask(QUEST_ID, OptionalLong.empty(), true));

        assertFalse(model.action(Action.SUBMIT).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());
        assertTrue(model.action(Action.ARCHIVE).emphasized());
    }

    @Test
    void signalMissingDisablesMutationActions() {
        EchoScreenModel model = EchoScreenModel.from(
                route(), Map.of(), EchoRecommendation.signalUnavailable(QUEST_ID));

        assertFalse(model.action(Action.SUBMIT).enabled());
        assertFalse(model.action(Action.CLAIM).enabled());
        assertFalse(model.action(Action.PIN).enabled());
        assertFalse(model.action(Action.ARCHIVE).enabled());
    }

    @Test
    void lockedStateKeepsOnlyExactQuestNavigation() {
        EchoScreenModel model = model(
                snapshot(false, false, false, List.of(), List.of()),
                EchoRecommendation.locked(QUEST_ID, OptionalLong.of(0x01L)));

        assertFalse(model.action(Action.SUBMIT).enabled());
        assertFalse(model.action(Action.CLAIM).enabled());
        assertTrue(model.action(Action.PIN).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());
        assertFalse(model.action(Action.ARCHIVE).emphasized());
    }

    @Test
    void completeRouteExposesNoMutationAction() {
        EchoScreenModel model = model(
                snapshot(true, false, true, List.of(), List.of()),
                EchoRecommendation.routeComplete(QUEST_ID));

        assertFalse(model.action(Action.SUBMIT).enabled());
        assertFalse(model.action(Action.CLAIM).enabled());
        assertFalse(model.action(Action.PIN).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());
    }

    @Test
    void pinnedSnapshotProducesTruthfulUnpinState() {
        EchoScreenModel model = model(
                snapshot(false, false, true, List.of(), List.of()),
                EchoRecommendation.locked(QUEST_ID, OptionalLong.empty()));

        assertTrue(model.pinned());
        assertEquals(Component.translatable("screen.afterlight.echo.action.unpin"), model.pinLabel());
    }

    private static EchoScreenModel claimModel(RewardSnapshot reward) {
        return model(
                snapshot(true, false, false, List.of(), List.of(reward)),
                EchoRecommendation.claimReward(QUEST_ID, REWARD_ID, false));
    }

    private static EchoScreenModel model(EchoQuestSnapshot snapshot, EchoRecommendation recommendation) {
        return EchoScreenModel.from(route(), Map.of(QUEST_ID, snapshot), recommendation);
    }

    private static EchoRoute route() {
        return new EchoRoute(1, QUEST_ID, List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID))));
    }

    private static EchoQuestSnapshot snapshot(
            boolean complete,
            boolean startable,
            boolean pinned,
            List<TaskSnapshot> tasks,
            List<RewardSnapshot> rewards) {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                complete,
                startable,
                pinned,
                List.of(),
                tasks,
                rewards);
    }

    private static TaskSnapshot task(
            boolean complete,
            boolean manual,
            boolean direct,
            boolean eligible) {
        return new TaskSnapshot(TASK_ID, "Check the relay", 0L, 1L, complete, manual, direct, eligible);
    }

    private static RewardSnapshot reward(
            boolean claimed,
            boolean choice,
            boolean direct,
            boolean eligible) {
        return new RewardSnapshot(REWARD_ID, "Recovered signal", claimed, choice, direct, eligible);
    }
}
