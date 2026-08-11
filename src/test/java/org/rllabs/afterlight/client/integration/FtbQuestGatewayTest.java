package org.rllabs.afterlight.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import dev.ftb.mods.ftbquests.net.TogglePinnedMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.client.integration.FtbQuestGateway.ClientAccess;
import org.rllabs.afterlight.client.integration.FtbQuestGateway.QuestState;
import org.rllabs.afterlight.client.integration.FtbQuestGateway.RewardState;
import org.rllabs.afterlight.client.integration.FtbQuestGateway.SynchronizedState;
import org.rllabs.afterlight.client.integration.FtbQuestGateway.TaskState;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

class FtbQuestGatewayTest {
    private static final long QUEST_ID = 0x11L;
    private static final long COMPLETE_QUEST_ID = 0x12L;
    private static final long TASK_ID = 0x21L;
    private static final long REWARD_ID = 0x31L;

    @Test
    void projectsEverySynchronizedQuestFieldAndInteractionGate() {
        var task = new TaskState(TASK_ID, "Check the relay", 2L, 5L, false, 0, true, true);
        var overriddenManualTask = new TaskState(0x22L, "Choose an input", 1L, 1L, false, -1, false, true);
        var automaticTask = new TaskState(0x23L, "Listen", 0L, 1L, false, 20, true, true);
        var reward = new RewardState(REWARD_ID, "Recovered signal", false, false, true, true);
        var choice = new RewardState(0x32L, "Choose a memory", false, true, false, true);
        var unsupported = new RewardState(0x33L, "Custom response", false, false, false, true);
        var blocked = new RewardState(0x34L, "Blocked response", false, false, true, false);
        var state = new FakeSynchronizedState(false);
        state.add(new QuestState(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                true,
                List.of(0x01L, 0x02L),
                List.of(task, overriddenManualTask, automaticTask),
                List.of(reward, choice, unsupported, blocked)));
        state.add(new QuestState(
                COMPLETE_QUEST_ID,
                "Carrier Restored",
                "The channel is stable",
                true,
                false,
                false,
                List.of(),
                List.of(),
                List.of()));
        var access = new FakeClientAccess(state);

        Map<Long, EchoQuestSnapshot> snapshots = new FtbQuestGateway(access).snapshots(route());

        assertEquals(List.of(QUEST_ID, COMPLETE_QUEST_ID), List.copyOf(snapshots.keySet()));
        EchoQuestSnapshot snapshot = snapshots.get(QUEST_ID);
        assertEquals(QUEST_ID, snapshot.questId());
        assertEquals("Signal Trace", snapshot.title());
        assertEquals("Recover the missing carrier", snapshot.subtitle());
        assertFalse(snapshot.teamComplete());
        assertTrue(snapshot.startable());
        assertTrue(snapshot.pinned());
        assertEquals(List.of(0x01L, 0x02L), snapshot.unmetDependencyIds());
        assertEquals(List.of(
                new TaskSnapshot(TASK_ID, "Check the relay", 2L, 5L, false, true, true, true),
                new TaskSnapshot(0x22L, "Choose an input", 1L, 1L, false, true, false, false),
                new TaskSnapshot(0x23L, "Listen", 0L, 1L, false, false, true, false)), snapshot.tasks());
        assertEquals(List.of(
                new RewardSnapshot(REWARD_ID, "Recovered signal", false, false, true, true),
                new RewardSnapshot(0x32L, "Choose a memory", false, true, false, false),
                new RewardSnapshot(0x33L, "Custom response", false, false, false, false),
                new RewardSnapshot(0x34L, "Blocked response", false, false, true, false)), snapshot.rewards());
        assertTrue(snapshots.get(COMPLETE_QUEST_ID).teamComplete());
        assertFalse(snapshots.get(COMPLETE_QUEST_ID).startable());
    }

    @Test
    void missingSynchronizedStateReturnsNoSnapshotsOrMutations() {
        var access = new FakeClientAccess(null);
        var gateway = new FtbQuestGateway(access);

        assertEquals(Map.of(), gateway.snapshots(route()));
        gateway.submit(TASK_ID);
        gateway.claim(REWARD_ID);
        gateway.togglePin(QUEST_ID);
        gateway.openArchive(QUEST_ID);

        assertEquals(List.of(), access.submissions);
        assertEquals(List.of(), access.claims);
        assertEquals(List.of(), access.pins);
        assertEquals(List.of(), access.openedArchiveQuests);
    }

    @Test
    void lockedSynchronizedStateReturnsNoSnapshotsOrMutations() {
        var state = stateWithEligibleObjects(true);
        var access = new FakeClientAccess(state);
        var gateway = new FtbQuestGateway(access);

        assertEquals(Map.of(), gateway.snapshots(route()));
        gateway.submit(TASK_ID);
        gateway.claim(REWARD_ID);
        gateway.togglePin(QUEST_ID);
        gateway.openArchive(QUEST_ID);

        assertEquals(List.of(), access.submissions);
        assertEquals(List.of(), access.claims);
        assertEquals(List.of(), access.pins);
        assertEquals(List.of(), access.openedArchiveQuests);
    }

    @Test
    void submitDispatchesTheExactFtbMessageForAnEligibleDirectManualTask() {
        var access = new FakeClientAccess(stateWithEligibleObjects(false));

        new FtbQuestGateway(access).submit(TASK_ID);

        assertEquals(List.of(new SubmitTaskMessage(TASK_ID)), access.submissions);
    }

    @Test
    void submitRejectsMissingAutomaticUnsupportedIneligibleAndDisconnectedTasks() {
        var state = stateWithEligibleObjects(false);
        state.tasks.put(0x22L, new TaskState(0x22L, "Automatic", 0L, 1L, false, 20, true, true));
        state.tasks.put(0x23L, new TaskState(0x23L, "Unsupported", 0L, 1L, false, 0, false, true));
        state.tasks.put(0x24L, new TaskState(0x24L, "Ineligible", 0L, 1L, false, 0, true, false));
        var access = new FakeClientAccess(state);
        var gateway = new FtbQuestGateway(access);

        gateway.submit(0x20L);
        gateway.submit(0x22L);
        gateway.submit(0x23L);
        gateway.submit(0x24L);
        access.connected = false;
        gateway.submit(TASK_ID);

        assertEquals(List.of(), access.submissions);
    }

    @Test
    void claimDispatchesTheExactFtbMessageForAnEligibleDirectReward() {
        var access = new FakeClientAccess(stateWithEligibleObjects(false));

        new FtbQuestGateway(access).claim(REWARD_ID);

        assertEquals(List.of(new ClaimRewardMessage(REWARD_ID, true)), access.claims);
    }

    @Test
    void claimRejectsMissingBlockedChoiceUnsupportedAndDisconnectedRewards() {
        var state = stateWithEligibleObjects(false);
        state.rewards.put(0x32L, new RewardState(0x32L, "Blocked", false, false, true, false));
        state.rewards.put(0x33L, new RewardState(0x33L, "Choice", false, true, false, true));
        state.rewards.put(0x34L, new RewardState(0x34L, "Unsupported", false, false, false, true));
        var access = new FakeClientAccess(state);
        var gateway = new FtbQuestGateway(access);

        gateway.claim(0x30L);
        gateway.claim(0x32L);
        gateway.claim(0x33L);
        gateway.claim(0x34L);
        access.connected = false;
        gateway.claim(REWARD_ID);

        assertEquals(List.of(), access.claims);
    }

    @Test
    void archiveOpensTheExactExistingQuestAndIgnoresMissingObjects() {
        var access = new FakeClientAccess(stateWithEligibleObjects(false));
        var gateway = new FtbQuestGateway(access);

        gateway.openArchive(QUEST_ID);
        gateway.openArchive(COMPLETE_QUEST_ID);

        assertEquals(List.of(QUEST_ID), access.openedArchiveQuests);
    }

    @Test
    void togglePinDispatchesTheExactFtbMessageForAnExistingSynchronizedQuest() {
        var access = new FakeClientAccess(stateWithEligibleObjects(false));

        new FtbQuestGateway(access).togglePin(QUEST_ID);

        assertEquals(List.of(new TogglePinnedMessage(QUEST_ID)), access.pins);
    }

    @Test
    void togglePinRejectsMissingLockedAndDisconnectedQuests() {
        var state = stateWithEligibleObjects(false);
        var access = new FakeClientAccess(state);
        var gateway = new FtbQuestGateway(access);

        gateway.togglePin(COMPLETE_QUEST_ID);
        access.connected = false;
        gateway.togglePin(QUEST_ID);
        new FtbQuestGateway(new FakeClientAccess(stateWithEligibleObjects(true))).togglePin(QUEST_ID);
        new FtbQuestGateway(new FakeClientAccess(null)).togglePin(QUEST_ID);

        assertEquals(List.of(), access.pins);
    }

    private static EchoRoute route() {
        return new EchoRoute(1, COMPLETE_QUEST_ID, List.of(
                new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID, COMPLETE_QUEST_ID))));
    }

    private static FakeSynchronizedState stateWithEligibleObjects(boolean locked) {
        var state = new FakeSynchronizedState(locked);
        state.add(new QuestState(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskState(TASK_ID, "Check the relay", 0L, 1L, false, 0, true, true)),
                List.of(new RewardState(REWARD_ID, "Recovered signal", false, false, true, true))));
        return state;
    }

    private static final class FakeSynchronizedState implements SynchronizedState {
        private final boolean locked;
        private final Map<Long, QuestState> quests = new LinkedHashMap<>();
        private final Map<Long, TaskState> tasks = new LinkedHashMap<>();
        private final Map<Long, RewardState> rewards = new LinkedHashMap<>();

        private FakeSynchronizedState(boolean locked) {
            this.locked = locked;
        }

        private void add(QuestState quest) {
            quests.put(quest.id(), quest);
            quest.tasks().forEach(task -> tasks.put(task.id(), task));
            quest.rewards().forEach(reward -> rewards.put(reward.id(), reward));
        }

        @Override
        public boolean locked() {
            return locked;
        }

        @Override
        public QuestState quest(long questId) {
            return quests.get(questId);
        }

        @Override
        public TaskState task(long taskId) {
            return tasks.get(taskId);
        }

        @Override
        public RewardState reward(long rewardId) {
            return rewards.get(rewardId);
        }
    }

    private static final class FakeClientAccess implements ClientAccess {
        private final SynchronizedState state;
        private final List<SubmitTaskMessage> submissions = new ArrayList<>();
        private final List<ClaimRewardMessage> claims = new ArrayList<>();
        private final List<TogglePinnedMessage> pins = new ArrayList<>();
        private final List<Long> openedArchiveQuests = new ArrayList<>();
        private boolean connected = true;

        private FakeClientAccess(SynchronizedState state) {
            this.state = state;
        }

        @Override
        public SynchronizedState synchronizedState() {
            return state;
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public void send(SubmitTaskMessage message) {
            submissions.add(message);
        }

        @Override
        public void send(ClaimRewardMessage message) {
            claims.add(message);
        }

        @Override
        public void send(TogglePinnedMessage message) {
            pins.add(message);
        }

        @Override
        public void openArchive(long questId) {
            openedArchiveQuests.add(questId);
        }
    }
}
