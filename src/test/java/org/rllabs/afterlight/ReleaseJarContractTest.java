package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
        auditJarContent(RELEASE_JAR);
    }

    @Test
    void jarAuditRejectsSecretAppendedToExpectedBinaryEntry(@TempDir Path temporaryDirectory)
            throws Exception {
        Path mutated = temporaryDirectory.resolve("mutated.jar");
        byte[] marker = privateKeyHeader("EC PRIVATE KEY");
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            try (var output = new ZipOutputStream(Files.newOutputStream(mutated))) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    output.putNextEntry(new ZipEntry(entry.getName()));
                    if (!entry.isDirectory()) {
                        zip.getInputStream(entry).transferTo(output);
                    }
                    if (entry.getName().equals("assets/afterlight/textures/gui/title.png")) {
                        output.write(marker);
                    }
                    output.closeEntry();
                }
            }
        }

        assertThrows(AssertionError.class, () -> auditJarContent(mutated));
    }

    private static void auditJarContent(Path jar) throws Exception {
        byte[] u2014 = new String(Character.toChars(0x2014)).getBytes(StandardCharsets.UTF_8);
        try (var zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] payload = zip.getInputStream(entry).readAllBytes();
                String marker = detectedSecretMarker(payload);
                assertTrue(marker == null, entry.getName() + ": " + marker);
                if (isValidUtf8(payload)) {
                    assertFalse(contains(payload, u2014), entry.getName());
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

            ReleaseClassReferenceScanner.assertSafe(
                    classes, ReleaseClassReferenceScanner.commonRoots(classes));
        }
    }

    @Test
    void classScannerRejectsDormantReferencesToEveryClientNamespace() {
        for (String namespace : List.of(
                "net/minecraft/client/renderer/LevelRenderer",
                "net/neoforged/neoforge/client/event/ScreenEvent",
                "com/mojang/blaze3d/vertex/PoseStack",
                "org/lwjgl/opengl/GL11")) {
            String common = "org/rllabs/afterlight/Fixture";
            Map<String, byte[]> classes = Map.of(
                    common, classWithDormantReference(common, namespace));

            AssertionError error = assertThrows(
                    AssertionError.class,
                    () -> ReleaseClassReferenceScanner.assertSafe(classes, Set.of(common)));

            assertTrue(error.getMessage().matches(".*" + java.util.regex.Pattern.quote(namespace) + ".*"));
        }
    }

    @Test
    void classScannerRejectsProjectClientReachabilityFromEveryCommonRoot() {
        String primary = "org/rllabs/afterlight/Afterlight";
        String dormant = "org/rllabs/afterlight/DormantCommon";
        String client = "org/rllabs/afterlight/client/HiddenClient";
        Map<String, byte[]> classes = Map.of(
                primary, emptyClass(primary),
                dormant, classWithDormantReference(dormant, client),
                client, emptyClass(client));
        Set<String> roots = ReleaseClassReferenceScanner.commonRoots(classes);
        assertEquals(Set.of(primary, dormant), roots);

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> ReleaseClassReferenceScanner.assertSafe(classes, roots));

        assertTrue(error.getMessage().matches(".*" + java.util.regex.Pattern.quote(client) + ".*"));
    }

    @Test
    void classScannerRecursivelyTraversesProjectReferences() {
        String root = "org/rllabs/afterlight/Root";
        String helper = "org/rllabs/afterlight/Helper";
        String client = "org/rllabs/afterlight/client/HiddenClient";
        Map<String, byte[]> classes = Map.of(
                root, classWithDormantReference(root, helper),
                helper, classWithDormantReference(helper, client),
                client, emptyClass(client));

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> ReleaseClassReferenceScanner.assertSafe(classes, Set.of(root)));

        assertTrue(error.getMessage().matches(".*" + java.util.regex.Pattern.quote(client) + ".*"));
    }

    @Test
    void classScannerIgnoresClientNamespaceTextThatIsNotAClassReference() {
        String common = "org/rllabs/afterlight/StringOnly";
        Map<String, byte[]> classes = Map.of(
                common,
                classWithStringConstant(common, "net/minecraft/client/not-a-type"));

        assertDoesNotThrow(() -> ReleaseClassReferenceScanner.assertSafe(classes, Set.of(common)));
    }

    @Test
    void independentArchiveRebuildIsByteForByteIdentical() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        assertTrue(Files.isRegularFile(REBUILT_JAR), "missing rebuilt JAR: " + REBUILT_JAR);
        assertArrayEquals(Files.readAllBytes(RELEASE_JAR), Files.readAllBytes(REBUILT_JAR));
    }

    @Test
    void workflowPinsToolchainRunsExactCiTwiceAndAuditsOutputs() throws Exception {
        ReleaseWorkflowModel workflow = ReleaseWorkflowModel.parse(
                ROOT.resolve(".github/workflows/build.yml"));
        assertEquals("build", workflow.name());
        assertEquals(Set.of("name", "on", "permissions", "jobs"), workflow.topLevelKeys());
        assertEquals(Set.of("push", "pull_request"), workflow.triggers());
        assertEquals(Map.of("contents", "read"), workflow.permissions());
        assertEquals(Set.of("test"), workflow.jobs().keySet());
        ReleaseWorkflowModel.Job job = workflow.jobs().get("test");
        assertEquals("ubuntu-24.04", job.runner());
        assertEquals(Set.of("runs-on", "steps"), job.keys());
        List<ReleaseWorkflowModel.Step> steps = job.steps();
        assertEquals(
                List.of(
                        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
                        "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
                        "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb"),
                steps.stream().map(ReleaseWorkflowModel.Step::uses).filter(java.util.Objects::nonNull).toList());
        List<ReleaseWorkflowModel.Step> checkouts = steps.stream()
                .filter(step -> step.uses() != null && step.uses().startsWith("actions/checkout@"))
                .toList();
        assertEquals(
                List.of(
                        Map.of(
                                "ref", "${{ github.sha }}",
                                "path", "source-a",
                                "clean", "true",
                                "fetch-depth", "1",
                                "persist-credentials", "false"),
                        Map.of(
                                "ref", "${{ github.sha }}",
                                "path", "source-b",
                                "clean", "true",
                                "fetch-depth", "1",
                                "persist-credentials", "false")),
                checkouts.stream().map(ReleaseWorkflowModel.Step::with).toList());
        ReleaseWorkflowModel.Step java = steps.stream()
                .filter(step -> step.uses() != null && step.uses().startsWith("actions/setup-java@"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Map.of(
                        "distribution", "temurin",
                        "java-version", "21.0.12+8.0.LTS",
                        "architecture", "x64"),
                java.with());
        ReleaseWorkflowModel.Step gradle = steps.stream()
                .filter(step -> step.uses() != null && step.uses().startsWith("gradle/actions/setup-gradle@"))
                .findFirst()
                .orElseThrow();
        assertEquals(Map.of("gradle-version", "9.2.1"), gradle.with());
        String exactCommand =
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true --no-daemon --no-build-cache --rerun-tasks";
        List<ReleaseWorkflowModel.Step> builds = steps.stream()
                .filter(step -> exactCommand.equals(step.run()))
                .toList();
        assertEquals(2, builds.size());
        assertEquals(List.of("source-a", "source-b"), builds.stream()
                .map(ReleaseWorkflowModel.Step::workingDirectory)
                .toList());
        assertEquals(
                List.of(
                        Map.of("GRADLE_USER_HOME", "${{ runner.temp }}/gradle-a"),
                        Map.of("GRADLE_USER_HOME", "${{ runner.temp }}/gradle-b")),
                builds.stream().map(ReleaseWorkflowModel.Step::environment).toList());
        assertEquals(3, steps.stream().filter(step -> step.run() != null).count());
        ReleaseWorkflowModel.Step audit = steps.stream()
                .filter(step -> step.run() != null && !step.run().equals(exactCommand))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedComparisonAndAuditCommand(), audit.run());
        List<String> scalarValues = workflowScalarValues(workflow);
        assertTrue(scalarValues.stream().noneMatch(value -> value.matches("(?s).*\\$\\{\\{\\s*secrets\\..*")));
        assertTrue(scalarValues.stream().noneMatch(value -> value.matches(
                "(?is).*(pull_request_target|upload-artifact|gh\\s+release|create-release|publish|id-token\\s*:\\s*write).*")));
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
    void releasePolicyRejectsEverySecretFormatInEveryFileType(@TempDir Path temporaryDirectory)
            throws Exception {
        List<String> paths = List.of(
                "release.pem",
                "release.key",
                "release.bin",
                "release.png",
                "release.dat",
                "release.secret",
                "release.asc",
                "token-a.bin",
                "token-b.bin",
                "token-c.bin",
                "token-d.bin",
                "token-e.bin",
                "token-f.bin",
                "token-g.bin",
                "token-h.bin");
        List<SecretFixture> fixtures = secretFixtures();
        assertEquals(paths.size(), fixtures.size());
        for (int index = 0; index < fixtures.size(); index++) {
            Path repository = temporaryDirectory.resolve("repository-" + index);
            initializeRepository(repository);
            Path relative = Path.of("src/main/resources").resolve(paths.get(index));
            Path source = repository.resolve(relative);
            byte[] content = fixtures.get(index).content();
            byte[] payload = new byte[content.length + 4];
            payload[0] = 0;
            payload[1] = (byte) 0xff;
            System.arraycopy(content, 0, payload, 2, content.length);
            payload[payload.length - 2] = 0;
            payload[payload.length - 1] = 1;
            Files.write(source, payload);
            git(repository, "add", relative.toString());
            git(repository, "commit", "-m", "secret fixture");

            CommandResult result = policy("verify-release", repository);

            assertRejected(
                    result,
                    "secret_marker path="
                            + relative
                            + " marker="
                            + fixtures.get(index).name());
        }
    }

    @Test
    void releasePolicyRejectsU2014InEveryValidUtf8SourceName(@TempDir Path temporaryDirectory)
            throws Exception {
        List<String> paths = List.of("release.sh", "Plugin.kt", "release.kts", "Makefile", "NOTICE");
        for (int index = 0; index < paths.size(); index++) {
            Path repository = temporaryDirectory.resolve("repository-" + index);
            initializeRepository(repository);
            Path relative = Path.of("src/main/resources").resolve(paths.get(index));
            Path source = repository.resolve(relative);
            Files.writeString(
                    source,
                    "forbidden " + new String(Character.toChars(0x2014)) + " punctuation\n");
            git(repository, "add", relative.toString());
            git(repository, "commit", "-m", "forbidden punctuation");

            CommandResult result = policy("verify-release", repository);

            assertRejected(result, "forbidden_u2014 path=" + relative);
        }
    }

    @Test
    void releasePolicyRejectsIgnoredUntrackedSource(@TempDir Path repository) throws Exception {
        initializeRepository(repository);
        Files.writeString(repository.resolve(".gitignore"), "*.jar\nprivate/\n");
        git(repository, "add", ".gitignore");
        git(repository, "commit", "-m", "ignore private source");
        Path source = repository.resolve("private/generated.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Generated {}\n");

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "untracked:private/generated.java");
    }

    @Test
    void releasePolicyRejectsSkipWorktreeSourceDigestMismatch(@TempDir Path repository)
            throws Exception {
        initializeRepository(repository);
        git(repository, "update-index", "--skip-worktree", "build.gradle");
        Files.writeString(repository.resolve("build.gradle"), "plugins { id 'java-library' }\n");
        assertTrue(successful(gitResult(repository, "diff", "--name-only", "HEAD", "--")).isEmpty());

        CommandResult result = policy("verify-release", repository);

        assertRejected(result, "clean working digest does not match HEAD Git objects");
    }

    @Test
    void excludedOutputsNeverInfluenceDigestOrCleanliness(@TempDir Path repository)
            throws Exception {
        initializeRepository(repository);
        String before = successful(policy("digest-working", repository));
        for (String path : List.of(
                ".gradle/cache/state.bin",
                "build/libs/output.jar",
                "config/client.toml",
                "crash-reports/report.txt",
                "logs/latest.log",
                "out/classes/Main.class",
                "run/world/session.lock",
                "run-data/output.json")) {
            Path output = repository.resolve(path);
            Files.createDirectories(output.getParent());
            Files.write(output, new byte[] {1, 2, 3, 4});
        }

        String after = successful(policy("digest-working", repository));
        CommandResult verification = policy("verify-release", repository);

        assertEquals(before, after);
        assertEquals(0, verification.exitCode(), verification.output());
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

    @Test
    void stageReleaseMaterializesExactImmutableHeadBytesAndModes(
            @TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path staging = temporaryDirectory.resolve("staging");
        initializeRepository(repository);
        Path binary = repository.resolve("src/main/resources/payload.bin");
        Files.write(binary, new byte[] {0, 1, 2, (byte) 0xff});
        Path script = repository.resolve("tools/release-check.sh");
        Files.writeString(script, "#!/bin/sh\nexit 0\n");
        Set<PosixFilePermission> executable = Files.getPosixFilePermissions(script);
        executable.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(script, executable);
        git(repository, "add", binary.toString(), script.toString());
        git(repository, "commit", "-m", "staging fixtures");

        CommandResult result = policy("stage-release", repository, staging);

        assertEquals(0, result.exitCode(), result.output());
        assertArrayEquals(
                successfulBytes(gitBytes(repository, "show", "HEAD:src/main/resources/payload.bin")),
                Files.readAllBytes(staging.resolve("src/main/resources/payload.bin")));
        assertArrayEquals(
                successfulBytes(gitBytes(repository, "show", "HEAD:tools/release-check.sh")),
                Files.readAllBytes(staging.resolve("tools/release-check.sh")));
        assertEquals(
                Set.of(PosixFilePermission.OWNER_READ),
                Files.getPosixFilePermissions(staging.resolve("src/main/resources/payload.bin")));
        assertEquals(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
                Files.getPosixFilePermissions(staging.resolve("tools/release-check.sh")));
        assertEquals(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
                Files.getPosixFilePermissions(staging));
        assertFalse(Files.exists(staging.resolve("build")));
    }

    @Test
    void postStageWorkingMutationCannotAlterStagedCompilerInput(
            @TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path staging = temporaryDirectory.resolve("staging");
        initializeRepository(repository);
        Path workingSource = repository.resolve("src/main/java/example/Main.java");
        byte[] committed = Files.readAllBytes(workingSource);
        assertEquals(0, policy("stage-release", repository, staging).exitCode());
        Path stagedSource = staging.resolve("src/main/java/example/Main.java");

        Files.writeString(workingSource, "package example; class Mutated {}\n");

        assertArrayEquals(committed, Files.readAllBytes(stagedSource));
        assertFalse(Files.isWritable(stagedSource));
        assertNotEquals(
                new String(Files.readAllBytes(workingSource), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(stagedSource), StandardCharsets.UTF_8));
    }

    @Test
    void stageReleaseRejectsUnsupportedGitlink(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path staging = temporaryDirectory.resolve("staging");
        initializeRepository(repository);
        String commit = successful(gitResult(repository, "rev-parse", "HEAD"));
        git(
                repository,
                "update-index",
                "--add",
                "--cacheinfo",
                "160000," + commit + ",vendor/module");
        git(repository, "commit", "-m", "gitlink fixture");

        CommandResult result = policy("stage-release", repository, staging);

        assertRejected(result, "unsupported_git_entry mode=160000 type=commit path=vendor/module");
        assertFalse(Files.exists(staging));
    }

    @Test
    void stageReleaseRejectsUnsupportedSymlinkMode(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path staging = temporaryDirectory.resolve("staging");
        initializeRepository(repository);
        Path link = repository.resolve("src/main/resources/linked.bin");
        Files.createSymbolicLink(link, Path.of("mod.json"));
        git(repository, "add", link.toString());
        git(repository, "commit", "-m", "symlink mode fixture");

        CommandResult result = policy("stage-release", repository, staging);

        assertRejected(
                result,
                "unsupported_git_entry mode=120000 type=blob path=src/main/resources/linked.bin");
        assertFalse(Files.exists(staging));
    }

    @Test
    void workingTypeGateRejectsReplacedParentDirectory(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path externalMain = temporaryDirectory.resolve("external-main");
        initializeRepository(repository);
        Files.move(repository.resolve("src/main"), externalMain);
        Files.createSymbolicLink(repository.resolve("src/main"), externalMain);

        CommandResult result = policy("verify-working-types", repository);

        assertRejected(
                result,
                "non_directory_working_parent path=src/main/java/example/Main.java parent=src/main kind=symbolic_link");
    }

    @Test
    void releaseBuildBindsEveryCompiledSourceAndResourceTaskToStaging() {
        assumeTrue(Boolean.getBoolean("afterlight.release.build"));
        Path staging = Path.of(System.getProperty("afterlight.release.staging.root"));
        assertEquals(
                Set.of(staging.resolve("src/main/java").toString()),
                pathProperty("afterlight.release.main.java.roots"));
        assertEquals(
                Set.of(staging.resolve("src/main/resources").toString()),
                pathProperty("afterlight.release.main.resources.roots"));
        assertEquals(
                Set.of(staging.resolve("src/test/java").toString()),
                pathProperty("afterlight.release.test.java.roots"));
        assertEquals(
                Set.of(staging.resolve("src/test/resources").toString()),
                pathProperty("afterlight.release.test.resources.roots"));
        for (String task : List.of(
                "compileJava", "processResources", "compileTestJava", "processTestResources")) {
            assertEquals(
                    "true",
                    System.getProperty("afterlight.release.stage.precedes." + task),
                    task);
        }
    }

    @Test
    void releaseResourceOutputsNormalizeStagedPermissions() throws Exception {
        assumeTrue(Boolean.getBoolean("afterlight.release.build"));
        for (Path output : List.of(
                ROOT.resolve("build/resources/main/META-INF"),
                ROOT.resolve("build/resources/main/META-INF/neoforge.mods.toml"),
                ROOT.resolve("build/resources/test/routes"),
                ROOT.resolve("build/resources/test/routes/valid.json"))) {
            assertTrue(Files.exists(output), output.toString());
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(output);
            assertTrue(
                    permissions.contains(PosixFilePermission.OWNER_WRITE),
                    output + " must remain writable build output");
        }
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

    private static CommandResult policy(String command, Path repository, Path staging)
            throws Exception {
        return execute(
                repository,
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                repository.resolve("tools/ReleaseSourcePolicy.java").toString(),
                command,
                repository.toString(),
                staging.toString());
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

    private static boolean isValidUtf8(byte[] content) {
        return Arrays.equals(
                content,
                new String(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    }

    private static String detectedSecretMarker(byte[] content) {
        for (SecretFixture fixture : secretFixtures().subList(0, 7)) {
            if (contains(content, fixture.content())) {
                return fixture.name();
            }
        }
        for (TokenPattern pattern : tokenPatterns()) {
            if (containsToken(content, pattern)) {
                return pattern.name();
            }
        }
        return null;
    }

    private static boolean containsToken(byte[] content, TokenPattern pattern) {
        for (int offset = 0; offset <= content.length - pattern.prefix().length; offset++) {
            if (!matchesAt(content, pattern.prefix(), offset)) {
                continue;
            }
            int length = 0;
            int valueStart = offset + pattern.prefix().length;
            while (valueStart + length < content.length
                    && acceptsTokenByte(content[valueStart + length], pattern)) {
                length++;
            }
            if (length >= pattern.minimumLength()) {
                return true;
            }
        }
        return false;
    }

    private static boolean acceptsTokenByte(byte value, TokenPattern pattern) {
        int unsigned = Byte.toUnsignedInt(value);
        boolean alphanumeric = unsigned >= '0' && unsigned <= '9'
                || unsigned >= 'A' && unsigned <= 'Z'
                || unsigned >= 'a' && unsigned <= 'z';
        return alphanumeric
                || pattern.underscore() && value == '_'
                || pattern.hyphen() && value == '-';
    }

    private static boolean matchesAt(byte[] content, byte[] marker, int offset) {
        for (int index = 0; index < marker.length; index++) {
            if (content[offset + index] != marker[index]) {
                return false;
            }
        }
        return true;
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

    private static byte[] emptyClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V21,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                internalName,
                null,
                "java/lang/Object",
                null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithDormantReference(
            String internalName, String referencedInternalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V21,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                internalName,
                null,
                "java/lang/Object",
                null);
        writer.visitMethod(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
                        "dormant",
                        "()L" + referencedInternalName + ";",
                        null,
                        null)
                .visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithStringConstant(String internalName, String value) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V21,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                internalName,
                null,
                "java/lang/Object",
                null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "dormant",
                "()V",
                null,
                null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String expectedComparisonAndAuditCommand() {
        return """
                first=source-a/build/libs/afterlight-signal-0.1.0+1.21.1.jar
                second=source-b/build/libs/afterlight-signal-0.1.0+1.21.1.jar
                shasum -a 256 "$first"
                shasum -a 256 "$second"
                cmp --silent "$first" "$second"
                test "$(shasum -a 256 "$first" | cut -d ' ' -f 1)" = "$(shasum -a 256 "$second" | cut -d ' ' -f 1)"
                java source-a/tools/ReleaseSourcePolicy.java verify-release source-a
                java source-b/tools/ReleaseSourcePolicy.java verify-release source-b
                test -z "$(git -C source-a ls-files '*.jar')"
                test -z "$(git -C source-b ls-files '*.jar')"
                test -z "$(git -C source-a status --porcelain=v1 --untracked-files=all)"
                test -z "$(git -C source-b status --porcelain=v1 --untracked-files=all)"
                test -z "$(jps -lv | grep -E 'GradleDaemon|GradleWorkerMain' || true)"
                """.stripTrailing();
    }

    private static List<String> workflowScalarValues(ReleaseWorkflowModel workflow) {
        List<String> values = new ArrayList<>();
        values.add(workflow.name());
        values.addAll(workflow.triggers());
        values.addAll(workflow.permissions().keySet());
        values.addAll(workflow.permissions().values());
        for (Map.Entry<String, ReleaseWorkflowModel.Job> jobEntry : workflow.jobs().entrySet()) {
            values.add(jobEntry.getKey());
            values.add(jobEntry.getValue().runner());
            values.addAll(jobEntry.getValue().keys());
            for (ReleaseWorkflowModel.Step step : jobEntry.getValue().steps()) {
                for (String value : List.of(
                        step.name() == null ? "" : step.name(),
                        step.uses() == null ? "" : step.uses(),
                        step.run() == null ? "" : step.run(),
                        step.workingDirectory() == null ? "" : step.workingDirectory())) {
                    values.add(value);
                }
                values.addAll(step.with().keySet());
                values.addAll(step.with().values());
                values.addAll(step.environment().keySet());
                values.addAll(step.environment().values());
            }
        }
        return List.copyOf(values);
    }

    private static Set<String> pathProperty(String name) {
        String value = System.getProperty(name);
        assertTrue(value != null, "missing system property: " + name);
        return Arrays.stream(value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(path -> !path.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<SecretFixture> secretFixtures() {
        return List.of(
                new SecretFixture("GENERIC_PRIVATE_KEY", privateKeyHeader("PRIVATE KEY")),
                new SecretFixture("ENCRYPTED_PRIVATE_KEY", privateKeyHeader("ENCRYPTED PRIVATE KEY")),
                new SecretFixture("RSA_PRIVATE_KEY", privateKeyHeader("RSA PRIVATE KEY")),
                new SecretFixture("EC_PRIVATE_KEY", privateKeyHeader("EC PRIVATE KEY")),
                new SecretFixture("DSA_PRIVATE_KEY", privateKeyHeader("DSA PRIVATE KEY")),
                new SecretFixture("OPENSSH_PRIVATE_KEY", privateKeyHeader("OPENSSH PRIVATE KEY")),
                new SecretFixture("PGP_PRIVATE_KEY", privateKeyHeader("PGP PRIVATE KEY BLOCK")),
                new SecretFixture("GITHUB_GHP", classicGithubToken("gh" + "p_")),
                new SecretFixture("GITHUB_GHO", classicGithubToken("gh" + "o_")),
                new SecretFixture("GITHUB_GHU", classicGithubToken("gh" + "u_")),
                new SecretFixture("GITHUB_GHS", classicGithubToken("gh" + "s_")),
                new SecretFixture("GITHUB_GHR", classicGithubToken("gh" + "r_")),
                new SecretFixture(
                        "GITHUB_PAT",
                        ("github" + "_pat_" + "A".repeat(22) + "_" + "B".repeat(59))
                                .getBytes(StandardCharsets.US_ASCII)),
                new SecretFixture(
                        "OPENAI_LEGACY_KEY",
                        ("sk-" + "A".repeat(48)).getBytes(StandardCharsets.US_ASCII)),
                new SecretFixture(
                        "OPENAI_PROJECT_KEY",
                        ("sk-" + "proj-" + "A".repeat(48))
                                .getBytes(StandardCharsets.US_ASCII)));
    }

    private static List<TokenPattern> tokenPatterns() {
        return List.of(
                new TokenPattern("GITHUB_GHP", ("gh" + "p_").getBytes(StandardCharsets.US_ASCII), 36, false, false),
                new TokenPattern("GITHUB_GHO", ("gh" + "o_").getBytes(StandardCharsets.US_ASCII), 36, false, false),
                new TokenPattern("GITHUB_GHU", ("gh" + "u_").getBytes(StandardCharsets.US_ASCII), 36, false, false),
                new TokenPattern("GITHUB_GHS", ("gh" + "s_").getBytes(StandardCharsets.US_ASCII), 36, false, false),
                new TokenPattern("GITHUB_GHR", ("gh" + "r_").getBytes(StandardCharsets.US_ASCII), 36, false, false),
                new TokenPattern("GITHUB_PAT", ("github" + "_pat_").getBytes(StandardCharsets.US_ASCII), 30, true, false),
                new TokenPattern("OPENAI_PROJECT_KEY", ("sk-" + "proj-").getBytes(StandardCharsets.US_ASCII), 20, true, true),
                new TokenPattern("OPENAI_LEGACY_KEY", "sk-".getBytes(StandardCharsets.US_ASCII), 20, false, false));
    }

    private static byte[] privateKeyHeader(String type) {
        return ("-----BEGIN " + type + "-----").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] classicGithubToken(String prefix) {
        return (prefix + "A".repeat(36)).getBytes(StandardCharsets.US_ASCII);
    }

    private record CommandResult(int exitCode, String output) {}

    private record BinaryCommandResult(int exitCode, byte[] output) {}

    private record SourceEntry(String mode, String type, byte[] content) {}

    private record SecretFixture(String name, byte[] content) {}

    private record TokenPattern(
            String name, byte[] prefix, int minimumLength, boolean underscore, boolean hyphen) {}
}
