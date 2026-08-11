package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.visual.VisualSceneCatalog;

class VisualAcceptanceHarnessTest {
    @Test
    void harnessRequiresTheExactExplicitJvmOptIn() {
        assertFalse(VisualAcceptanceHarness.enabled(null));
        assertFalse(VisualAcceptanceHarness.enabled("false"));
        assertFalse(VisualAcceptanceHarness.enabled("TRUE"));
        assertTrue(VisualAcceptanceHarness.enabled("true"));
    }

    @Test
    void capturePlanCoversEveryRequiredProductionView() {
        assertEquals(
                List.of(
                        "title-1920x1080.png",
                        "title-3440x1440.png",
                        "title-854x480.png",
                        "echo-wide.png",
                        "echo-standard.png",
                        "echo-compact.png",
                        "echo-minimal.png",
                        "echo-item-gui.png",
                        "echo-item-first-person.png",
                        "echo-item-third-person.png",
                        "echo-item-dropped.png",
                        "echo-item-frame.png",
                        "gate-idle.png",
                        "gate-open.png",
                        "gate-fault.png",
                        "far-relay-arrival.png",
                        "far-relay-central.png",
                        "far-relay-east.png",
                        "far-relay-west.png",
                        "far-relay-north.png",
                        "far-relay-south.png",
                        "far-relay-return.png"),
                VisualAcceptanceHarness.expectedArtifacts());
        assertEquals(4_800, VisualAcceptanceHarness.timeoutTicks());
        assertEquals(12, VisualAcceptanceHarness.stableSceneTicks());
    }

    @Test
    void everyWorldArtifactHasAnExactSceneContract() {
        assertEquals(
                VisualAcceptanceHarness.expectedArtifacts().subList(7, 22),
                VisualSceneCatalog.worldScenes().stream()
                        .map(VisualSceneCatalog.WorldScene::artifact)
                        .toList());
        VisualSceneCatalog.worldScenes().forEach(scene -> {
            assertTrue(scene.coordinateTolerance() <= 0.05, scene.artifact());
            assertFalse(scene.anchorRequirements().isEmpty(), scene.artifact());
            assertFalse(scene.requiredChunks().isEmpty(), scene.artifact());
        });
    }
}
