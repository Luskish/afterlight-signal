package org.rllabs.afterlight.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class EchoRouteLoader {
    public static final Path DEFAULT_PATH = Path.of("config/afterlight/echo_route.json");

    private static final Pattern HEX_ID = Pattern.compile("[0-9A-Fa-f]{1,16}");
    private static final long ESTABLISHED_FINALE_ID = Long.parseUnsignedLong("31C9557D2F51238F", 16);

    public EchoRoute load() throws IOException, RouteValidationException {
        return load(DEFAULT_PATH);
    }

    public EchoRoute load(Path path) throws IOException, RouteValidationException {
        try (Reader reader = Files.newBufferedReader(Objects.requireNonNull(path))) {
            return load(reader);
        }
    }

    public EchoRoute load(Reader reader) throws RouteValidationException {
        JsonElement root;
        try {
            root = JsonParser.parseReader(Objects.requireNonNull(reader));
        } catch (JsonParseException exception) {
            throw new RouteValidationException(List.of("route JSON is malformed"));
        }

        if (root == null || !root.isJsonObject()) {
            throw new RouteValidationException(List.of("route root must be an object"));
        }

        JsonObject object = root.getAsJsonObject();
        List<String> errors = new ArrayList<>();
        Integer schema = readSchema(object, errors);
        Long terminalQuestId = readHexField(object, "terminal_quest", errors);
        List<SegmentDraft> segments = readSegments(object, errors);

        validateGraph(segments, errors);
        if (terminalQuestId != null) {
            boolean terminalPresent = segments.stream()
                    .flatMap(segment -> segment.quests().stream())
                    .anyMatch(terminalQuestId::equals);
            if (!terminalPresent) {
                errors.add("terminal quest " + EchoRoute.formatQuestId(terminalQuestId) + " is absent from the route");
            } else if (errors.isEmpty() && terminalQuestId != ESTABLISHED_FINALE_ID) {
                errors.add("terminal quest " + EchoRoute.formatQuestId(terminalQuestId)
                        + " does not match established finale " + EchoRoute.formatQuestId(ESTABLISHED_FINALE_ID));
            }
        }

        if (!errors.isEmpty()) {
            throw new RouteValidationException(errors);
        }

        return new EchoRoute(
                schema,
                terminalQuestId,
                segments.stream()
                        .map(segment -> new EchoRoute.Segment(segment.id(), segment.after(), segment.quests()))
                        .toList());
    }

    private static Integer readSchema(JsonObject object, List<String> errors) {
        if (!object.has("schema") || object.get("schema").isJsonNull()) {
            errors.add("schema is required");
            return null;
        }
        JsonElement element = object.get("schema");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add("schema must be an integer");
            return null;
        }
        try {
            int schema = element.getAsBigDecimal().intValueExact();
            if (schema != 1) {
                errors.add("schema must be 1, found " + schema);
            }
            return schema;
        } catch (ArithmeticException | NumberFormatException exception) {
            errors.add("schema must be an integer");
            return null;
        }
    }

    private static Long readHexField(JsonObject object, String field, List<String> errors) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            errors.add(field + " is required");
            return null;
        }
        JsonElement element = object.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            errors.add(field + " must be a string");
            return null;
        }
        return parseHexId(element.getAsString(), field, errors);
    }

    private static List<SegmentDraft> readSegments(JsonObject object, List<String> errors) {
        if (!object.has("segments") || object.get("segments").isJsonNull()) {
            errors.add("segments is required");
            return List.of();
        }
        JsonElement element = object.get("segments");
        if (!element.isJsonArray()) {
            errors.add("segments must be an array");
            return List.of();
        }

        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            errors.add("segments must contain at least one segment");
        }

        List<SegmentDraft> segments = new ArrayList<>();
        Set<String> segmentIds = new LinkedHashSet<>();
        Set<Long> questIds = new LinkedHashSet<>();

        for (int segmentIndex = 0; segmentIndex < array.size(); segmentIndex++) {
            JsonElement segmentElement = array.get(segmentIndex);
            String prefix = "segments[" + segmentIndex + "]";
            if (!segmentElement.isJsonObject()) {
                errors.add(prefix + " must be an object");
                continue;
            }

            JsonObject segmentObject = segmentElement.getAsJsonObject();
            String id = readSegmentId(segmentObject, prefix, errors);
            List<String> after = readStringArray(segmentObject, "after", prefix, errors);
            List<Long> quests = readQuestIds(segmentObject, prefix, errors, questIds);

            if (id != null && !segmentIds.add(id)) {
                errors.add("duplicate segment ID " + id + " at " + prefix + ".id");
            }
            if (id != null && after != null && quests != null) {
                segments.add(new SegmentDraft(id, after, quests));
            }
        }
        return segments;
    }

    private static String readSegmentId(JsonObject object, String prefix, List<String> errors) {
        if (!object.has("id") || object.get("id").isJsonNull()) {
            errors.add(prefix + ".id is required");
            return null;
        }
        JsonElement element = object.get("id");
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            errors.add(prefix + ".id must be a string");
            return null;
        }
        String id = element.getAsString();
        if (id.isBlank()) {
            errors.add(prefix + ".id must not be blank");
            return null;
        }
        return id;
    }

    private static List<String> readStringArray(
            JsonObject object,
            String field,
            String prefix,
            List<String> errors) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            errors.add(prefix + "." + field + " is required");
            return null;
        }
        JsonElement element = object.get(field);
        if (!element.isJsonArray()) {
            errors.add(prefix + "." + field + " must be an array");
            return null;
        }

        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                errors.add(prefix + "." + field + "[" + index + "] must be a string");
                continue;
            }
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }

    private static List<Long> readQuestIds(
            JsonObject object,
            String prefix,
            List<String> errors,
            Set<Long> allQuestIds) {
        if (!object.has("quests") || object.get("quests").isJsonNull()) {
            errors.add(prefix + ".quests is required");
            return null;
        }
        JsonElement element = object.get("quests");
        if (!element.isJsonArray()) {
            errors.add(prefix + ".quests must be an array");
            return null;
        }

        List<Long> quests = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (int questIndex = 0; questIndex < array.size(); questIndex++) {
            JsonElement questElement = array.get(questIndex);
            String location = prefix + ".quests[" + questIndex + "]";
            if (!questElement.isJsonPrimitive() || !questElement.getAsJsonPrimitive().isString()) {
                errors.add(location + " must be a string");
                continue;
            }
            Long questId = parseHexId(questElement.getAsString(), location, errors);
            if (questId == null) {
                continue;
            }
            quests.add(questId);
            if (!allQuestIds.add(questId)) {
                errors.add("duplicate quest ID " + EchoRoute.formatQuestId(questId) + " at " + location);
            }
        }
        return List.copyOf(quests);
    }

    private static Long parseHexId(String value, String location, List<String> errors) {
        if (!HEX_ID.matcher(value).matches()) {
            errors.add(location + " must be 1 to 16 hexadecimal digits, found \"" + value + "\"");
            return null;
        }
        return Long.parseUnsignedLong(value, 16);
    }

    private static void validateGraph(List<SegmentDraft> segments, List<String> errors) {
        Map<String, SegmentDraft> byId = new LinkedHashMap<>();
        boolean duplicateIds = false;
        for (SegmentDraft segment : segments) {
            if (byId.putIfAbsent(segment.id(), segment) != null) {
                duplicateIds = true;
            }
        }

        for (SegmentDraft segment : byId.values()) {
            for (String dependency : segment.after()) {
                if (!byId.containsKey(dependency)) {
                    errors.add("segment " + segment.id() + " has unknown dependency " + dependency);
                }
            }
        }

        if (!duplicateIds) {
            detectCycles(byId, errors);
            detectUnreachable(byId, errors);
        }
    }

    private static void detectCycles(Map<String, SegmentDraft> byId, List<String> errors) {
        Map<String, VisitState> states = new HashMap<>();
        List<String> stack = new ArrayList<>();
        Set<String> emittedCycles = new HashSet<>();
        for (String segmentId : byId.keySet()) {
            detectCycles(segmentId, byId, states, stack, emittedCycles, errors);
        }
    }

    private static void detectCycles(
            String segmentId,
            Map<String, SegmentDraft> byId,
            Map<String, VisitState> states,
            List<String> stack,
            Set<String> emittedCycles,
            List<String> errors) {
        VisitState state = states.get(segmentId);
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            int cycleStart = stack.indexOf(segmentId);
            List<String> cycle = new ArrayList<>(stack.subList(cycleStart, stack.size()));
            cycle.add(segmentId);
            String rendered = String.join(" -> ", cycle);
            if (emittedCycles.add(rendered)) {
                errors.add("segment dependency cycle: " + rendered);
            }
            return;
        }

        states.put(segmentId, VisitState.VISITING);
        stack.add(segmentId);
        SegmentDraft segment = byId.get(segmentId);
        for (String dependency : segment.after()) {
            if (byId.containsKey(dependency)) {
                detectCycles(dependency, byId, states, stack, emittedCycles, errors);
            }
        }
        stack.removeLast();
        states.put(segmentId, VisitState.VISITED);
    }

    private static void detectUnreachable(Map<String, SegmentDraft> byId, List<String> errors) {
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (String segmentId : byId.keySet()) {
            dependents.put(segmentId, new ArrayList<>());
        }
        for (SegmentDraft segment : byId.values()) {
            for (String dependency : segment.after()) {
                List<String> dependencyDependents = dependents.get(dependency);
                if (dependencyDependents != null) {
                    dependencyDependents.add(segment.id());
                }
            }
        }

        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        for (SegmentDraft segment : byId.values()) {
            if (segment.after().isEmpty()) {
                reachable.add(segment.id());
                pending.add(segment.id());
            }
        }
        while (!pending.isEmpty()) {
            for (String dependent : dependents.get(pending.removeFirst())) {
                if (reachable.add(dependent)) {
                    pending.addLast(dependent);
                }
            }
        }

        for (String segmentId : byId.keySet()) {
            if (!reachable.contains(segmentId)) {
                errors.add("segment " + segmentId + " is unreachable from every zero-dependency root");
            }
        }
    }

    private record SegmentDraft(String id, List<String> after, List<Long> quests) {
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    public static final class RouteValidationException extends Exception {
        private final List<String> errors;

        public RouteValidationException(List<String> errors) {
            super(String.join(System.lineSeparator(), errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
