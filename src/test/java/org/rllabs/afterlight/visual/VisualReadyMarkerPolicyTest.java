package org.rllabs.afterlight.visual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Failure;

class VisualReadyMarkerPolicyTest {
    @Test
    void markerRequiresExactIdentityAndEveryLoadedValidScene() {
        Evaluation ready = new Evaluation(true, Set.of());
        Evaluation unloaded = new Evaluation(false, Set.of(Failure.CHUNKS));
        Evaluation wrongAnchor = new Evaluation(false, Set.of(Failure.ANCHORS));

        assertTrue(VisualReadyMarkerPolicy.mayWrite(true, List.of(ready, ready)));
        assertFalse(VisualReadyMarkerPolicy.mayWrite(false, List.of(ready, ready)));
        assertFalse(VisualReadyMarkerPolicy.mayWrite(true, List.of(ready, unloaded)));
        assertFalse(VisualReadyMarkerPolicy.mayWrite(true, List.of(ready, wrongAnchor)));
        assertFalse(VisualReadyMarkerPolicy.mayWrite(true, List.of()));
    }
}
