package org.rllabs.afterlight.client.integration;

import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

public final class FtbQuestGateway implements EchoQuestGateway {
    @Override
    public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
        ClientState state = synchronizedState();
        if (state == null) {
            return Map.of();
        }

        Map<Long, EchoQuestSnapshot> snapshots = new LinkedHashMap<>();
        for (long questId : route.questIds()) {
            Quest quest = state.file().getQuest(questId);
            if (quest != null) {
                snapshots.put(questId, snapshot(quest, state));
            }
        }
        return Collections.unmodifiableMap(snapshots);
    }

    @Override
    public void submit(long taskId) {
        ClientState state = synchronizedState();
        if (state == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        Task task = state.file().getTask(taskId);
        if (task != null && isDirectManualTask(task)
                && state.teamData().canStartTasks(task.getQuest())
                && !state.teamData().isCompleted(task)) {
            PacketDistributor.sendToServer(new SubmitTaskMessage(taskId));
        }
    }

    @Override
    public void claim(long rewardId) {
        ClientState state = synchronizedState();
        if (state == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        Reward reward = state.file().getReward(rewardId);
        if (reward != null && isDirectReward(reward)
                && !state.teamData().isRewardBlocked(reward)
                && state.teamData().getClaimType(state.player().getUUID(), reward).canClaim()) {
            PacketDistributor.sendToServer(new ClaimRewardMessage(rewardId, true));
        }
    }

    @Override
    public void openArchive(long questId) {
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        if (file != null && file.getQuest(questId) != null) {
            ClientQuestFile.openBookToQuestObject(questId);
        }
    }

    private static EchoQuestSnapshot snapshot(Quest quest, ClientState state) {
        TeamData teamData = state.teamData();
        boolean startable = teamData.canStartTasks(quest);
        List<Long> unmetDependencyIds = quest.streamDependencies()
                .filter(dependency -> !teamData.isCompleted(dependency))
                .map(QuestObject::getId)
                .toList();
        List<TaskSnapshot> tasks = quest.getTasks().stream()
                .map(task -> taskSnapshot(task, teamData, startable))
                .toList();
        List<RewardSnapshot> rewards = quest.getRewards().stream()
                .map(reward -> rewardSnapshot(reward, state))
                .toList();

        return new EchoQuestSnapshot(
                quest.getId(),
                quest.getTitle().getString(),
                quest.getSubtitle().getString(),
                teamData.isCompleted(quest),
                startable,
                unmetDependencyIds,
                tasks,
                rewards);
    }

    private static TaskSnapshot taskSnapshot(Task task, TeamData teamData, boolean startable) {
        boolean complete = teamData.isCompleted(task);
        boolean manualSubmit = isDirectManualTask(task);
        return new TaskSnapshot(
                task.getId(),
                task.getTitle().getString(),
                teamData.getProgress(task),
                task.getMaxProgress(),
                complete,
                manualSubmit,
                manualSubmit && startable && !complete && task.isValid());
    }

    private static RewardSnapshot rewardSnapshot(Reward reward, ClientState state) {
        boolean choice = reward instanceof ChoiceReward;
        boolean claimed = state.teamData().isRewardClaimed(state.player().getUUID(), reward);
        boolean claimEligible = !choice
                && isDirectReward(reward)
                && !state.teamData().isRewardBlocked(reward)
                && state.teamData().getClaimType(state.player().getUUID(), reward).canClaim();
        return new RewardSnapshot(
                reward.getId(),
                reward.getTitle().getString(),
                claimed,
                choice,
                claimEligible);
    }

    private static boolean isDirectManualTask(Task task) {
        return task.autoSubmitOnPlayerTick() <= 0 && declaresClickHandler(task, Task.class);
    }

    private static boolean isDirectReward(Reward reward) {
        return !(reward instanceof ChoiceReward) && declaresClickHandler(reward, Reward.class);
    }

    private static boolean declaresClickHandler(Object interaction, Class<?> supportedOwner) {
        try {
            Method method = interaction.getClass().getMethod("onButtonClicked", Button.class, boolean.class);
            return method.getDeclaringClass() == supportedOwner;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }

    private static ClientState synchronizedState() {
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
        if (teamData == null || teamData.isLocked()) {
            return null;
        }
        return new ClientState(file, teamData, player);
    }

    private record ClientState(ClientQuestFile file, TeamData teamData, LocalPlayer player) {
    }
}
