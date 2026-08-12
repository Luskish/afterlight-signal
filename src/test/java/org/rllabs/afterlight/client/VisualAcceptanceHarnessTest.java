package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void inventoryCapturePlanNeverEntersCreativeMode() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/client/VisualAcceptanceHarness.java");
        String harness = Files.readString(source);
        int planStart = harness.indexOf("private List<Step> buildSteps()");
        int inventoryRequirement = harness.indexOf(
                "addScreenAwait(planned, 1920, 1080, InventoryScreen.class)", planStart);

        assertTrue(planStart >= 0, "missing visual capture plan");
        assertTrue(inventoryRequirement > planStart, "missing exact inventory screen requirement");
        assertFalse(
                harness.substring(planStart, inventoryRequirement).contains("gamemode creative"),
                "visual capture enters creative mode before requiring InventoryScreen");
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

    @Test
    void worldSceneReadinessUsesCompiledRendererSectionsAndRecordsTheResult() throws Exception {
        Path root = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize();
        String probe = Files.readString(root.resolve(
                "src/test/java/org/rllabs/afterlight/client/VisualSceneProbe.java"));
        String harness = Files.readString(root.resolve(
                "src/test/java/org/rllabs/afterlight/client/VisualAcceptanceHarness.java"));

        assertTrue(
                probe.contains("minecraft.levelRenderer.hasRenderedAllSections()"),
                "scene readiness ignores the renderer compile queue");
        assertTrue(
                probe.contains("minecraft.levelRenderer.isSectionCompiled("),
                "scene readiness ignores anchor render-section compilation");
        assertTrue(
                harness.contains("sceneJson.addProperty(\"renderer_ready\", scene.rendererReady())"),
                "visual manifest omits renderer readiness evidence");
    }
}
