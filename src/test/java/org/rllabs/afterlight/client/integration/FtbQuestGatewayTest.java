package org.rllabs.afterlight.client.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.rllabs.afterlight.route.EchoRoute;

@ResourceLock("ClientQuestFile.INSTANCE")
class FtbQuestGatewayTest {
    @Test
    void missingSynchronizedDataReturnsNoSnapshotsOrActions() {
        ClientQuestFile previous = ClientQuestFile.INSTANCE;
        ClientQuestFile.INSTANCE = null;
        try {
            var gateway = new FtbQuestGateway();
            var route = new EchoRoute(1, 0x11L, List.of(
                    new EchoRoute.Segment("root", List.of(), List.of(0x11L))));

            assertEquals(Map.of(), gateway.snapshots(route));
            assertDoesNotThrow(() -> gateway.submit(0x21L));
            assertDoesNotThrow(() -> gateway.claim(0x31L));
            assertDoesNotThrow(() -> gateway.openArchive(0x11L));
        } finally {
            ClientQuestFile.INSTANCE = previous;
        }
    }
}
