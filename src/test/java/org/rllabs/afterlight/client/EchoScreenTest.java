package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

class EchoScreenTest {
    private static final long QUEST_ID = 0x11L;

    @Test
    void escapeClosesWithoutChangingProgress() {
        FakeGateway gateway = new FakeGateway();
        TrackingEchoScreen screen = new TrackingEchoScreen(route(), gateway);

        assertTrue(screen.keyPressed(256, 0, 0));

        assertTrue(screen.closed);
        assertEquals(List.of(), gateway.mutations);
    }

    @Test
    void screenDoesNotPauseTheWorld() {
        EchoScreen screen = new EchoScreen(route(), new FakeGateway());

        assertFalse(screen.isPauseScreen());
    }

    private static EchoRoute route() {
        return new EchoRoute(1, QUEST_ID, List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID))));
    }

    private static final class TrackingEchoScreen extends EchoScreen {
        private boolean closed;

        private TrackingEchoScreen(EchoRoute route, EchoQuestGateway gateway) {
            super(route, gateway);
        }

        @Override
        public void onClose() {
            closed = true;
        }
    }

    private static final class FakeGateway implements EchoQuestGateway {
        private final List<String> mutations = new ArrayList<>();

        @Override
        public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
            return Map.of();
        }

        @Override
        public void submit(long taskId) {
            mutations.add("submit");
        }

        @Override
        public void claim(long rewardId) {
            mutations.add("claim");
        }

        @Override
        public void togglePin(long questId) {
            mutations.add("pin");
        }

        @Override
        public void openArchive(long questId) {
            mutations.add("archive");
        }
    }
}
