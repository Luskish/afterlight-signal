package org.rllabs.afterlight.route;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record EchoRoute(int schema, long terminalQuestId, List<Segment> segments) {
    public EchoRoute {
        segments = List.copyOf(Objects.requireNonNull(segments));
    }

    public List<Long> questIds() {
        return segments.stream().flatMap(segment -> segment.quests().stream()).toList();
    }

    public static String formatQuestId(long questId) {
        return String.format(Locale.ROOT, "%016X", questId);
    }

    public record Segment(String id, List<String> after, List<Long> quests) {
        public Segment {
            id = Objects.requireNonNull(id);
            after = List.copyOf(Objects.requireNonNull(after));
            quests = List.copyOf(Objects.requireNonNull(quests));
        }
    }
}
