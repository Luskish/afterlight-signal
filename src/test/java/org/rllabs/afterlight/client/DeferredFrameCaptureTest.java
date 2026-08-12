package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeferredFrameCaptureTest {
    @Test
    void captureLifecycleDefersAndDeduplicatesUntilCompletion() {
        DeferredFrameCapture capture = new DeferredFrameCapture();
        AtomicInteger invocations = new AtomicInteger();

        capture.request(invocations::incrementAndGet);
        assertEquals(0, invocations.get(), "tick-time request captured before a rendered frame");

        capture.onRenderedFrame();
        assertEquals(1, invocations.get(), "rendered frame did not issue the capture");

        capture.onRenderedFrame();
        assertEquals(1, invocations.get(), "pending callback allowed a duplicate capture");

        capture.complete();
        capture.request(invocations::incrementAndGet);
        assertEquals(1, invocations.get(), "next request captured before its rendered frame");

        capture.onRenderedFrame();
        assertEquals(2, invocations.get(), "completion did not permit the next capture");
    }
}
