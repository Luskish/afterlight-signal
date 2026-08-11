package org.rllabs.afterlight.integration;

import java.util.Map;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

public interface EchoQuestGateway {
    Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route);

    void submit(long taskId);

    void claim(long rewardId);

    void togglePin(long questId);

    void openArchive(long questId);
}
