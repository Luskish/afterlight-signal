package org.rllabs.afterlight.client.integration;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import dev.ftb.mods.ftbquests.net.TogglePinnedMessage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

public final class FtbQuestGateway implements EchoQuestGateway {
    private final ClientAccess access;

    public FtbQuestGateway() {
        this(new FtbClientAccess());
    }

    FtbQuestGateway(ClientAccess access) {
        this.access = Objects.requireNonNull(access);
    }

    @Override
    public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
        Objects.requireNonNull(route);
        SynchronizedState state = access.synchronizedState();
        if (state == null || state.locked()) {
            return Map.of();
        }

        Map<Long, EchoQuestSnapshot> snapshots = new LinkedHashMap<>();
        for (long questId : route.questIds()) {
            QuestState quest = state.quest(questId);
            if (quest != null) {
                snapshots.put(questId, snapshot(quest));
            }
        }
        return Collections.unmodifiableMap(snapshots);
    }

    @Override
    public void submit(long taskId) {
        SynchronizedState state = access.synchronizedState();
        if (state == null || state.locked() || !access.connected()) {
            return;
        }
        TaskState task = state.task(taskId);
        if (task != null
                && task.autoSubmitOnPlayerTick() <= 0
                && task.directInteractionSupported()
                && task.submitEligible()
                && !task.complete()) {
            access.send(new SubmitTaskMessage(taskId));
        }
    }

    @Override
    public void claim(long rewardId) {
        SynchronizedState state = access.synchronizedState();
        if (state == null || state.locked() || !access.connected()) {
            return;
        }
        RewardState reward = state.reward(rewardId);
        if (reward != null
                && !reward.claimed()
                && !reward.choice()
                && reward.directInteractionSupported()
                && reward.claimEligible()) {
            access.send(new ClaimRewardMessage(rewardId, true));
        }
    }

    @Override
    public void togglePin(long questId) {
        SynchronizedState state = access.synchronizedState();
        if (state == null || state.locked() || !access.connected() || state.quest(questId) == null) {
            return;
        }
        access.send(new TogglePinnedMessage(questId));
    }

    @Override
    public void openArchive(long questId) {
        SynchronizedState state = access.synchronizedState();
        if (state != null && !state.locked() && state.quest(questId) != null) {
            access.openArchive(questId);
        }
    }

    @Override
    public void openArchive() {
        SynchronizedState state = access.synchronizedState();
        if (state != null && !state.locked()) {
            access.openArchive();
        }
    }

    private static EchoQuestSnapshot snapshot(QuestState quest) {
        List<TaskSnapshot> tasks = quest.tasks().stream()
                .map(FtbQuestGateway::taskSnapshot)
                .toList();
        List<RewardSnapshot> rewards = quest.rewards().stream()
                .map(FtbQuestGateway::rewardSnapshot)
                .toList();
        return new EchoQuestSnapshot(
                quest.id(),
                quest.title(),
                quest.subtitle(),
                quest.teamComplete(),
                quest.startable(),
                quest.pinned(),
                quest.unmetDependencyIds(),
                tasks,
                rewards);
    }

    private static TaskSnapshot taskSnapshot(TaskState task) {
        boolean manualSubmit = task.autoSubmitOnPlayerTick() <= 0;
        boolean submitEligible = manualSubmit
                && task.directInteractionSupported()
                && task.submitEligible()
                && !task.complete();
        return new TaskSnapshot(
                task.id(),
                task.title(),
                task.currentValue(),
                task.requiredValue(),
                task.complete(),
                manualSubmit,
                task.directInteractionSupported(),
                submitEligible);
    }

    private static RewardSnapshot rewardSnapshot(RewardState reward) {
        boolean claimEligible = !reward.claimed()
                && !reward.choice()
                && reward.directInteractionSupported()
                && reward.claimEligible();
        return new RewardSnapshot(
                reward.id(),
                reward.title(),
                reward.claimed(),
                reward.choice(),
                reward.directInteractionSupported(),
                claimEligible);
    }

    interface ClientAccess {
        SynchronizedState synchronizedState();

        boolean connected();

        void send(SubmitTaskMessage message);

        void send(ClaimRewardMessage message);

        void send(TogglePinnedMessage message);

        void openArchive();

        void openArchive(long questId);
    }

    interface SynchronizedState {
        boolean locked();

        QuestState quest(long questId);

        TaskState task(long taskId);

        RewardState reward(long rewardId);
    }

    record QuestState(
            long id,
            String title,
            String subtitle,
            boolean teamComplete,
            boolean startable,
            boolean pinned,
            List<Long> unmetDependencyIds,
            List<TaskState> tasks,
            List<RewardState> rewards) {
        QuestState {
            title = Objects.requireNonNull(title);
            subtitle = Objects.requireNonNull(subtitle);
            unmetDependencyIds = List.copyOf(Objects.requireNonNull(unmetDependencyIds));
            tasks = List.copyOf(Objects.requireNonNull(tasks));
            rewards = List.copyOf(Objects.requireNonNull(rewards));
        }

        QuestState(
                long id,
                String title,
                String subtitle,
                boolean teamComplete,
                boolean startable,
                List<Long> unmetDependencyIds,
                List<TaskState> tasks,
                List<RewardState> rewards) {
            this(id, title, subtitle, teamComplete, startable, false, unmetDependencyIds, tasks, rewards);
        }
    }

    record TaskState(
            long id,
            String title,
            long currentValue,
            long requiredValue,
            boolean complete,
            int autoSubmitOnPlayerTick,
            boolean directInteractionSupported,
            boolean submitEligible) {
        TaskState {
            title = Objects.requireNonNull(title);
        }
    }

    record RewardState(
            long id,
            String title,
            boolean claimed,
            boolean choice,
            boolean directInteractionSupported,
            boolean claimEligible) {
        RewardState {
            title = Objects.requireNonNull(title);
        }
    }

    private static final class FtbClientAccess implements ClientAccess {
        @Override
        public SynchronizedState synchronizedState() {
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            if (file == null || !ClientQuestFile.exists()) {
                return null;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft == null ? null : minecraft.player;
            if (player == null) {
                return null;
            }
            TeamData teamData = ClientQuestFile.INSTANCE.selfTeamData;
            if (teamData == null) {
                return null;
            }
            return new FtbSynchronizedState(file, teamData, player);
        }

        @Override
        public boolean connected() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.getConnection() != null;
        }

        @Override
        public void send(SubmitTaskMessage message) {
            NetworkManager.sendToServer(message);
        }

        @Override
        public void send(ClaimRewardMessage message) {
            NetworkManager.sendToServer(message);
        }

        @Override
        public void send(TogglePinnedMessage message) {
            NetworkManager.sendToServer(message);
        }

        @Override
        public void openArchive(long questId) {
            ClientQuestFile.openBookToQuestObject(questId);
        }

        @Override
        public void openArchive() {
            ClientQuestFile.openGui();
        }
    }

    private static final class FtbSynchronizedState implements SynchronizedState {
        private final ClientQuestFile file;
        private final TeamData teamData;
        private final LocalPlayer player;

        private FtbSynchronizedState(ClientQuestFile file, TeamData teamData, LocalPlayer player) {
            this.file = file;
            this.teamData = teamData;
            this.player = player;
        }

        @Override
        public boolean locked() {
            return teamData.isLocked();
        }

        @Override
        public QuestState quest(long questId) {
            Quest quest = file.getQuest(questId);
            if (quest == null) {
                return null;
            }
            boolean startable = teamData.canStartTasks(quest);
            List<Long> unmetDependencyIds = quest.streamDependencies()
                    .filter(dependency -> !teamData.isCompleted(dependency))
                    .map(QuestObject::getId)
                    .toList();
            List<TaskState> tasks = quest.getTasks().stream()
                    .map(task -> taskState(task, startable))
                    .toList();
            List<RewardState> rewards = quest.getRewards().stream()
                    .map(this::rewardState)
                    .toList();
            return new QuestState(
                    quest.getId(),
                    quest.getTitle().getString(),
                    quest.getSubtitle().getString(),
                    teamData.isCompleted(quest),
                    startable,
                    teamData.isQuestPinned(player, quest.getId()),
                    unmetDependencyIds,
                    tasks,
                    rewards);
        }

        @Override
        public TaskState task(long taskId) {
            Task task = file.getTask(taskId);
            return task == null ? null : taskState(task, teamData.canStartTasks(task.getQuest()));
        }

        @Override
        public RewardState reward(long rewardId) {
            Reward reward = file.getReward(rewardId);
            return reward == null ? null : rewardState(reward);
        }

        private TaskState taskState(Task task, boolean startable) {
            boolean complete = teamData.isCompleted(task);
            return new TaskState(
                    task.getId(),
                    task.getTitle().getString(),
                    teamData.getProgress(task),
                    task.getMaxProgress(),
                    complete,
                    task.autoSubmitOnPlayerTick(),
                    declaresClickHandler(task, Task.class),
                    startable && !complete && task.isValid());
        }

        private RewardState rewardState(Reward reward) {
            boolean choice = reward instanceof ChoiceReward;
            boolean claimed = teamData.isRewardClaimed(player.getUUID(), reward);
            return new RewardState(
                    reward.getId(),
                    reward.getTitle().getString(),
                    claimed,
                    choice,
                    !choice && declaresClickHandler(reward, Reward.class),
                    !teamData.isRewardBlocked(reward)
                            && teamData.getClaimType(player.getUUID(), reward).canClaim());
        }
    }

    private static boolean declaresClickHandler(Object interaction, Class<?> supportedOwner) {
        try {
            Method method = interaction.getClass().getMethod("onButtonClicked", Button.class, boolean.class);
            return method.getDeclaringClass() == supportedOwner;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }
}
