package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void requiredCompactLayoutUsesShortLabelsAndConfinedTextClips() throws Exception {
        EchoScreenLayout layout = EchoScreenLayout.compute(214, 120, 1);
        EchoScreenLayout.PaneLabels labels = layout.paneLabels();
        JsonObject language = JsonParser.parseString(Files.readString(
                        Path.of("src/main/resources/assets/afterlight/lang/en_us.json")))
                .getAsJsonObject();

        assertEquals(Mode.COMPACT, layout.mode());
        assertEquals("LOG", language.get(labels.transcriptKey()).getAsString());
        assertEquals("ROUTE", language.get(labels.routeKey()).getAsString());
        assertEquals("STATE", language.get(labels.progressKey()).getAsString());

        List<Rect> labeledPanes = List.of(layout.transcript(), layout.route(), layout.progress());
        for (Rect pane : labeledPanes) {
            Rect clip = layout.textClip(pane);
            assertTrue(pane.contains(clip));
            for (Rect other : layout.panes()) {
                if (other != pane) {
                    assertFalse(clip.overlaps(other));
                }
            }
        }
    }

    @ParameterizedTest(name = "minimal {0}x{1} at GUI scale {2}")
    @MethodSource("belowMinimumMatrix")
    void belowMinimumLayoutsAreSafeAndContainOnlyClippedFaultLine(int width, int height, int guiScale) {
        EchoScreenLayout layout = assertDoesNotThrow(() -> EchoScreenLayout.compute(width, height, guiScale));

        assertEquals("MINIMAL", layout.mode().name());
        assertTrue(layout.panes().isEmpty());
        assertTrue(layout.actionButtons().isEmpty());
        assertInsideViewport(layout.faultLine(), layout.logicalWidth(), layout.logicalHeight());
    }

    @Test
    void supportedMinimumConfinesOrHidesEveryLabelAndBodyLine() {
        EchoScreenLayout layout = EchoScreenLayout.compute(96, 80, 1);

        assertEquals(Mode.COMPACT, layout.mode());
        for (Rect pane : List.of(layout.header(), layout.transcript(), layout.route(), layout.progress())) {
            Rect clip = layout.textClip(pane);
            assertTrue(pane.contains(clip));
            for (int y : List.of(pane.y() + 4, pane.y() + 13, pane.y() + 15)) {
                boolean visible = layout.canRenderTextLine(pane, y, 9);
                if (visible) {
                    assertTrue(y >= clip.y());
                    assertTrue(y + 9 <= clip.bottom());
                }
            }
        }
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

    private static Stream<Arguments> belowMinimumMatrix() {
        return Stream.of(
                Arguments.of(95, 79, 1),
                Arguments.of(32, 24, 1),
                Arguments.of(1, 1, 1),
                Arguments.of(0, 0, 1),
                Arguments.of(1, 1, Integer.MAX_VALUE),
                Arguments.of(0, 0, Integer.MAX_VALUE));
    }

    private static void assertOnScreen(Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.width() > 0);
        assertTrue(rect.height() > 0);
        assertTrue(rect.right() <= width);
        assertTrue(rect.bottom() <= height);
    }

    private static void assertInsideViewport(Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.width() >= 0);
        assertTrue(rect.height() >= 0);
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
