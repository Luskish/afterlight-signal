package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ReleaseWorkflowModel(
        String name,
        Set<String> topLevelKeys,
        Set<String> triggers,
        Map<String, String> permissions,
        Map<String, Job> jobs) {
    static ReleaseWorkflowModel parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        lines.forEach(line -> {
            if (line.indexOf('\t') >= 0) {
                fail("workflow must not contain tabs");
            }
        });
        Set<String> topLevelKeys = keysAtIndent(lines, 0, 0, lines.size());
        String name = scalarAt(lines, 0, "name");
        int onStart = sectionStart(lines, 0, "on");
        int permissionsStart = sectionStart(lines, 0, "permissions");
        int jobsStart = sectionStart(lines, 0, "jobs");
        Set<String> triggers = keysAtIndent(
                lines, 2, onStart + 1, nextAtMostIndent(lines, onStart + 1, 0));
        Map<String, String> permissions = scalarMap(
                lines,
                2,
                permissionsStart + 1,
                nextAtMostIndent(lines, permissionsStart + 1, 0));
        Map<String, Job> jobs = parseJobs(
                lines, jobsStart + 1, nextAtMostIndent(lines, jobsStart + 1, 0));
        return new ReleaseWorkflowModel(
                name,
                Set.copyOf(topLevelKeys),
                Set.copyOf(triggers),
                Map.copyOf(permissions),
                Map.copyOf(jobs));
    }

    List<Step> allSteps() {
        return jobs.values().stream().flatMap(job -> job.steps().stream()).toList();
    }

    private static Map<String, Job> parseJobs(List<String> lines, int start, int end) {
        Map<String, Job> jobs = new LinkedHashMap<>();
        int index = start;
        while (index < end) {
            if (blankOrComment(lines.get(index))) {
                index++;
                continue;
            }
            if (indent(lines.get(index)) != 2) {
                fail("unexpected jobs indentation at line " + (index + 1));
            }
            String jobName = key(lines.get(index));
            int jobEnd = nextAtMostIndent(lines, index + 1, 2);
            Map<String, String> fields = scalarMap(lines, 4, index + 1, jobEnd);
            int stepsStart = sectionStart(lines, 4, "steps", index + 1, jobEnd);
            List<Step> steps = parseSteps(lines, stepsStart + 1, jobEnd);
            jobs.put(
                    jobName,
                    new Job(
                            fields.get("runs-on"),
                            Set.copyOf(keysAtIndent(lines, 4, index + 1, jobEnd)),
                            List.copyOf(steps)));
            index = jobEnd;
        }
        return jobs;
    }

    private static List<Step> parseSteps(List<String> lines, int start, int end) {
        List<Step> steps = new ArrayList<>();
        int index = start;
        while (index < end) {
            if (blankOrComment(lines.get(index))) {
                index++;
                continue;
            }
            String line = lines.get(index);
            if (indent(line) != 6 || !line.stripLeading().startsWith("- ")) {
                fail("unexpected step at line " + (index + 1));
            }
            int stepEnd = nextStep(lines, index + 1, end);
            Map<String, String> scalars = new LinkedHashMap<>();
            Map<String, String> with = new LinkedHashMap<>();
            Map<String, String> environment = new LinkedHashMap<>();
            parseStepField(line.stripLeading().substring(2), scalars);
            int fieldIndex = index + 1;
            while (fieldIndex < stepEnd) {
                if (blankOrComment(lines.get(fieldIndex))) {
                    fieldIndex++;
                    continue;
                }
                String fieldLine = lines.get(fieldIndex);
                if (indent(fieldLine) != 8) {
                    fail("unexpected step field at line " + (fieldIndex + 1));
                }
                String fieldKey = key(fieldLine);
                String fieldValue = value(fieldLine);
                if (fieldValue.equals("|")) {
                    int blockEnd = fieldIndex + 1;
                    while (blockEnd < stepEnd
                            && (blankOrComment(lines.get(blockEnd))
                                    || indent(lines.get(blockEnd)) >= 10)) {
                        blockEnd++;
                    }
                    List<String> block = new ArrayList<>();
                    for (int blockIndex = fieldIndex + 1; blockIndex < blockEnd; blockIndex++) {
                        String blockLine = lines.get(blockIndex);
                        block.add(blockLine.length() >= 10 ? blockLine.substring(10) : "");
                    }
                    scalars.put(fieldKey, String.join("\n", block).stripTrailing());
                    fieldIndex = blockEnd;
                    continue;
                }
                if (fieldValue.isEmpty() && (fieldKey.equals("with") || fieldKey.equals("env"))) {
                    int mapEnd = fieldIndex + 1;
                    while (mapEnd < stepEnd && (blankOrComment(lines.get(mapEnd))
                            || indent(lines.get(mapEnd)) >= 10)) {
                        mapEnd++;
                    }
                    Map<String, String> target = fieldKey.equals("with") ? with : environment;
                    target.putAll(scalarMap(lines, 10, fieldIndex + 1, mapEnd));
                    fieldIndex = mapEnd;
                    continue;
                }
                scalars.put(fieldKey, fieldValue);
                fieldIndex++;
            }
            steps.add(new Step(
                    scalars.get("name"),
                    scalars.get("uses"),
                    scalars.get("run"),
                    scalars.get("working-directory"),
                    Map.copyOf(with),
                    Map.copyOf(environment),
                    Set.copyOf(scalars.keySet())));
            index = stepEnd;
        }
        return steps;
    }

    private static void parseStepField(String field, Map<String, String> scalars) {
        int separator = field.indexOf(':');
        if (separator < 0) {
            fail("step field lacks colon: " + field);
        }
        scalars.put(field.substring(0, separator).strip(), cleanScalar(field.substring(separator + 1)));
    }

    private static int nextStep(List<String> lines, int start, int end) {
        for (int index = start; index < end; index++) {
            if (indent(lines.get(index)) == 6 && lines.get(index).stripLeading().startsWith("- ")) {
                return index;
            }
        }
        return end;
    }

    private static Map<String, String> scalarMap(
            List<String> lines, int expectedIndent, int start, int end) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = start; index < end; index++) {
            String line = lines.get(index);
            if (blankOrComment(line) || indent(line) != expectedIndent) {
                continue;
            }
            String value = value(line);
            if (!value.isEmpty()) {
                values.put(key(line), value);
            }
        }
        return values;
    }

    private static Set<String> keysAtIndent(
            List<String> lines, int expectedIndent, int start, int end) {
        Set<String> keys = new LinkedHashSet<>();
        for (int index = start; index < end; index++) {
            String line = lines.get(index);
            if (!blankOrComment(line) && indent(line) == expectedIndent) {
                keys.add(key(line));
            }
        }
        return keys;
    }

    private static String scalarAt(List<String> lines, int expectedIndent, String expectedKey) {
        int index = sectionStart(lines, expectedIndent, expectedKey);
        return value(lines.get(index));
    }

    private static int sectionStart(List<String> lines, int expectedIndent, String expectedKey) {
        return sectionStart(lines, expectedIndent, expectedKey, 0, lines.size());
    }

    private static int sectionStart(
            List<String> lines, int expectedIndent, String expectedKey, int start, int end) {
        for (int index = start; index < end; index++) {
            String line = lines.get(index);
            if (!blankOrComment(line)
                    && indent(line) == expectedIndent
                    && key(line).equals(expectedKey)) {
                return index;
            }
        }
        fail("missing workflow key: " + expectedKey);
        return -1;
    }

    private static int nextAtMostIndent(List<String> lines, int start, int maximumIndent) {
        for (int index = start; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!blankOrComment(line) && indent(line) <= maximumIndent) {
                return index;
            }
        }
        return lines.size();
    }

    private static String key(String line) {
        String stripped = line.strip();
        int separator = stripped.indexOf(':');
        if (separator < 0) {
            fail("workflow line lacks colon: " + line);
        }
        return stripped.substring(0, separator);
    }

    private static String value(String line) {
        String stripped = line.strip();
        int separator = stripped.indexOf(':');
        if (separator < 0) {
            fail("workflow line lacks colon: " + line);
        }
        return cleanScalar(stripped.substring(separator + 1));
    }

    private static String cleanScalar(String raw) {
        String value = raw.strip();
        int comment = value.indexOf(" #");
        if (comment >= 0) {
            value = value.substring(0, comment).stripTrailing();
        }
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                        || (value.startsWith("\"") && value.endsWith("\"")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int indent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static boolean blankOrComment(String line) {
        String stripped = line.strip();
        return stripped.isEmpty() || stripped.startsWith("#");
    }

    record Job(String runner, Set<String> keys, List<Step> steps) {}

    record Step(
            String name,
            String uses,
            String run,
            String workingDirectory,
            Map<String, String> with,
            Map<String, String> environment,
            Set<String> keys) {}
}
