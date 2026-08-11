package org.rllabs.afterlight.visual;

import java.util.List;
import org.rllabs.afterlight.visual.VisualSceneReadiness.Evaluation;

public final class VisualReadyMarkerPolicy {
    private VisualReadyMarkerPolicy() {}

    public static boolean mayWrite(boolean exactIdentity, List<Evaluation> scenes) {
        return exactIdentity && !scenes.isEmpty() && scenes.stream().allMatch(Evaluation::ready);
    }
}
