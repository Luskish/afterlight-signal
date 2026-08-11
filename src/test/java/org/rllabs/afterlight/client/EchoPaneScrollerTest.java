package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EchoPaneScrollerTest {
    private static final int LINE_HEIGHT = 9;

    @ParameterizedTest(name = "{0}x{1} at GUI scale {2}")
    @MethodSource("supportedDetailLayouts")
    void everyRequiredRouteAndProgressRowIsReachable(int width, int height, int guiScale) {
        EchoScreenLayout layout = EchoScreenLayout.compute(width, height, guiScale);
        List<String> routeRows = List.of(
                "QUEST TITLE",
                "QUEST TITLE CONTINUATION",
                "PREREQUISITES",
                "TASK ONE // 2 / 5 // INCOMPLETE",
                "TASK ONE CONTINUATION",
                "TASK TWO // 1 / 1 // COMPLETE");
        List<String> progressRows = List.of(
                "ROUTE PROGRESS",
                "TASK TITLE",
                "EXACT CURRENT 2 / REQUIRED 5",
                "TEXTUAL COMPLETION INCOMPLETE");

        int routeCapacity = layout.detailLineCapacity(layout.route(), LINE_HEIGHT, false);
        int progressCapacity = layout.detailLineCapacity(layout.progress(), LINE_HEIGHT, true);

        assertTrue(routeCapacity > 0, "route viewport has no body rows");
        assertTrue(progressCapacity > 0, "progress viewport has no body rows");
        assertEquals(new LinkedHashSet<>(routeRows), reachableRows(routeRows, routeCapacity));
        assertEquals(new LinkedHashSet<>(progressRows), reachableRows(progressRows, progressCapacity));
    }

    private static Set<String> reachableRows(List<String> rows, int capacity) {
        EchoPaneScroller scroller = new EchoPaneScroller();
        Set<String> reached = new LinkedHashSet<>(scroller.window(rows, capacity));
        while (scroller.scroll(-1.0D)) {
            reached.addAll(scroller.window(rows, capacity));
        }
        return reached;
    }

    private static Stream<Arguments> supportedDetailLayouts() {
        return Stream.of(854, 1280, 1920).flatMap(width -> {
            int height = switch (width) {
                case 854 -> 480;
                case 1280 -> 720;
                default -> 1080;
            };
            return Stream.of(2, 3, 4).map(scale -> Arguments.of(width, height, scale));
        });
    }
}
