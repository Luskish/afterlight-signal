package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseJarContractTest {
    private static final byte[] DIGEST_DOMAIN =
            "AFTERLIGHT_RELEASE_SOURCE_TREE_V2\0".getBytes(StandardCharsets.UTF_8);
    private static final Path ROOT = Path.of(
                    System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();
    private static final Path POLICY = ROOT.resolve("tools/ReleaseSourcePolicy.java");
    private static final Path RELEASE_JAR = Path.of(System.getProperty(
            "afterlight.release.jar",
            ROOT.resolve("build/libs/afterlight-signal-0.1.0+1.21.1.jar").toString()));
    private static final Path REBUILT_JAR = Path.of(System.getProperty(
            "afterlight.rebuilt.release.jar",
            ROOT.resolve("build/reproducible-libs/afterlight-signal-0.1.0+1.21.1.jar")
                    .toString()));
    private static final LocalDateTime REPRODUCIBLE_TIMESTAMP =
            LocalDateTime.of(1980, 2, 1, 0, 0);
    private static final List<String> EXPECTED_ENTRIES = List.of(
            "META-INF/",
            "META-INF/MANIFEST.MF",
            "org/",
            "org/rllabs/",
            "org/rllabs/afterlight/",
            "org/rllabs/afterlight/Afterlight.class",
            "org/rllabs/afterlight/EchoContent.class",
            "org/rllabs/afterlight/client/",
            "org/rllabs/afterlight/client/AfterlightClient.class",
            "org/rllabs/afterlight/client/EchoScreen$1.class",
            "org/rllabs/afterlight/client/EchoScreen$ClaimFingerprint.class",
            "org/rllabs/afterlight/client/EchoScreen$MutationFingerprint.class",
            "org/rllabs/afterlight/client/EchoScreen$MutationKey.class",
            "org/rllabs/afterlight/client/EchoScreen$PendingMutation.class",
            "org/rllabs/afterlight/client/EchoScreen$PinFingerprint.class",
            "org/rllabs/afterlight/client/EchoScreen$SubmitFingerprint.class",
            "org/rllabs/afterlight/client/EchoScreen.class",
            "org/rllabs/afterlight/client/EchoScreenLayout$Mode.class",
            "org/rllabs/afterlight/client/EchoScreenLayout$PaneLabels.class",
            "org/rllabs/afterlight/client/EchoScreenLayout$Rect.class",
            "org/rllabs/afterlight/client/EchoScreenLayout.class",
            "org/rllabs/afterlight/client/EchoScreenModel$1.class",
            "org/rllabs/afterlight/client/EchoScreenModel$Action.class",
            "org/rllabs/afterlight/client/EchoScreenModel$ActionState.class",
            "org/rllabs/afterlight/client/EchoScreenModel.class",
            "org/rllabs/afterlight/client/SignalClientConfig.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$ButtonDecoration.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$ClientAccess.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$CoverCrop.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$Destination.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$MenuGeometry.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$MinecraftClientAccess.class",
            "org/rllabs/afterlight/client/SignalTitleScreen$SignalButton.class",
            "org/rllabs/afterlight/client/SignalTitleScreen.class",
            "org/rllabs/afterlight/client/SignalTitleScreenHook.class",
            "org/rllabs/afterlight/client/integration/",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$ClientAccess.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$FtbClientAccess.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$FtbSynchronizedState.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$QuestState.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$RewardState.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$SynchronizedState.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway$TaskState.class",
            "org/rllabs/afterlight/client/integration/FtbQuestGateway.class",
            "org/rllabs/afterlight/echo/",
            "org/rllabs/afterlight/echo/EchoBond.class",
            "org/rllabs/afterlight/echo/EchoCommands.class",
            "org/rllabs/afterlight/echo/EchoIdentity.class",
            "org/rllabs/afterlight/echo/EchoInventory.class",
            "org/rllabs/afterlight/echo/EchoItem.class",
            "org/rllabs/afterlight/echo/EchoPlayerEvents$PendingFirstIssue.class",
            "org/rllabs/afterlight/echo/EchoPlayerEvents.class",
            "org/rllabs/afterlight/echo/EchoRecoveryService$RecoveryResult.class",
            "org/rllabs/afterlight/echo/EchoRecoveryService$RecoveryStatus.class",
            "org/rllabs/afterlight/echo/EchoRecoveryService.class",
            "org/rllabs/afterlight/echo/EchoRuntimeService$1.class",
            "org/rllabs/afterlight/echo/EchoRuntimeService$OpenStatus.class",
            "org/rllabs/afterlight/echo/EchoRuntimeService$PlayerInventory.class",
            "org/rllabs/afterlight/echo/EchoRuntimeService.class",
            "org/rllabs/afterlight/integration/",
            "org/rllabs/afterlight/integration/EchoQuestGateway.class",
            "org/rllabs/afterlight/network/",
            "org/rllabs/afterlight/network/AfterlightPayloads.class",
            "org/rllabs/afterlight/network/OpenEchoRequest.class",
            "org/rllabs/afterlight/network/OpenEchoScreen.class",
            "org/rllabs/afterlight/route/",
            "org/rllabs/afterlight/route/EchoQuestSnapshot$RewardSnapshot.class",
            "org/rllabs/afterlight/route/EchoQuestSnapshot$TaskSnapshot.class",
            "org/rllabs/afterlight/route/EchoQuestSnapshot.class",
            "org/rllabs/afterlight/route/EchoRecommendation$Kind.class",
            "org/rllabs/afterlight/route/EchoRecommendation.class",
            "org/rllabs/afterlight/route/EchoRoute$Segment.class",
            "org/rllabs/afterlight/route/EchoRoute.class",
            "org/rllabs/afterlight/route/EchoRouteLoader$RouteValidationException.class",
            "org/rllabs/afterlight/route/EchoRouteLoader$SegmentDraft.class",
            "org/rllabs/afterlight/route/EchoRouteLoader$VisitState.class",
            "org/rllabs/afterlight/route/EchoRouteLoader.class",
            "org/rllabs/afterlight/route/EchoRouteResolver.class",
            "pack.mcmeta",
            "META-INF/afterlight-provenance.json",
            "META-INF/neoforge.mods.toml",
            "assets/",
            "assets/afterlight/",
            "assets/afterlight/lang/",
            "assets/afterlight/lang/en_us.json",
            "assets/afterlight/models/",
            "assets/afterlight/models/item/",
            "assets/afterlight/models/item/echo.json",
            "assets/afterlight/textures/",
            "assets/afterlight/textures/gui/",
            "assets/afterlight/textures/gui/echo_panel.png",
            "assets/afterlight/textures/gui/title.png",
            "assets/afterlight/textures/item/",
            "assets/afterlight/textures/item/echo.png");

    @Test
    void builtJarHasExactInventoryOrderAndReproducibleTimestamps() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            assertEquals(EXPECTED_ENTRIES, entries.stream().map(ZipEntry::getName).toList());
            entries.forEach(entry -> assertEquals(
                    REPRODUCIBLE_TIMESTAMP, entry.getTimeLocal(), entry.getName()));
        }
    }

    @Test
    void builtJarHasExactManifestModMetadataAndProvenance() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            Manifest manifest = new Manifest(zip.getInputStream(requiredEntry(zip, "META-INF/MANIFEST.MF")));
            Map<String, String> attributes = new LinkedHashMap<>();
            manifest.getMainAttributes().forEach(
                    (key, value) -> attributes.put(key.toString(), value.toString()));
            assertEquals(
                    Map.of(
                            Attributes.Name.MANIFEST_VERSION.toString(), "1.0",
                            "Implementation-Title", "AFTERLIGHT Signal",
                            "Implementation-Version", "0.1.0+1.21.1"),
                    attributes);

            assertEquals(
                    """
                    modLoader="javafml"
                    loaderVersion="[4,)"
                    license="All Rights Reserved"

                    [[mods]]
                    modId="afterlight"
                    version="0.1.0"
                    displayName="AFTERLIGHT Signal"
                    authors="RLLabs"
                    description='''
                    The Signal companion mod for AFTERLIGHT.
                    '''

                    [[dependencies.afterlight]]
                    modId="neoforge"
                    type="required"
                    versionRange="[21.1.248]"
                    ordering="NONE"
                    side="BOTH"

                    [[dependencies.afterlight]]
                    modId="minecraft"
                    type="required"
                    versionRange="[1.21.1]"
                    ordering="NONE"
                    side="BOTH"

                    [[dependencies.afterlight]]
                    modId="ftbquests"
                    type="required"
                    versionRange="[2101.1.30]"
                    ordering="AFTER"
                    side="BOTH"

                    [[dependencies.afterlight]]
                    modId="ftblibrary"
                    type="required"
                    versionRange="[2101.1.35]"
                    ordering="AFTER"
                    side="BOTH"

                    [[dependencies.afterlight]]
                    modId="ftbteams"
                    type="required"
                    versionRange="[2101.1.10]"
                    ordering="AFTER"
                    side="BOTH"
                    """,
                    readUtf8(zip, "META-INF/neoforge.mods.toml"));

            JsonObject provenance;
            try (var reader = new InputStreamReader(
                    zip.getInputStream(requiredEntry(zip, "META-INF/afterlight-provenance.json")),
                    StandardCharsets.UTF_8)) {
                provenance = JsonParser.parseReader(reader).getAsJsonObject();
            }
            assertEquals(
                    Set.of(
                            "schema",
                            "sourceTreeDigestSchema",
                            "sourceRepository",
                            "sourceCommit",
                            "sourceTreeSha256",
                            "releaseBuild",
                            "version"),
                    provenance.keySet());
            assertEquals(3, provenance.get("schema").getAsInt());
            assertEquals(2, provenance.get("sourceTreeDigestSchema").getAsInt());
            assertEquals(
                    "https://github.com/Luskish/afterlight-signal",
                    provenance.get("sourceRepository").getAsString());
            assertTrue(provenance.get("sourceCommit").getAsString().matches("[0-9a-f]{40}"));
            assertTrue(provenance.get("sourceTreeSha256").getAsString().matches("[0-9a-f]{64}"));
            assertEquals(
                    System.getProperty("afterlight.source.commit"),
                    provenance.get("sourceCommit").getAsString());
            assertEquals(
                    System.getProperty("afterlight.source.tree.sha256"),
                    provenance.get("sourceTreeSha256").getAsString());
            assertEquals(
                    Boolean.getBoolean("afterlight.release.build"),
                    provenance.get("releaseBuild").getAsBoolean());
            assertEquals("0.1.0+1.21.1", provenance.get("version").getAsString());
        }
    }

    @Test
    void builtJarContainsNoSecretMarkersPrivateKeysOrU2014() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        List<byte[]> forbidden = List.of(
                ("-----BEGIN " + "PRIVATE KEY-----").getBytes(StandardCharsets.US_ASCII),
                ("-----BEGIN RSA " + "PRIVATE KEY-----").getBytes(StandardCharsets.US_ASCII),
                ("github" + "_pat_").getBytes(StandardCharsets.US_ASCII),
                ("sk-" + "proj-").getBytes(StandardCharsets.US_ASCII),
                new String(Character.toChars(0x2014)).getBytes(StandardCharsets.UTF_8));
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] payload = zip.getInputStream(entry).readAllBytes();
                if (!isTextJarEntry(entry.getName())) {
                    continue;
                }
                for (byte[] marker : forbidden) {
                    assertFalse(contains(payload, marker), entry.getName());
                }
            }
        }
    }

    @Test
    void commonEntryClassesCannotReachClientClassesOrClientReferences() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            Map<String, byte[]> classes = new TreeMap<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.getName().startsWith("org/rllabs/afterlight/")
                        && entry.getName().endsWith(".class")) {
                    classes.put(
                            entry.getName().substring(0, entry.getName().length() - 6),
                            zip.getInputStream(entry).readAllBytes());
                }
            }

            var pending = new ArrayDeque<>(List.of("org/rllabs/afterlight/Afterlight"));
            var reachable = new LinkedHashSet<String>();
            while (!pending.isEmpty()) {
                String className = pending.removeFirst();
                if (!reachable.add(className)) {
                    continue;
                }
                byte[] payload = classes.get(className);
                assertTrue(payload != null, "missing reachable class: " + className);
                assertFalse(
                        className.startsWith("org/rllabs/afterlight/client/"),
                        "common entry reaches client class: " + className);
                assertFalse(
                        contains(
                                payload,
                                "net/minecraft/client/".getBytes(StandardCharsets.US_ASCII)),
                        "common class references Minecraft client code: " + className);
                for (String candidate : classes.keySet()) {
                    if (!reachable.contains(candidate)
                            && contains(payload, candidate.getBytes(StandardCharsets.US_ASCII))) {
                        pending.addLast(candidate);
                    }
                }
            }
        }
    }

    @Test
    void independentArchiveRebuildIsByteForByteIdentical() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        assertTrue(Files.isRegularFile(REBUILT_JAR), "missing rebuilt JAR: " + REBUILT_JAR);
        assertArrayEquals(Files.readAllBytes(RELEASE_JAR), Files.readAllBytes(REBUILT_JAR));
    }

    @Test
    void workflowPinsToolchainRunsExactCiTwiceAndAuditsOutputs() throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        assertTrue(workflow.contains(
                "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"));
        assertTrue(workflow.contains(
                "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961"));
        assertTrue(workflow.contains(
                "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb"));
        assertTrue(workflow.contains("runs-on: ubuntu-24.04"));
        assertTrue(workflow.contains("distribution: temurin"));
        assertTrue(workflow.contains("java-version: '21.0.12+8.0.LTS'"));
        assertTrue(workflow.contains("gradle-version: '9.2.1'"));
        String exactCommand =
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true --no-daemon --no-build-cache --rerun-tasks";
        assertEquals(2, occurrences(workflow, exactCommand));
        assertTrue(workflow.contains("shasum -a 256"));
        assertTrue(workflow.contains("cmp --silent"));
        assertTrue(workflow.contains("git ls-files '*.jar'"));
        assertTrue(workflow.contains("git status --porcelain=v1 --untracked-files=all"));
        assertFalse(Files.exists(ROOT.resolve("gradlew")));
        assertFalse(Files.exists(ROOT.resolve("gradle/wrapper/gradle-wrapper.jar")));
    }

    @Test
    void cleanCommittedTreeHasIndependentMatchingDigests(@TempDir Path repository)
            throws Exception {
        initializeRepository(repository);

        CommandResult verification = policy("verify-release", repository);
        String working = successful(policy("digest-working", repository));
        String committed = successful(policy("digest-head", repository));

        assertEquals(0, verification.exitCode(), verification.output());
        assertEquals(committed, working);
        assertEquals(independentHeadDigest(repository), committed);
        assertTrue(working.matches("[0-9a-f]{64}"), working);
    }

    @Test
    void releasePolicyRejectsDirtyTrackedSource(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Files.writeString(repository.resolve("build.gradle"), "plugins { id 'java-library' }\n");

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "tracked:build.gradle");
    }

    @Test
    void releasePolicyRejectsUntrackedRelevantSource(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Path source = repository.resolve("src/main/resources/generated-policy.json");
        Files.writeString(source, "{}\n");

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "untracked:src/main/resources/generated-policy.json");
    }

    @Test
    void releasePolicyRejectsSymlinkSource(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Path source = repository.resolve("src/main/resources/linked.json");
        Files.createSymbolicLink(source, Path.of("mod.json"));
        git(repository, "add", source.toString());
        git(repository, "commit", "-m", "symlink source");

        CommandResult result = policy("verify-release", repository);

        assertRejected(
                result,
                "non_regular_working_input path=src/main/resources/linked.json kind=symbolic_link");
    }

    @Test
    void releasePolicyRejectsHardlinkAlias(@TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Files.createLink(
                temporaryDirectory.resolve("build.gradle.alias"),
                repository.resolve("build.gradle"));

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "hardlink_count path=build.gradle expected=1 actual=2");
    }

    @Test
    void releasePolicyRejectsSourceDigestMismatch(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        git(repository, "update-index", "--assume-unchanged", "build.gradle");
        Files.writeString(repository.resolve("build.gradle"), "plugins { id 'java-library' }\n");
        assertTrue(successful(gitResult(repository, "diff", "--name-only", "HEAD", "--")).isEmpty());

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "clean working digest does not match HEAD Git objects");
    }

    @Test
    void releasePolicyRejectsSecretMarker(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Path source = repository.resolve("src/main/resources/credential.txt");
        Files.writeString(source, "github" + "_pat_" + "A".repeat(32) + "\n");
        git(repository, "add", source.toString());
        git(repository, "commit", "-m", "secret marker");

        CommandResult result = policy("verify-release", repository);

        assertRejected(
                result,
                "secret_marker path=src/main/resources/credential.txt marker=GITHUB_PAT");
    }

    @Test
    void releasePolicyRejectsU2014(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Path source = repository.resolve("README.md");
        Files.writeString(
                source,
                "forbidden " + new String(Character.toChars(0x2014)) + " punctuation\n");
        git(repository, "add", source.toString());
        git(repository, "commit", "-m", "forbidden punctuation");

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "forbidden_u2014 path=README.md");
    }

    @Test
    void buildOutputNeverChangesSourceDigest(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        String before = successful(policy("digest-working", repository));
        Path output = repository.resolve("build/libs/afterlight-signal.jar");
        Files.createDirectories(output.getParent());
        Files.write(output, new byte[] {1, 2, 3, 4});

        String after = successful(policy("digest-working", repository));
        CommandResult verification = policy("verify-release", repository);

        assertEquals(before, after);
        assertEquals(0, verification.exitCode(), verification.output());
    }

    @Test
    void executableModeChangesTheSourceDigest(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        String regular = successful(policy("digest-head", repository));
        Path buildFile = repository.resolve("build.gradle");
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(buildFile);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(buildFile, permissions);
        git(repository, "add", "build.gradle");
        git(repository, "commit", "-m", "executable source");

        String executable = successful(policy("digest-head", repository));

        assertNotEquals(regular, executable);
        assertEquals(executable, successful(policy("digest-working", repository)));
    }

    private static void initializeRepository(Path repository) throws Exception {
        Files.createDirectories(repository.resolve("src/main/java/example"));
        Files.createDirectories(repository.resolve("src/main/resources"));
        Files.createDirectories(repository.resolve("src/test/java/example"));
        Files.createDirectories(repository.resolve("gradle"));
        Files.createDirectories(repository.resolve(".github/workflows"));
        Files.createDirectories(repository.resolve("tools"));
        Files.copy(POLICY, repository.resolve("tools/ReleaseSourcePolicy.java"));
        Files.writeString(repository.resolve(".gitignore"), "*.jar\n");
        Files.writeString(repository.resolve("README.md"), "fixture\n");
        Files.writeString(repository.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(repository.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(repository.resolve("gradle.lockfile"), "empty=\n");
        Files.writeString(
                repository.resolve("gradle/verification-metadata.xml"),
                "<verification-metadata/>\n");
        Files.writeString(repository.resolve("src/main/java/example/Main.java"), "package example;\n");
        Files.writeString(repository.resolve("src/main/resources/mod.json"), "{}\n");
        Files.writeString(repository.resolve("src/test/java/example/MainTest.java"), "package example;\n");
        Files.writeString(repository.resolve(".github/workflows/build.yml"), "name: build\n");
        git(repository, "init");
        git(repository, "config", "user.name", "Afterlight Test");
        git(repository, "config", "user.email", "afterlight@example.invalid");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "fixture");
    }

    private static CommandResult policy(String command, Path repository) throws Exception {
        return execute(
                repository,
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                repository.resolve("tools/ReleaseSourcePolicy.java").toString(),
                command,
                repository.toString());
    }

    private static void git(Path repository, String... arguments) throws Exception {
        CommandResult result = gitResult(repository, arguments);
        assertEquals(0, result.exitCode(), result.output());
    }

    private static CommandResult gitResult(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        return execute(repository, command.toArray(String[]::new));
    }

    private static String successful(CommandResult result) {
        assertEquals(0, result.exitCode(), result.output());
        return result.output().strip();
    }

    private static CommandResult execute(Path directory, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, new String(output, StandardCharsets.UTF_8));
    }

    private static void assertRejected(CommandResult result, String expected) {
        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains(expected), result.output());
    }

    private static String independentHeadDigest(Path repository) throws Exception {
        TreeMap<String, SourceEntry> entries = new TreeMap<>();
        byte[] listing = successfulBytes(gitBytes(
                repository, "ls-tree", "-r", "-z", "--full-tree", "HEAD"));
        int start = 0;
        for (int index = 0; index < listing.length; index++) {
            if (listing[index] != 0) {
                continue;
            }
            String line = new String(
                    Arrays.copyOfRange(listing, start, index), StandardCharsets.UTF_8);
            int separator = line.indexOf('\t');
            String[] metadata = line.substring(0, separator).split(" ");
            String path = line.substring(separator + 1);
            start = index + 1;
            if (!isReleaseRelevant(path)) {
                continue;
            }
            assertEquals("blob", metadata[1]);
            assertTrue(metadata[0].equals("100644") || metadata[0].equals("100755"));
            entries.put(
                    path,
                    new SourceEntry(
                            metadata[0],
                            metadata[1],
                            successfulBytes(gitBytes(
                                    repository, "cat-file", "blob", metadata[2]))));
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(DIGEST_DOMAIN);
        for (Map.Entry<String, SourceEntry> mapEntry : entries.entrySet()) {
            SourceEntry entry = mapEntry.getValue();
            updateLengthPrefixed(digest, entry.mode().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, entry.type().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, mapEntry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(entry.content().length).array());
            digest.update(entry.content());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static BinaryCommandResult gitBytes(Path repository, String... arguments)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        return new BinaryCommandResult(exitCode, output.toByteArray());
    }

    private static byte[] successfulBytes(BinaryCommandResult result) {
        assertEquals(
                0,
                result.exitCode(),
                new String(result.output(), StandardCharsets.UTF_8));
        return result.output();
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static boolean isReleaseRelevant(String path) {
        return List.of(".git/", ".gradle/", "build/", "logs/", "run/", "run-data/")
                .stream()
                .noneMatch(path::startsWith);
    }

    private static ZipEntry requiredEntry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing JAR entry: " + name);
        return entry;
    }

    private static String readUtf8(ZipFile zip, String name) throws IOException {
        return new String(
                zip.getInputStream(requiredEntry(zip, name)).readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static boolean contains(byte[] payload, byte[] marker) {
        if (marker.length == 0 || marker.length > payload.length) {
            return false;
        }
        outer:
        for (int offset = 0; offset <= payload.length - marker.length; offset++) {
            for (int index = 0; index < marker.length; index++) {
                if (payload[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isTextJarEntry(String name) {
        return name.endsWith(".class")
                || name.endsWith(".json")
                || name.endsWith(".mcmeta")
                || name.endsWith(".toml")
                || name.endsWith(".MF");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private record CommandResult(int exitCode, String output) {}

    private record BinaryCommandResult(int exitCode, byte[] output) {}

    private record SourceEntry(String mode, String type, byte[] content) {}
}
