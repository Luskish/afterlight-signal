package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class VisualWorkflowContractTest {
    private static final Pattern PACKAGE_PIN = Pattern.compile(
            "^([a-z0-9][a-z0-9+.-]*)=([0-9][A-Za-z0-9.+:~-]*)(?:\\s+\\\\)?$");
    private static final Path ROOT = Path.of(System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void parsedWorkflowHasSafeManualAndFilteredPremergeTriggers() throws Exception {
        Map<String, Object> workflow = workflow();
        Map<String, Object> triggers = map(workflow.get("on"));

        assertEquals(Set.of("workflow_dispatch", "pull_request"), triggers.keySet());
        Map<String, Object> pullRequest = map(triggers.get("pull_request"));
        assertEquals(
                List.of("opened", "synchronize", "reopened", "ready_for_review"),
                list(pullRequest.get("types")));
        assertTrue(list(pullRequest.get("paths")).containsAll(List.of(
                "src/**",
                "build.gradle",
                "settings.gradle",
                "gradle.properties",
                "gradle/**",
                "tools/run-visual-acceptance-linux.sh",
                "tools/visual-acceptance-lib.sh",
                ".github/workflows/visual-acceptance.yml")));
        assertFalse(triggers.containsKey("pull_request_target"));
        assertFalse(triggers.containsKey("push"));
    }

    @Test
    void parsedWorkflowPinsActionsAndUbuntuPackageSnapshot() throws Exception {
        Map<String, Object> workflow = workflow();
        Map<String, Object> capture = map(map(workflow.get("jobs")).get("capture"));
        assertEquals("ubuntu-24.04", capture.get("runs-on"));
        assertEquals(Map.of("contents", "read"), map(workflow.get("permissions")));

        List<Map<String, Object>> steps = maps(capture.get("steps"));
        steps.stream()
                .map(step -> step.get("uses"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .forEach(action -> assertTrue(
                        action.matches("[^@]+@[0-9a-f]{40}"), "unpinned action: " + action));

        String install = runStep(steps, "Install pinned Xvfb and Mesa snapshot");
        assertTrue(install.contains("https://snapshot.ubuntu.com/ubuntu/20250115T000000Z/"));
        assertTrue(install.contains("mesa-utils"));
        assertTrue(install.contains("libgl1-mesa-dri"));
        assertTrue(install.contains("xvfb"));
        assertTrue(install.contains("--allow-downgrades"));
        assertTrue(install.contains("xvfb=2:21.1.12-1ubuntu1.1"));
        assertTrue(install.contains("xauth=1:1.1.2-1build1"));
        assertTrue(install.contains("libgl1-mesa-dri=24.0.9-0ubuntu0.3"));
        assertTrue(install.contains("libglx-mesa0=24.0.9-0ubuntu0.3"));
        assertTrue(install.contains("mesa-utils=9.0.0-2"));
        assertEquals(
                "./tools/run-visual-acceptance-linux.sh",
                runStep(steps, "Render acceptance artifacts"));

        Map<String, Object> upload = steps.stream()
                .filter(step -> "Upload rendered PNG inventory".equals(step.get("name")))
                .findFirst()
                .map(step -> map(step.get("with")))
                .orElseThrow();
        assertEquals("build/visual-artifacts", upload.get("path"));
        assertEquals("error", upload.get("if-no-files-found"));
        assertEquals(14, upload.get("retention-days"));
    }

    @Test
    void parsedWorkflowDisablesMirrorIndirectionAndDefinesSignedSnapshotSource()
            throws Exception {
        Map<String, Object> workflow = workflow();
        Map<String, Object> capture = map(map(workflow.get("jobs")).get("capture"));
        String install = runStep(
                maps(capture.get("steps")), "Install pinned Xvfb and Mesa snapshot");

        assertTrue(
                install.contains(
                        "grep -Eq '^URIs:[[:space:]]+mirror\\+file:"
                                + "/etc/apt/apt-mirrors\\.txt$' "
                                + "/etc/apt/sources.list.d/ubuntu.sources"),
                "workflow does not authenticate the runner mirror+file source");
        assertTrue(
                install.contains(
                        "sudo mv /etc/apt/sources.list.d/ubuntu.sources "
                                + "/etc/apt/disabled-sources/ubuntu.sources"),
                "workflow leaves the mirror+file source active");
        assertTrue(
                install.contains(
                        "sudo mv /etc/apt/apt-mirrors.txt "
                                + "/etc/apt/disabled-sources/apt-mirrors.txt"),
                "workflow leaves the runner mirror list active");
        assertEquals(
                Map.of(
                        "Types", "deb",
                        "URIs", "https://snapshot.ubuntu.com/ubuntu/20250115T000000Z/",
                        "Suites", "noble noble-updates noble-security",
                        "Components", "main restricted universe multiverse",
                        "Signed-By", "/usr/share/keyrings/ubuntu-archive-keyring.gpg"),
                deb822Source(install));
        assertTrue(
                install.contains("Active live Ubuntu mirror remained after snapshot setup"),
                "workflow does not fail closed when an active live mirror remains");
        assertFalse(install.contains("sudo sed -Ei"));
        assertFalse(install.contains("azure.archive.ubuntu.com"));
        assertFalse(install.contains("archive.ubuntu.com"));
        assertFalse(install.contains("security.ubuntu.com"));
    }

    @Test
    void visualRuntimePackageContractIsPinnedVerifiedAndRecorded() throws Exception {
        Map<String, Object> workflow = workflow();
        Map<String, Object> capture = map(map(workflow.get("jobs")).get("capture"));
        String install = runStep(
                maps(capture.get("steps")), "Install pinned Xvfb and Mesa snapshot");
        Map<String, String> expectedPins = Map.ofEntries(
                Map.entry("xvfb", "2:21.1.12-1ubuntu1.1"),
                Map.entry("xauth", "1:1.1.2-1build1"),
                Map.entry("libgl1-mesa-dri", "24.0.9-0ubuntu0.3"),
                Map.entry("libglx-mesa0", "24.0.9-0ubuntu0.3"),
                Map.entry("libglapi-mesa", "24.0.9-0ubuntu0.3"),
                Map.entry("libegl-mesa0", "24.0.9-0ubuntu0.3"),
                Map.entry("libgbm1", "24.0.9-0ubuntu0.3"),
                Map.entry("mesa-utils", "9.0.0-2"));
        Map<String, String> expectedMesaRuntime = Map.of(
                "libgl1-mesa-dri", "24.0.9-0ubuntu0.3",
                "libglx-mesa0", "24.0.9-0ubuntu0.3",
                "libglapi-mesa", "24.0.9-0ubuntu0.3",
                "libegl-mesa0", "24.0.9-0ubuntu0.3",
                "libgbm1", "24.0.9-0ubuntu0.3");
        Map<String, String> actualPins = packagePins(install);
        Map<String, String> actualMesaRuntime = actualPins.entrySet().stream()
                .filter(entry -> "24.0.9-0ubuntu0.3".equals(entry.getValue()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
        String runner = Files.readString(ROOT.resolve("tools/run-visual-acceptance-linux.sh"));

        assertAll(
                () -> assertEquals(expectedPins, actualPins, "incomplete visual package pins"),
                () -> assertEquals(
                        expectedMesaRuntime,
                        actualMesaRuntime,
                        "Mesa runtime closure is incomplete or includes unrelated packages"),
                () -> assertTrue(
                        install.contains("for package_pin in \"${pinned_packages[@]}\"; do"),
                        "installed versions are not checked for every pin"),
                () -> assertTrue(
                        install.contains(
                                "installed_version=\"$(dpkg-query -W -f='${Version}' "
                                        + "\"$package_name\")\""),
                        "installed versions are not read through dpkg-query"),
                () -> assertTrue(
                        install.contains("Pinned package version mismatch:"),
                        "version mismatch does not fail loudly"),
                () -> assertEquals(
                        expectedPins.keySet(),
                        packageVersionInventory(runner),
                        "ubuntu-package-versions.txt inventory is incomplete"));
    }

    @Test
    void visualShellScriptsParseAndRendererHelperAcceptsOnlySoftwareMesa(@TempDir Path temp)
            throws Exception {
        Path runner = ROOT.resolve("tools/run-visual-acceptance-linux.sh");
        Path helper = ROOT.resolve("tools/visual-acceptance-lib.sh");
        assertEquals(0, command("bash", "-n", runner.toString()).exitCode());
        assertEquals(0, command("bash", "-n", helper.toString()).exitCode());
        assertEquals(
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE),
                Files.getPosixFilePermissions(runner));

        Path glxinfo = temp.resolve("glxinfo.txt");
        Files.writeString(glxinfo, """
                OpenGL vendor string: Mesa/X.org
                OpenGL renderer string: llvmpipe (LLVM 19.1.7, 256 bits)
                OpenGL core profile version string: 4.5 (Core Profile) Mesa 24.3.4
                """);
        String source = shellQuote(helper.toString());
        String fixture = shellQuote(glxinfo.toString());
        CommandResult approved = command(
                "bash",
                "-c",
                "source " + source + "; visual_assert_approved_glxinfo " + fixture);
        assertEquals(0, approved.exitCode(), approved.output());
        assertEquals(
                "llvmpipe (LLVM 19.1.7, 256 bits)",
                command(
                                "bash",
                                "-c",
                                "source " + source + "; visual_glxinfo_field "
                                        + fixture
                                        + " 'OpenGL renderer string'")
                        .output()
                        .trim());

        Files.writeString(glxinfo, """
                OpenGL vendor string: NVIDIA Corporation
                OpenGL renderer string: NVIDIA GeForce RTX
                OpenGL core profile version string: 4.6
                """);
        assertTrue(command(
                        "bash",
                        "-c",
                        "source " + source + "; visual_assert_approved_glxinfo " + fixture)
                .exitCode()
                != 0);

        Path screenshots = Files.createDirectories(temp.resolve("screenshots"));
        Files.writeString(screenshots.resolve("first.png"), "first");
        Files.writeString(screenshots.resolve("second.png"), "second");
        String artifactRoot = shellQuote(temp.toString());
        CommandResult exactInventory = command(
                "bash",
                "-c",
                "source "
                        + source
                        + "; visual_assert_png_inventory "
                        + artifactRoot
                        + " first.png second.png");
        assertEquals(0, exactInventory.exitCode(), exactInventory.output());
        Files.writeString(screenshots.resolve("unexpected.png"), "unexpected");
        assertTrue(command(
                        "bash",
                        "-c",
                        "source "
                                + source
                                + "; visual_assert_png_inventory "
                                + artifactRoot
                                + " first.png second.png")
                .exitCode()
                != 0);
    }

    @Test
    void parsedVisualServerPropertiesAreDeterministicAndLoopbackOnly() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                ROOT.resolve("src/test/resources/visual-acceptance/server.properties"))) {
            properties.load(input);
        }

        assertEquals("127.0.0.1", properties.getProperty("server-ip"));
        assertEquals("false", properties.getProperty("online-mode"));
        assertEquals("25567", properties.getProperty("server-port"));
        assertEquals("afterlight-visual-acceptance", properties.getProperty("level-seed"));
        assertEquals("minecraft:flat", properties.getProperty("level-type"));
        assertEquals("false", properties.getProperty("generate-structures"));
    }

    private static Map<String, Object> workflow() throws Exception {
        Path path = ROOT.resolve(".github/workflows/visual-acceptance.yml");
        assertTrue(Files.isRegularFile(path), "missing isolated visual workflow");
        Load load = new Load(LoadSettings.builder().setLabel(path.toString()).build());
        try (InputStream input = Files.newInputStream(path)) {
            return map(load.loadFromInputStream(input));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static String runStep(List<Map<String, Object>> steps, String name) {
        return steps.stream()
                .filter(step -> name.equals(step.get("name")))
                .map(step -> (String) step.get("run"))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, String> deb822Source(String script) {
        String opening = "sudo tee /etc/apt/sources.list.d/ubuntu-snapshot.sources "
                + ">/dev/null <<'EOF'\n";
        int start = script.indexOf(opening);
        assertTrue(start >= 0, "missing authoritative deb822 snapshot source");
        int contentStart = start + opening.length();
        int end = script.indexOf("\nEOF\n", contentStart);
        assertTrue(end >= 0, "unterminated authoritative deb822 snapshot source");
        return script.substring(contentStart, end).lines()
                .map(line -> line.split(": ", 2))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        parts -> parts[0], parts -> parts[1]));
    }

    private static Map<String, String> packagePins(String script) {
        Map<String, String> pins = new LinkedHashMap<>();
        for (String line : script.lines().toList()) {
            Matcher matcher = PACKAGE_PIN.matcher(line.strip());
            if (matcher.matches()) {
                pins.put(matcher.group(1), matcher.group(2));
            }
        }
        return Map.copyOf(pins);
    }

    private static Set<String> packageVersionInventory(String runner) {
        String opening = "dpkg-query -W -f='${binary:Package}\\t${Version}\\n' \\\n";
        int start = runner.indexOf(opening);
        assertTrue(start >= 0, "missing ubuntu-package-versions.txt query");
        int contentStart = start + opening.length();
        int end = runner.indexOf("  | LC_ALL=C sort >", contentStart);
        assertTrue(end >= 0, "unterminated ubuntu-package-versions.txt query");
        return runner.substring(contentStart, end).lines()
                .flatMap(line -> java.util.Arrays.stream(line.strip().split("\\s+")))
                .filter(token -> !"\\".equals(token))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static CommandResult command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output.toString(StandardCharsets.UTF_8));
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private record CommandResult(int exitCode, String output) {}
}
