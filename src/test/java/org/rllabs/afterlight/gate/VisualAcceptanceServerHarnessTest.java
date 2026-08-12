package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VisualAcceptanceServerHarnessTest {
    @Test
    void authorizedVisualLoginPreservesSurvivalForInventoryCapture() throws Exception {
        String harness = harnessSource();
        int loginStart = harness.indexOf("public static void onPlayerLoggedIn(");
        int scenePreparation = harness.indexOf("private static void prepareScenes(", loginStart);

        assertTrue(loginStart >= 0, "missing visual server login path");
        assertTrue(scenePreparation > loginStart, "missing visual server scene preparation");
        assertFalse(
                harness.substring(loginStart, scenePreparation)
                        .contains("player.setGameMode(GameType.CREATIVE)"),
                "visual server login forces creative before the inventory capture");
    }

    @Test
    void droppedEchoSpawnsWithoutVelocityForStableCapture() throws Exception {
        String harness = harnessSource();
        int itemViewsStart = harness.indexOf("private static void prepareItemViews(");
        int readyMarkerStart = harness.indexOf("private static void writeReadyMarker(", itemViewsStart);

        assertTrue(itemViewsStart >= 0, "missing visual item-view setup");
        assertTrue(readyMarkerStart > itemViewsStart, "missing visual ready-marker boundary");
        String itemViews = harness.substring(itemViewsStart, readyMarkerStart);
        assertTrue(
                itemViews.contains(
                        "new ItemEntity(level, 72.5, 102.2, 0.5, echo.copy(), 0.0, 0.0, 0.0)"),
                "dropped ECHO uses the random-velocity ItemEntity constructor");
    }

    private static String harnessSource() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/gate/VisualAcceptanceServerHarness.java");
        return Files.readString(source);
    }
}
