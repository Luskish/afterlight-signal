package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReleaseRecordContractTest {
    private static final Path ROOT = Path.of(
                    System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();
    private static final String RECORD_PATH = "docs/releases/0.2.0.md";
    private static final String BLOCK_START = "<!-- AFTERLIGHT_RELEASE_RECORD_V1";
    private static final String BLOCK_END = "AFTERLIGHT_RELEASE_RECORD_END -->";
    private static final String JAR = "afterlight-signal-0.2.0+1.21.1.jar";
    private static final Set<String> SOURCE_COMMIT_FILES = Set.of(
            ".github/workflows/build.yml",
            "README.md",
            RECORD_PATH,
            "gradle.properties",
            "src/main/resources/META-INF/neoforge.mods.toml",
            "src/test/java/org/rllabs/afterlight/ReleaseJarContractTest.java",
            "src/test/java/org/rllabs/afterlight/ReleaseRecordContractTest.java");
    private static final List<String> EVIDENCE_KEYS = List.of(
            "accepted_source_sha",
            "jar_sha256",
            "jar_sha512",
            "local_double_build",
            "linux_build_ci",
            "visual_ci",
            "visual_artifact",
            "visual_review",
            "route_evidence",
            "final_review",
            "public_release");

    @Test
    void releaseRecordIsPendingOnlyInTheImmutableSourceCommit() throws Exception {
        Path record = ROOT.resolve(RECORD_PATH);
        assertTrue(Files.isRegularFile(record), "missing release record: " + record);
        String document = Files.readString(record, StandardCharsets.UTF_8);
        Map<String, String> fields = recordFields(document);

        assertEquals(
                Set.of(
                        "schema",
                        "state",
                        "version",
                        "jar",
                        "assets",
                        "accepted_source_sha",
                        "jar_sha256",
                        "jar_sha512",
                        "local_double_build",
                        "linux_build_ci",
                        "visual_ci",
                        "visual_artifact",
                        "visual_review",
                        "route_evidence",
                        "final_review",
                        "public_release"),
                fields.keySet());
        assertEquals("1", fields.get("schema"));
        assertEquals("0.2.0", fields.get("version"));
        assertEquals(JAR, fields.get("jar"));
        assertEquals(JAR + "," + JAR + ".sha256," + JAR + ".sha512", fields.get("assets"));

        String currentCommit = git("rev-parse", "HEAD");
        String parentCommit = git("rev-parse", "HEAD^");
        Set<String> changedFiles = Set.copyOf(lines(git(
                "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD")));
        String state = fields.get("state");

        if ("SOURCE_PENDING".equals(state)) {
            assertEquals(SOURCE_COMMIT_FILES, changedFiles);
            assertEquals(
                    currentCommit,
                    System.getProperty("afterlight.source.commit"),
                    "release build is not bound to the pending source commit");
            for (String key : EVIDENCE_KEYS) {
                assertEquals("PENDING", fields.get(key), "non-pending source field: " + key);
            }
            return;
        }

        assertEquals("COMPLETE", state);
        assertEquals(Set.of(RECORD_PATH), changedFiles);
        assertEquals(parentCommit, fields.get("accepted_source_sha"));
        assertFalse(document.contains("PENDING"), "complete release record contains pending evidence");
        for (String key : EVIDENCE_KEYS) {
            assertFalse(fields.get(key).isBlank(), "blank completed field: " + key);
        }
    }

    private static Map<String, String> recordFields(String document) {
        int start = document.indexOf(BLOCK_START);
        int end = document.indexOf(BLOCK_END);
        assertTrue(start >= 0, "missing release record block start");
        assertTrue(end > start, "missing release record block end");
        assertEquals(start, document.lastIndexOf(BLOCK_START), "duplicate release record block start");
        assertEquals(end, document.lastIndexOf(BLOCK_END), "duplicate release record block end");

        String block = document.substring(start + BLOCK_START.length(), end).strip();
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : block.lines().toList()) {
            int separator = line.indexOf('=');
            assertTrue(separator > 0, "invalid release record line: " + line);
            String key = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            assertFalse(value.isBlank(), "blank release record field: " + key);
            assertEquals(null, fields.put(key, value), "duplicate release record field: " + key);
        }
        return Map.copyOf(fields);
    }

    private static List<String> lines(String output) {
        if (output.isBlank()) {
            return List.of();
        }
        return Arrays.stream(output.split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String git(String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(ROOT.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .strip();
        assertEquals(0, process.waitFor(), "git command failed: " + output);
        return output;
    }
}
