package org.rllabs.afterlight.visual;

import java.util.Locale;

public final class VisualRendererPolicy {
    private VisualRendererPolicy() {}

    public static boolean isApproved(String vendor, String renderer, String version) {
        if (vendor == null || renderer == null || version == null) {
            return false;
        }
        String normalizedVendor = vendor.toLowerCase(Locale.ROOT);
        String normalizedRenderer = renderer.toLowerCase(Locale.ROOT);
        String normalizedVersion = version.toLowerCase(Locale.ROOT);
        return (normalizedVendor.contains("mesa") || normalizedVendor.contains("x.org"))
                && normalizedRenderer.contains("llvmpipe")
                && normalizedVersion.contains("mesa");
    }
}
