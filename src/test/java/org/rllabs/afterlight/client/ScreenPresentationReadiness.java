package org.rllabs.afterlight.client;

final class ScreenPresentationReadiness {
    private final int expectedWidth;
    private final int expectedHeight;
    private final int requiredReadySamples;
    private int consecutiveReadySamples;

    ScreenPresentationReadiness(int expectedWidth, int expectedHeight, int requiredReadySamples) {
        this.expectedWidth = expectedWidth;
        this.expectedHeight = expectedHeight;
        this.requiredReadySamples = requiredReadySamples;
    }

    boolean update(
            boolean expectedScreenActive,
            int framebufferWidth,
            int framebufferHeight,
            boolean overlayActive) {
        boolean ready = isFrameReady(
                expectedScreenActive,
                expectedWidth,
                expectedHeight,
                framebufferWidth,
                framebufferHeight,
                overlayActive);
        consecutiveReadySamples = ready ? consecutiveReadySamples + 1 : 0;
        return consecutiveReadySamples >= requiredReadySamples;
    }

    static boolean isFrameReady(
            boolean expectedScreenActive,
            int expectedWidth,
            int expectedHeight,
            int framebufferWidth,
            int framebufferHeight,
            boolean overlayActive) {
        return expectedScreenActive
                && framebufferWidth == expectedWidth
                && framebufferHeight == expectedHeight
                && !overlayActive;
    }

    int consecutiveReadySamples() {
        return consecutiveReadySamples;
    }
}
