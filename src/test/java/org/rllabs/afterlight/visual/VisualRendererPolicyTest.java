package org.rllabs.afterlight.visual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisualRendererPolicyTest {
    @Test
    void onlyAnActualMesaLlvmpipeContextIsApproved() {
        assertTrue(VisualRendererPolicy.isApproved(
                "Mesa/X.org",
                "llvmpipe (LLVM 19.1.7, 256 bits)",
                "4.5 (Core Profile) Mesa 24.3.4"));
        assertFalse(VisualRendererPolicy.isApproved(
                "NVIDIA Corporation", "NVIDIA GeForce RTX", "4.6"));
        assertFalse(VisualRendererPolicy.isApproved(
                "Mesa/X.org", "softpipe", "4.5 Mesa"));
        assertFalse(VisualRendererPolicy.isApproved(
                "Mesa/X.org", "Minecraft Screenshot API under Xvfb and Mesa", "unknown"));
    }
}
