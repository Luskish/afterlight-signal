package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void firstPersonCaptureKeepsTheProductionHandRendererEnabled() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/client/VisualAcceptanceHarness.java");
        String harness = Files.readString(source);

        assertTrue(harness.contains("minecraft.options.hideGui = false;"));
        assertFalse(harness.contains("minecraft.options.hideGui = true;"));
    }
}
