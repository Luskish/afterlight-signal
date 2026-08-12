package org.rllabs.afterlight.client;

import java.util.Objects;

final class DeferredFrameCapture {
    private Runnable requestedCapture;
    private boolean callbackPending;

    void request(Runnable capture) {
        if (requestedCapture != null || callbackPending) {
            throw new IllegalStateException("Capture already requested");
        }
        requestedCapture = Objects.requireNonNull(capture);
    }

    void onRenderedFrame() {
        if (requestedCapture == null || callbackPending) {
            return;
        }
        Runnable capture = requestedCapture;
        requestedCapture = null;
        callbackPending = true;
        capture.run();
    }

    void complete() {
        if (!callbackPending) {
            throw new IllegalStateException("No screenshot callback pending");
        }
        callbackPending = false;
    }
}
