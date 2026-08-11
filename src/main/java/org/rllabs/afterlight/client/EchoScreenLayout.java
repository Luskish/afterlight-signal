package org.rllabs.afterlight.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EchoScreenLayout(
        int logicalWidth,
        int logicalHeight,
        Mode mode,
        Rect header,
        Rect transcript,
        Rect route,
        Rect progress,
        Rect actionRail,
        List<Rect> actionButtons) {
    private static final int GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int WIDE_MIN_WIDTH = 560;
    private static final int WIDE_MIN_HEIGHT = 300;
    private static final int COMPACT_MIN_WIDTH = 320;
    private static final int COMPACT_MIN_HEIGHT = 180;
    private static final int MINIMUM_WIDTH = 96;
    private static final int MINIMUM_HEIGHT = 80;
    private static final Rect EMPTY_RECT = new Rect(0, 0, 0, 0);

    public EchoScreenLayout {
        mode = Objects.requireNonNull(mode);
        header = Objects.requireNonNull(header);
        transcript = Objects.requireNonNull(transcript);
        route = Objects.requireNonNull(route);
        progress = Objects.requireNonNull(progress);
        actionRail = Objects.requireNonNull(actionRail);
        actionButtons = List.copyOf(Objects.requireNonNull(actionButtons));
    }

    public static EchoScreenLayout compute(int framebufferWidth, int framebufferHeight, int guiScale) {
        int safeScale = Math.max(1, guiScale);
        int logicalWidth = ceilDivide(Math.max(0, framebufferWidth), safeScale);
        int logicalHeight = ceilDivide(Math.max(0, framebufferHeight), safeScale);
        if (logicalWidth < MINIMUM_WIDTH || logicalHeight < MINIMUM_HEIGHT) {
            return new EchoScreenLayout(
                    logicalWidth,
                    logicalHeight,
                    Mode.MINIMAL,
                    EMPTY_RECT,
                    EMPTY_RECT,
                    EMPTY_RECT,
                    EMPTY_RECT,
                    EMPTY_RECT,
                    List.of());
        }

        Mode mode = mode(logicalWidth, logicalHeight);
        int margin = mode == Mode.COMPACT ? 2 : 4;
        int headerHeight = mode == Mode.COMPACT ? 36 : 26;
        Rect header = new Rect(margin, margin, logicalWidth - margin * 2, headerHeight);

        if (mode == Mode.WIDE) {
            return computeWide(logicalWidth, logicalHeight, mode, margin, header);
        }
        return computeBottomRail(logicalWidth, logicalHeight, mode, margin, header);
    }

    public List<Rect> panes() {
        if (mode == Mode.MINIMAL) {
            return List.of();
        }
        return List.of(header, transcript, route, progress, actionRail);
    }

    public PaneLabels paneLabels() {
        if (mode == Mode.COMPACT) {
            return new PaneLabels(
                    "screen.afterlight.echo.pane.log",
                    "screen.afterlight.echo.pane.route.compact",
                    "screen.afterlight.echo.pane.state");
        }
        return new PaneLabels(
                "screen.afterlight.echo.pane.transcript",
                "screen.afterlight.echo.pane.route",
                "screen.afterlight.echo.pane.progress");
    }

    public Rect textClip(Rect pane) {
        Objects.requireNonNull(pane);
        int horizontalInset = Math.min(2, pane.width() / 2);
        int verticalInset = Math.min(2, pane.height() / 2);
        return new Rect(
                pane.x() + horizontalInset,
                pane.y() + verticalInset,
                Math.max(0, pane.width() - horizontalInset * 2),
                Math.max(0, pane.height() - verticalInset * 2));
    }

    public boolean canRenderTextLine(Rect pane, int y, int lineHeight) {
        Rect clip = textClip(pane);
        return lineHeight > 0
                && clip.width() > 0
                && clip.height() > 0
                && y >= clip.y()
                && y + lineHeight <= clip.bottom();
    }

    public Rect faultLine() {
        if (mode != Mode.MINIMAL || logicalWidth == 0 || logicalHeight == 0) {
            return EMPTY_RECT;
        }
        int height = Math.min(9, logicalHeight);
        return new Rect(0, Math.max(0, (logicalHeight - height) / 2), logicalWidth, height);
    }

    private static EchoScreenLayout computeWide(
            int logicalWidth,
            int logicalHeight,
            Mode mode,
            int margin,
            Rect header) {
        int bodyY = header.bottom() + GAP;
        int bodyHeight = logicalHeight - margin - bodyY;
        int actionWidth = clamp(logicalWidth / 5, 104, 136);
        Rect actionRail = new Rect(logicalWidth - margin - actionWidth, bodyY, actionWidth, bodyHeight);

        int contentWidth = actionRail.x() - GAP - margin;
        int transcriptWidth = clamp(contentWidth * 3 / 10, 86, 164);
        Rect transcript = new Rect(margin, bodyY, transcriptWidth, bodyHeight);
        int centerX = transcript.right() + GAP;
        int centerWidth = actionRail.x() - GAP - centerX;
        int routeHeight = (bodyHeight - GAP) * 3 / 5;
        Rect route = new Rect(centerX, bodyY, centerWidth, routeHeight);
        Rect progress = new Rect(centerX, route.bottom() + GAP, centerWidth, bodyHeight - routeHeight - GAP);

        List<Rect> buttons = verticalButtons(actionRail);
        return new EchoScreenLayout(
                logicalWidth,
                logicalHeight,
                mode,
                header,
                transcript,
                route,
                progress,
                actionRail,
                buttons);
    }

    private static EchoScreenLayout computeBottomRail(
            int logicalWidth,
            int logicalHeight,
            Mode mode,
            int margin,
            Rect header) {
        int actionHeight = mode == Mode.COMPACT ? 24 : 26;
        Rect actionRail = new Rect(
                margin,
                logicalHeight - margin - actionHeight,
                logicalWidth - margin * 2,
                actionHeight);
        int bodyY = header.bottom() + GAP;
        int bodyHeight = actionRail.y() - GAP - bodyY;
        int contentWidth = logicalWidth - margin * 2;
        int minimumTranscript = mode == Mode.COMPACT ? 46 : 72;
        int transcriptWidth = clamp(contentWidth * 3 / 10, minimumTranscript, Math.max(minimumTranscript, contentWidth / 2));
        Rect transcript = new Rect(margin, bodyY, transcriptWidth, bodyHeight);
        int centerX = transcript.right() + GAP;
        int centerWidth = logicalWidth - margin - centerX;
        int routeHeight = (bodyHeight - GAP) / 2;
        Rect route = new Rect(centerX, bodyY, centerWidth, routeHeight);
        Rect progress = new Rect(centerX, route.bottom() + GAP, centerWidth, bodyHeight - routeHeight - GAP);

        List<Rect> buttons = horizontalButtons(actionRail);
        return new EchoScreenLayout(
                logicalWidth,
                logicalHeight,
                mode,
                header,
                transcript,
                route,
                progress,
                actionRail,
                buttons);
    }

    private static List<Rect> verticalButtons(Rect rail) {
        int padding = 4;
        int buttonGap = 6;
        int availableHeight = rail.height() - padding * 2 - buttonGap * 3;
        int buttonHeight = clamp(availableHeight / 4, BUTTON_HEIGHT, 28);
        int usedHeight = buttonHeight * 4 + buttonGap * 3;
        int y = rail.y() + Math.max(padding, (rail.height() - usedHeight) / 2);
        List<Rect> buttons = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            buttons.add(new Rect(rail.x() + padding, y, rail.width() - padding * 2, buttonHeight));
            y += buttonHeight + buttonGap;
        }
        return List.copyOf(buttons);
    }

    private static List<Rect> horizontalButtons(Rect rail) {
        int padding = 3;
        int availableWidth = rail.width() - padding * 2 - GAP * 3;
        int baseWidth = availableWidth / 4;
        int remainder = availableWidth % 4;
        int x = rail.x() + padding;
        int y = rail.y() + (rail.height() - BUTTON_HEIGHT) / 2;
        List<Rect> buttons = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int width = baseWidth + (index < remainder ? 1 : 0);
            buttons.add(new Rect(x, y, width, BUTTON_HEIGHT));
            x += width + GAP;
        }
        return List.copyOf(buttons);
    }

    private static Mode mode(int width, int height) {
        if (width < COMPACT_MIN_WIDTH || height < COMPACT_MIN_HEIGHT) {
            return Mode.COMPACT;
        }
        if (width >= WIDE_MIN_WIDTH && height >= WIDE_MIN_HEIGHT) {
            return Mode.WIDE;
        }
        return Mode.STANDARD;
    }

    private static int ceilDivide(int value, int divisor) {
        return (int) (((long) value + divisor - 1L) / divisor);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Mode {
        WIDE,
        STANDARD,
        COMPACT,
        MINIMAL
    }

    public record PaneLabels(String transcriptKey, String routeKey, String progressKey) {
        public PaneLabels {
            Objects.requireNonNull(transcriptKey);
            Objects.requireNonNull(routeKey);
            Objects.requireNonNull(progressKey);
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Rectangle dimensions cannot be negative");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(Rect other) {
            Objects.requireNonNull(other);
            return other.x >= x
                    && other.y >= y
                    && other.right() <= right()
                    && other.bottom() <= bottom();
        }

        public boolean overlaps(Rect other) {
            Objects.requireNonNull(other);
            if (width == 0 || height == 0 || other.width == 0 || other.height == 0) {
                return false;
            }
            return x < other.right()
                    && right() > other.x
                    && y < other.bottom()
                    && bottom() > other.y;
        }
    }
}
