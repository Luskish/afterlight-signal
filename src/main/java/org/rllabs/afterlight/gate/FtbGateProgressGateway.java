package org.rllabs.afterlight.gate;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

public final class FtbGateProgressGateway implements GateProgressGateway {
    @Override
    public boolean completed(ServerPlayer player, long taskId) {
        Objects.requireNonNull(player, "player");
        return ServerQuestFile.getInstance()
                .filter(file -> file.server == player.server)
                .map(file -> completed(file, player, taskId))
                .orElse(false);
    }

    private static boolean completed(
            ServerQuestFile file,
            ServerPlayer player,
            long taskId) {
        Task task = file.getTask(taskId);
        return task != null
                && file.getTeamData(player)
                        .map(teamData -> teamData.isCompleted(task))
                        .orElse(false);
    }
}
