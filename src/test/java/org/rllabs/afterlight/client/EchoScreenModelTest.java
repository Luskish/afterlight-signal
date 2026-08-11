package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
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
    void signalMissingDisablesMutationActionsAndKeepsArchive() {
        EchoScreenModel model = EchoScreenModel.from(
                route(), Map.of(), EchoRecommendation.signalUnavailable(QUEST_ID));

        assertFalse(model.action(Action.SUBMIT).enabled());
        assertFalse(model.action(Action.CLAIM).enabled());
        assertFalse(model.action(Action.PIN).enabled());
        assertTrue(model.action(Action.ARCHIVE).enabled());
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

    @Test
    void exposesHeaderPrerequisitesTasksExactValuesAndCompletionState() throws Exception {
        long firstQuestId = 0x10L;
        TaskSnapshot selectedTask = new TaskSnapshot(
                TASK_ID,
                "Check the relay",
                2L,
                5L,
                false,
                true,
                true,
                true);
        TaskSnapshot completedTask = new TaskSnapshot(
                0x22L,
                "Stabilize the carrier",
                1L,
                1L,
                true,
                false,
                true,
                false);
        EchoRoute route = new EchoRoute(
                1,
                QUEST_ID,
                List.of(
                        new EchoRoute.Segment("act_i", List.of(), List.of(firstQuestId)),
                        new EchoRoute.Segment("act_ii", List.of("act_i"), List.of(QUEST_ID))));
        EchoQuestSnapshot first = new EchoQuestSnapshot(
                firstQuestId,
                "Cold Boot",
                "Signal restored",
                true,
                false,
                false,
                List.of(),
                List.of(),
                List.of());
        EchoQuestSnapshot selected = new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                false,
                List.of(0x01L),
                List.of(selectedTask, completedTask),
                List.of());

        EchoScreenModel model = EchoScreenModel.from(
                route,
                Map.of(firstQuestId, first, QUEST_ID, selected),
                EchoRecommendation.submitTask(QUEST_ID, OptionalLong.of(TASK_ID), false));

        assertEquals("ACT // ACT II", component(model, "currentAct").getString());
        assertEquals("MEMORY // 1", component(model, "memoryCount").getString());
        assertEquals(
                List.of("PREREQUISITE // 0000000000000001"),
                components(model, "prerequisites").stream().map(Component::getString).toList());
        assertEquals(
                List.of(
                        "TASK // Check the relay // 2 / 5 // INCOMPLETE",
                        "TASK // Stabilize the carrier // 1 / 1 // COMPLETE"),
                components(model, "tasks").stream().map(Component::getString).toList());
        assertEquals("TASK VALUE // 2 / 5", component(model, "progressValue").getString());
        assertEquals("TASK INCOMPLETE", component(model, "completionState").getString());
    }

    private static Component component(EchoScreenModel model, String methodName) throws Exception {
        Method method = EchoScreenModel.class.getMethod(methodName);
        return (Component) method.invoke(model);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> components(EchoScreenModel model, String methodName) throws Exception {
        Method method = EchoScreenModel.class.getMethod(methodName);
        return (List<Component>) method.invoke(model);
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
