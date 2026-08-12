package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VisualAcceptanceServerHarnessTest {
    @Test
    void authorizedVisualLoginPreservesSurvivalForInventoryCapture() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/gate/VisualAcceptanceServerHarness.java");
        String harness = Files.readString(source);
        int loginStart = harness.indexOf("public static void onPlayerLoggedIn(");
        int scenePreparation = harness.indexOf("private static void prepareScenes(", loginStart);

        assertTrue(loginStart >= 0, "missing visual server login path");
        assertTrue(scenePreparation > loginStart, "missing visual server scene preparation");
        assertFalse(
                harness.substring(loginStart, scenePreparation)
                        .contains("player.setGameMode(GameType.CREATIVE)"),
                "visual server login forces creative before the inventory capture");
    }
}
