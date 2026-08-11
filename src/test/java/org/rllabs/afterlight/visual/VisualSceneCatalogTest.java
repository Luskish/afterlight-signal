package org.rllabs.afterlight.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.gate.GateState;
import org.rllabs.afterlight.relay.RelaySite;

class VisualSceneCatalogTest {
    @Test
    void catalogPinsEveryWorldCameraAndSceneSpecificAnchor() {
        assertEquals(
                List.of(
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
                VisualSceneCatalog.worldScenes().stream()
                        .map(VisualSceneCatalog.WorldScene::artifact)
                        .toList());
        VisualSceneCatalog.worldScenes().forEach(scene -> {
            assertNotNull(scene.dimension(), scene.artifact());
            assertTrue(scene.requiredChunks().contains(scene.cameraChunk()), scene.artifact());
            assertTrue(scene.anchorRequirements().stream()
                    .map(VisualSceneCatalog.AnchorRequirement::name)
                    .distinct()
                    .count() == scene.anchorRequirements().size(), scene.artifact());
        });
    }

    @Test
    void gatesAndRelaySitesCarryTheirExactSemanticState() {
        assertEquals(GateState.IDLE, VisualSceneCatalog.scene("gate-idle.png").gateState());
        assertEquals(GateState.OPEN, VisualSceneCatalog.scene("gate-open.png").gateState());
        assertEquals(GateState.FAULT, VisualSceneCatalog.scene("gate-fault.png").gateState());
        assertEquals(GateState.OPEN, VisualSceneCatalog.scene("far-relay-return.png").gateState());
        assertEquals(
                RelaySite.CENTRAL,
                VisualSceneCatalog.scene("far-relay-central.png").relaySite());
        assertEquals(RelaySite.EAST, VisualSceneCatalog.scene("far-relay-east.png").relaySite());
        assertEquals(RelaySite.WEST, VisualSceneCatalog.scene("far-relay-west.png").relaySite());
        assertEquals(RelaySite.NORTH, VisualSceneCatalog.scene("far-relay-north.png").relaySite());
        assertEquals(RelaySite.SOUTH, VisualSceneCatalog.scene("far-relay-south.png").relaySite());
    }

    @Test
    void groundedCreativeScenesAndSpectatorReturnUseStableExactHeights() {
        assertEquals(101.0, VisualSceneCatalog.scene("echo-item-gui.png").y());
        assertEquals(101.0, VisualSceneCatalog.scene("gate-idle.png").y());
        assertEquals(101.0, VisualSceneCatalog.scene("gate-open.png").y());
        assertEquals(101.0, VisualSceneCatalog.scene("gate-fault.png").y());
        assertEquals(103.0, VisualSceneCatalog.scene("far-relay-return.png").y());
    }

    @Test
    void eachSatelliteCameraFacesTheTerminalFrontAndBlackboxSilhouette() {
        for (RelaySite site : RelaySite.values()) {
            if (site == RelaySite.CENTRAL) {
                continue;
            }
            Direction front = site.directionTowardCenter();
            VisualSceneCatalog.WorldScene scene = VisualSceneCatalog.scene(
                    "far-relay-" + site.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            assertEquals(site.x() + 0.5 + front.getStepX() * 15.0, scene.x());
            assertEquals(site.z() + 0.5 + front.getStepZ() * 15.0, scene.z());
        }
    }
}
