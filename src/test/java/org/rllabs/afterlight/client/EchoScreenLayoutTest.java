package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.rllabs.afterlight.client.EchoScreenLayout.Mode;
import org.rllabs.afterlight.client.EchoScreenLayout.Rect;

class EchoScreenLayoutTest {
    @ParameterizedTest(name = "{0}x{1} at GUI scale {2}")
    @MethodSource("requiredResolutionMatrix")
    void keepsEveryPaneAndActionOnScreenWithoutOverlap(int width, int height, int guiScale) {
        EchoScreenLayout layout = EchoScreenLayout.compute(width, height, guiScale);

        assertEquals((width + guiScale - 1) / guiScale, layout.logicalWidth());
        assertEquals((height + guiScale - 1) / guiScale, layout.logicalHeight());

        List<Rect> panes = layout.panes();
        assertEquals(5, panes.size());
        panes.forEach(rect -> assertOnScreen(rect, layout.logicalWidth(), layout.logicalHeight()));
        for (int first = 0; first < panes.size(); first++) {
            for (int second = first + 1; second < panes.size(); second++) {
                assertFalse(panes.get(first).overlaps(panes.get(second)));
            }
        }

        assertEquals(4, layout.actionButtons().size());
        layout.actionButtons().forEach(button -> {
            assertTrue(layout.actionRail().contains(button));
            assertTrue(button.height() >= 20);
        });
        for (int first = 0; first < layout.actionButtons().size(); first++) {
            for (int second = first + 1; second < layout.actionButtons().size(); second++) {
                assertTrue(separation(layout.actionButtons().get(first), layout.actionButtons().get(second)) >= 2);
            }
        }
    }

    @Test
    void computesOddLogicalDimensionsWithMinecraftCeiling() {
        EchoScreenLayout layout = EchoScreenLayout.compute(1025, 769, 3);

        assertEquals(342, layout.logicalWidth());
        assertEquals(257, layout.logicalHeight());
        assertEquals(Mode.STANDARD, layout.mode());
    }

    @Test
    void wideThresholdRequiresBothLogicalDimensions() {
        assertEquals(Mode.WIDE, EchoScreenLayout.compute(1119, 599, 2).mode());
        assertEquals(Mode.STANDARD, EchoScreenLayout.compute(1118, 599, 2).mode());
        assertEquals(Mode.STANDARD, EchoScreenLayout.compute(1119, 598, 2).mode());
    }

    @Test
    void compactThresholdUsesEitherLogicalDimension() {
        assertEquals(Mode.STANDARD, EchoScreenLayout.compute(1277, 717, 4).mode());
        assertEquals(Mode.COMPACT, EchoScreenLayout.compute(1276, 717, 4).mode());
        assertEquals(Mode.COMPACT, EchoScreenLayout.compute(1277, 716, 4).mode());
    }

    private static Stream<Arguments> requiredResolutionMatrix() {
        return Stream.of(854, 1280, 1920).flatMap(width -> {
            int height = switch (width) {
                case 854 -> 480;
                case 1280 -> 720;
                default -> 1080;
            };
            return Stream.of(2, 3, 4).map(scale -> Arguments.of(width, height, scale));
        });
    }

    private static void assertOnScreen(Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.width() > 0);
        assertTrue(rect.height() > 0);
        assertTrue(rect.right() <= width);
        assertTrue(rect.bottom() <= height);
    }

    private static int separation(Rect first, Rect second) {
        if (first.right() <= second.x()) {
            return second.x() - first.right();
        }
        if (second.right() <= first.x()) {
            return first.x() - second.right();
        }
        if (first.bottom() <= second.y()) {
            return second.y() - first.bottom();
        }
        if (second.bottom() <= first.y()) {
            return first.y() - second.bottom();
        }
        return -1;
    }
}
