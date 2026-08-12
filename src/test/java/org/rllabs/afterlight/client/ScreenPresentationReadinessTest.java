package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenPresentationReadinessTest {
    @Test
    void activeOverlayPreventsStableScreenReadiness() {
        ScreenPresentationReadiness readiness = new ScreenPresentationReadiness(1920, 1080, 3);

        assertFalse(readiness.update(true, 1920, 1080, true));
        assertFalse(readiness.update(true, 1920, 1080, true));
        assertFalse(readiness.update(true, 1920, 1080, true));
        assertEquals(0, readiness.consecutiveReadySamples());

        assertFalse(readiness.update(false, 1920, 1080, false));
        assertFalse(readiness.update(true, 1919, 1080, false));
        assertFalse(readiness.update(true, 1920, 1079, false));
        assertFalse(readiness.update(true, 1920, 1080, false));
        assertFalse(readiness.update(true, 1920, 1080, false));
        assertTrue(readiness.update(true, 1920, 1080, false));
    }

    @Test
    void activeOverlayMakesRenderedFrameUnready() {
        assertFalse(ScreenPresentationReadiness.isFrameReady(
                true, 1920, 1080, 1920, 1080, true));
        assertTrue(ScreenPresentationReadiness.isFrameReady(
                true, 1920, 1080, 1920, 1080, false));
    }
}
