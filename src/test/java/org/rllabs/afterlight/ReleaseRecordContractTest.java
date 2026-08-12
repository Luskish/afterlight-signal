package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            RECORD_PATH,
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
        String recordCommit = git("log", "-1", "--format=%H", "--", RECORD_PATH);
        String state = fields.get("state");
        validateRecordOwnership(state, currentCommit, recordCommit);

        if ("SOURCE_PENDING".equals(state)) {
            assertEquals(SOURCE_COMMIT_FILES, changedFiles(recordCommit));
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
        String parentCommit = git("rev-parse", recordCommit + "^");
        assertEquals(Set.of(RECORD_PATH), changedFiles(recordCommit));
        assertFalse(document.contains("PENDING"), "complete release record contains pending evidence");
        validateCompletedEvidence(fields, parentCommit);
    }

    @Test
    void completedEvidenceRejectsMalformedOrUnboundClaims() {
        String acceptedSource = "a".repeat(40);
        Map<String, String> valid = validCompletedEvidence(acceptedSource);
        assertDoesNotThrow(() -> validateCompletedEvidence(valid, acceptedSource));

        for (String key : EVIDENCE_KEYS) {
            Map<String, String> malformed = new LinkedHashMap<>(valid);
            malformed.put(key, "arbitrary nonblank claim");
            assertThrows(
                    AssertionError.class,
                    () -> validateCompletedEvidence(Map.copyOf(malformed), acceptedSource),
                    "accepted malformed completed field: " + key);
        }

        assertThrows(
                AssertionError.class,
                () -> validateCompletedEvidence(valid, "d".repeat(40)),
                "accepted evidence bound to a different source commit");
    }

    @Test
    void completedRecordRejectsLaterSourceCommits() {
        assertDoesNotThrow(() -> validateRecordOwnership(
                "COMPLETE", "a".repeat(40), "a".repeat(40)));
        assertThrows(
                AssertionError.class,
                () -> validateRecordOwnership(
                        "COMPLETE", "b".repeat(40), "a".repeat(40)),
                "completed evidence authenticated a later source commit");
    }

    private static void validateRecordOwnership(
            String state, String currentCommit, String recordCommit) {
        assertEquals(
                currentCommit,
                recordCommit,
                "release record is not owned by HEAD for state " + state);
    }

    private static Map<String, String> validCompletedEvidence(String acceptedSource) {
        String sha256 = "b".repeat(64);
        String sha512 = "c".repeat(128);
        return Map.ofEntries(
                Map.entry("accepted_source_sha", acceptedSource),
                Map.entry("jar_sha256", sha256),
                Map.entry("jar_sha512", sha512),
                Map.entry(
                        "local_double_build",
                        "PASS;copies=2;junit=335;gametests=59;sha256="
                                + sha256
                                + ";sha512="
                                + sha512),
                Map.entry(
                        "linux_build_ci",
                        "PASS;url=https://github.com/Luskish/afterlight-signal/actions/runs/1;head="
                                + acceptedSource),
                Map.entry(
                        "visual_ci",
                        "PASS;url=https://github.com/Luskish/afterlight-signal/actions/runs/2;head="
                                + acceptedSource
                                + ";screenshots=22"),
                Map.entry(
                        "visual_artifact",
                        "PASS;name=afterlight-visual-acceptance-"
                                + acceptedSource
                                + ";manifest_sha256="
                                + "d".repeat(64)),
                Map.entry(
                        "visual_review",
                        "APPROVE;screenshots=22;duplicates=0;blank=0;wrong_state=0;wrong_location=0;unloaded=0;placeholder=0;concept=0"),
                Map.entry(
                        "route_evidence",
                        "PASS;log_sha256="
                                + "e".repeat(64)
                                + ";prepare=OK;verify=OK;outbound=SUCCESS;return=SUCCESS;repeated_writes=0"),
                Map.entry("final_review", "APPROVE;critical=0;important=0"),
                Map.entry(
                        "public_release",
                        "PASS;url=https://github.com/Luskish/afterlight-signal/releases/tag/v0.2.0;assets=3;byte_equal=true"));
    }

    private static void validateCompletedEvidence(
            Map<String, String> fields, String expectedSource) {
        String acceptedSource = fields.get("accepted_source_sha");
        assertMatches(acceptedSource, "[0-9a-f]{40}", "accepted_source_sha");
        assertEquals(expectedSource, acceptedSource, "completed evidence is not bound to its parent source");

        String sha256 = fields.get("jar_sha256");
        String sha512 = fields.get("jar_sha512");
        assertMatches(sha256, "[0-9a-f]{64}", "jar_sha256");
        assertMatches(sha512, "[0-9a-f]{128}", "jar_sha512");
        assertEquals(
                "PASS;copies=2;junit=335;gametests=59;sha256="
                        + sha256
                        + ";sha512="
                        + sha512,
                fields.get("local_double_build"));
        assertMatches(
                fields.get("linux_build_ci"),
                "PASS;url=https://github\\.com/Luskish/afterlight-signal/actions/runs/[1-9][0-9]*;head="
                        + acceptedSource,
                "linux_build_ci");
        assertMatches(
                fields.get("visual_ci"),
                "PASS;url=https://github\\.com/Luskish/afterlight-signal/actions/runs/[1-9][0-9]*;head="
                        + acceptedSource
                        + ";screenshots=22",
                "visual_ci");
        assertMatches(
                fields.get("visual_artifact"),
                "PASS;name=afterlight-visual-acceptance-"
                        + acceptedSource
                        + ";manifest_sha256=[0-9a-f]{64}",
                "visual_artifact");
        assertEquals(
                "APPROVE;screenshots=22;duplicates=0;blank=0;wrong_state=0;wrong_location=0;unloaded=0;placeholder=0;concept=0",
                fields.get("visual_review"));
        assertMatches(
                fields.get("route_evidence"),
                "PASS;log_sha256=[0-9a-f]{64};prepare=OK;verify=OK;outbound=SUCCESS;return=SUCCESS;repeated_writes=0",
                "route_evidence");
        assertEquals("APPROVE;critical=0;important=0", fields.get("final_review"));
        assertEquals(
                "PASS;url=https://github.com/Luskish/afterlight-signal/releases/tag/v0.2.0;assets=3;byte_equal=true",
                fields.get("public_release"));
    }

    private static void assertMatches(String value, String pattern, String field) {
        assertTrue(value != null && value.matches(pattern), "malformed completed field: " + field);
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

    private static Set<String> changedFiles(String commit) throws IOException, InterruptedException {
        return Set.copyOf(lines(git(
                "diff-tree", "--no-commit-id", "--name-only", "-r", commit)));
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
