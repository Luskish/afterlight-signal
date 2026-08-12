package org.rllabs.afterlight.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;
import org.rllabs.afterlight.visual.VisualSceneReadiness.ExpectedScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Failure;
import org.rllabs.afterlight.visual.VisualSceneReadiness.ObservedScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness.SceneStability;

class VisualSceneReadinessTest {
    private static final ExpectedScene EXPECTED = new ExpectedScene(
            "afterlight:far_relay",
            15.5,
            82.0,
            15.5,
            0.05,
            Set.of("0,0", "1,0"),
            Set.of("arrival_floor", "return_terminal", "loot_chest"),
            "OPEN");
    private static final ObservedScene READY = new ObservedScene(
            "afterlight:far_relay",
            15.5,
            82.0,
            15.5,
            Map.of("0,0", true, "1,0", true),
            Map.of("arrival_floor", true, "return_terminal", true, "loot_chest", true),
            true,
            "OPEN");

    @Test
    void exactDimensionCoordinatesChunksAnchorsRendererAndGateStateAreRequired() {
        Evaluation evaluation = VisualSceneReadiness.evaluate(EXPECTED, READY);

        assertTrue(evaluation.ready());
        assertEquals(Set.of(), evaluation.failures());

        assertFailure(
                new ObservedScene(
                        "minecraft:overworld",
                        15.5,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        true,
                        "OPEN"),
                Failure.DIMENSION);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        15.56,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        true,
                        "OPEN"),
                Failure.COORDINATES);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        Double.NaN,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        true,
                        "OPEN"),
                Failure.COORDINATES);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        15.5,
                        82.0,
                        15.5,
                        Map.of("0,0", true, "1,0", false),
                        READY.anchors(),
                        true,
                        "OPEN"),
                Failure.CHUNKS);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        15.5,
                        82.0,
                        15.5,
                        READY.chunks(),
                        Map.of("arrival_floor", true, "return_terminal", false, "loot_chest", true),
                        true,
                        "OPEN"),
                Failure.ANCHORS);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        15.5,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        false,
                        "OPEN"),
                Failure.RENDERER);
        assertFailure(
                new ObservedScene(
                        READY.dimension(),
                        15.5,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        true,
                        "FAULT"),
                Failure.GATE_STATE);
    }

    @Test
    void readinessMustRemainStableAcrossConsecutiveClientTicks() {
        SceneStability stability = new SceneStability(3);
        Evaluation ready = VisualSceneReadiness.evaluate(EXPECTED, READY);
        Evaluation wrong = VisualSceneReadiness.evaluate(
                EXPECTED,
                new ObservedScene(
                        "minecraft:overworld",
                        15.5,
                        82.0,
                        15.5,
                        READY.chunks(),
                        READY.anchors(),
                        true,
                        "OPEN"));

        assertFalse(stability.update(ready));
        assertFalse(stability.update(ready));
        assertTrue(stability.update(ready));
        assertFalse(stability.update(wrong));
        assertEquals(0, stability.consecutiveReadySamples());
    }

    private static void assertFailure(ObservedScene observed, Failure failure) {
        Evaluation evaluation = VisualSceneReadiness.evaluate(EXPECTED, observed);
        assertFalse(evaluation.ready());
        assertTrue(evaluation.failures().contains(failure));
    }
}
