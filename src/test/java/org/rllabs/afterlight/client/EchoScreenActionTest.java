package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.client.EchoScreenModel.Action;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

class EchoScreenActionTest {
    private static final long QUEST_ID = 0x11L;
    private static final long TASK_ID = 0x21L;
    private static final long REWARD_ID = 0x31L;

    @Test
    void submitDelegatesOnlyToGatewayAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.SUBMIT);
        screen.activate(Action.SUBMIT);

        assertEquals(List.of(TASK_ID), gateway.submissions);
        assertEquals(List.of(), gateway.claims);
        assertEquals(List.of(), gateway.pins);
        assertEquals(List.of(), gateway.archives);
        assertFalse(screen.isActionEnabled(Action.SUBMIT));
    }

    @Test
    void claimDelegatesOnlyToGatewayAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, claimSnapshot()));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.CLAIM);
        screen.activate(Action.CLAIM);

        assertEquals(List.of(REWARD_ID), gateway.claims);
        assertEquals(List.of(), gateway.submissions);
        assertEquals(List.of(), gateway.pins);
        assertEquals(List.of(), gateway.archives);
        assertFalse(screen.isActionEnabled(Action.CLAIM));
    }

    @Test
    void pinDelegatesExactQuestAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.PIN);
        screen.activate(Action.PIN);

        assertEquals(List.of(QUEST_ID), gateway.pins);
        assertFalse(screen.isActionEnabled(Action.PIN));
    }

    @Test
    void archiveDelegatesExactQuestWithoutMutationCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.ARCHIVE);

        assertEquals(List.of(QUEST_ID), gateway.archives);
        assertTrue(screen.isActionEnabled(Action.ARCHIVE));
    }

    @Test
    void synchronizedStateChangeClearsMutationCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.SUBMIT);
        gateway.snapshots = Map.of(QUEST_ID, submitSnapshot(1L));

        screen.tick();

        assertTrue(screen.isActionEnabled(Action.SUBMIT));
    }

    @Test
    void unchangedStateReleasesMutationAfterBoundedCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.SUBMIT);

        for (int tick = 0; tick < 40; tick++) {
            screen.tick();
        }

        assertTrue(screen.isActionEnabled(Action.SUBMIT));
    }

    private static EchoRoute route() {
        return new EchoRoute(1, QUEST_ID, List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID))));
    }

    private static EchoQuestSnapshot submitSnapshot(long currentValue) {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskSnapshot(
                        TASK_ID,
                        "Check the relay",
                        currentValue,
                        2L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
    }

    private static EchoQuestSnapshot claimSnapshot() {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                true,
                false,
                false,
                List.of(),
                List.of(),
                List.of(new RewardSnapshot(REWARD_ID, "Recovered signal", false, false, true, true)));
    }

    private static final class FakeGateway implements EchoQuestGateway {
        private Map<Long, EchoQuestSnapshot> snapshots;
        private final List<Long> submissions = new ArrayList<>();
        private final List<Long> claims = new ArrayList<>();
        private final List<Long> pins = new ArrayList<>();
        private final List<Long> archives = new ArrayList<>();

        private FakeGateway(Map<Long, EchoQuestSnapshot> snapshots) {
            this.snapshots = new LinkedHashMap<>(snapshots);
        }

        @Override
        public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
            return snapshots;
        }

        @Override
        public void submit(long taskId) {
            submissions.add(taskId);
        }

        @Override
        public void claim(long rewardId) {
            claims.add(rewardId);
        }

        @Override
        public void togglePin(long questId) {
            pins.add(questId);
        }

        @Override
        public void openArchive(long questId) {
            archives.add(questId);
        }
    }
}
