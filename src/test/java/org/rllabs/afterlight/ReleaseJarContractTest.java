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
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypeReference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class ReleaseJarContractTest {
    private static final String CROSS_REPOSITORY_ORIGIN =
            "Authenticated from NeoForged Releases and Maven Central";
    private static final String MOJANG_CHECKSUM_ORIGIN =
            "Authenticated from Mojang Libraries published SHA-256";
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
            "org/rllabs/afterlight/client/EchoPaneScroller.class",
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
            "org/rllabs/afterlight/client/EchoTooltip.class",
            "org/rllabs/afterlight/client/FarRelayEffects.class",
            "org/rllabs/afterlight/client/GateRenderer$1.class",
            "org/rllabs/afterlight/client/GateRenderer$Transition.class",
            "org/rllabs/afterlight/client/GateRenderer.class",
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
            "org/rllabs/afterlight/gate/",
            "org/rllabs/afterlight/gate/FtbGateProgressGateway.class",
            "org/rllabs/afterlight/gate/GateActivationService$ActivationCode.class",
            "org/rllabs/afterlight/gate/GateActivationService$ActivationDecision.class",
            "org/rllabs/afterlight/gate/GateActivationService$ActivationRequest.class",
            "org/rllabs/afterlight/gate/GateActivationService.class",
            "org/rllabs/afterlight/gate/GateControllerBlock.class",
            "org/rllabs/afterlight/gate/GateControllerBlockEntity.class",
            "org/rllabs/afterlight/gate/GateFieldBlock.class",
            "org/rllabs/afterlight/gate/GateFieldBlockEntity.class",
            "org/rllabs/afterlight/gate/GateLocalPos.class",
            "org/rllabs/afterlight/gate/GatePattern$GatePart.class",
            "org/rllabs/afterlight/gate/GatePattern.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher$LevelWorldView.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher$MatchResult.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher$Mismatch.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher$MismatchKind.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher$WorldView.class",
            "org/rllabs/afterlight/gate/GatePatternMatcher.class",
            "org/rllabs/afterlight/gate/GateProgressGateway.class",
            "org/rllabs/afterlight/gate/GateReturnTarget.class",
            "org/rllabs/afterlight/gate/GateState.class",
            "org/rllabs/afterlight/gate/GateTravelService$TravelResult.class",
            "org/rllabs/afterlight/gate/GateTravelService.class",
            "org/rllabs/afterlight/integration/",
            "org/rllabs/afterlight/integration/EchoQuestGateway.class",
            "org/rllabs/afterlight/network/",
            "org/rllabs/afterlight/network/AfterlightPayloads.class",
            "org/rllabs/afterlight/network/OpenEchoRequest.class",
            "org/rllabs/afterlight/network/OpenEchoScreen.class",
            "org/rllabs/afterlight/relay/",
            "org/rllabs/afterlight/relay/FarRelayInitializer$1.class",
            "org/rllabs/afterlight/relay/FarRelayInitializer.class",
            "org/rllabs/afterlight/relay/FarRelayKeys.class",
            "org/rllabs/afterlight/relay/FarRelaySavedData.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Anchor.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Builder.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Material.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Placement.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Plan.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan$Position.class",
            "org/rllabs/afterlight/relay/FarRelayStructurePlan.class",
            "org/rllabs/afterlight/relay/FutureConsoleBlock$1.class",
            "org/rllabs/afterlight/relay/FutureConsoleBlock.class",
            "org/rllabs/afterlight/relay/RelaySite.class",
            "org/rllabs/afterlight/relay/ReturnTerminalBlock$1.class",
            "org/rllabs/afterlight/relay/ReturnTerminalBlock.class",
            "org/rllabs/afterlight/relay/SignalTerminalBlock.class",
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
            "assets/afterlight/sounds.json",
            "assets/afterlight/blockstates/",
            "assets/afterlight/blockstates/future_console.json",
            "assets/afterlight/blockstates/gate_controller.json",
            "assets/afterlight/blockstates/gate_field.json",
            "assets/afterlight/blockstates/gate_frame.json",
            "assets/afterlight/blockstates/relay_stone.json",
            "assets/afterlight/blockstates/return_terminal.json",
            "assets/afterlight/blockstates/signal_glass.json",
            "assets/afterlight/lang/",
            "assets/afterlight/lang/en_us.json",
            "assets/afterlight/models/",
            "assets/afterlight/models/block/",
            "assets/afterlight/models/block/future_console.json",
            "assets/afterlight/models/block/future_console_base.json",
            "assets/afterlight/models/block/future_console_dormant.json",
            "assets/afterlight/models/block/gate_controller.json",
            "assets/afterlight/models/block/gate_controller_fault.json",
            "assets/afterlight/models/block/gate_controller_open.json",
            "assets/afterlight/models/block/gate_field.json",
            "assets/afterlight/models/block/gate_frame.json",
            "assets/afterlight/models/block/relay_stone.json",
            "assets/afterlight/models/block/return_terminal.json",
            "assets/afterlight/models/block/return_terminal_base.json",
            "assets/afterlight/models/block/return_terminal_dormant.json",
            "assets/afterlight/models/block/signal_glass.json",
            "assets/afterlight/models/item/",
            "assets/afterlight/models/item/echo.json",
            "assets/afterlight/models/item/future_console.json",
            "assets/afterlight/models/item/gate_controller.json",
            "assets/afterlight/models/item/gate_frame.json",
            "assets/afterlight/models/item/relay_stone.json",
            "assets/afterlight/models/item/return_terminal.json",
            "assets/afterlight/models/item/signal_glass.json",
            "assets/afterlight/sounds/",
            "assets/afterlight/sounds/gate_close.ogg",
            "assets/afterlight/sounds/gate_fault.ogg",
            "assets/afterlight/sounds/gate_open.ogg",
            "assets/afterlight/textures/",
            "assets/afterlight/textures/block/",
            "assets/afterlight/textures/block/future_console.png",
            "assets/afterlight/textures/block/future_console_dormant.png",
            "assets/afterlight/textures/block/gate_controller.png",
            "assets/afterlight/textures/block/gate_controller_fault.png",
            "assets/afterlight/textures/block/gate_controller_open.png",
            "assets/afterlight/textures/block/gate_field.png",
            "assets/afterlight/textures/block/gate_field.png.mcmeta",
            "assets/afterlight/textures/block/gate_frame.png",
            "assets/afterlight/textures/block/relay_stone.png",
            "assets/afterlight/textures/block/return_terminal.png",
            "assets/afterlight/textures/block/return_terminal_dormant.png",
            "assets/afterlight/textures/block/signal_glass.png",
            "assets/afterlight/textures/gui/",
            "assets/afterlight/textures/gui/echo_panel.png",
            "assets/afterlight/textures/gui/title.png",
            "assets/afterlight/textures/item/",
            "assets/afterlight/textures/item/echo.png",
            "data/",
            "data/afterlight/",
            "data/afterlight/advancement/",
            "data/afterlight/advancement/far_relay_arrival.json",
            "data/afterlight/advancement/gate_opened.json",
            "data/afterlight/dimension/",
            "data/afterlight/dimension/far_relay.json",
            "data/afterlight/dimension_type/",
            "data/afterlight/dimension_type/far_relay.json",
            "data/afterlight/loot_table/",
            "data/afterlight/loot_table/blocks/",
            "data/afterlight/loot_table/blocks/future_console.json",
            "data/afterlight/loot_table/blocks/gate_controller.json",
            "data/afterlight/loot_table/blocks/gate_frame.json",
            "data/afterlight/loot_table/blocks/relay_stone.json",
            "data/afterlight/loot_table/blocks/return_terminal.json",
            "data/afterlight/loot_table/blocks/signal_glass.json",
            "data/afterlight/loot_table/chests/",
            "data/afterlight/loot_table/chests/far_relay.json",
            "data/afterlight/recipe/",
            "data/afterlight/recipe/gate_controller.json",
            "data/afterlight/recipe/gate_frame.json",
            "data/afterlight/recipe/signal_glass.json",
            "data/afterlight/worldgen/",
            "data/afterlight/worldgen/biome/",
            "data/afterlight/worldgen/biome/far_relay.json",
            "data/afterlight/worldgen/noise_settings/",
            "data/afterlight/worldgen/noise_settings/far_relay.json");

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
    void visualHarnessStaysInTestSourceWhileProductionPresentationShips() throws Exception {
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "src/test/java/org/rllabs/afterlight/client/VisualAcceptanceHarness.java")));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "src/test/java/org/rllabs/afterlight/gate/VisualAcceptanceServerHarness.java")));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "src/test/java/org/rllabs/afterlight/client/VisualSceneProbe.java")));
        assertTrue(Files.isDirectory(ROOT.resolve(
                "src/test/java/org/rllabs/afterlight/visual")));
        assertTrue(Files.isRegularFile(ROOT.resolve(
                "src/test/resources/visual-acceptance/server.properties")));
        assertFalse(Files.exists(ROOT.resolve(
                "src/main/java/org/rllabs/afterlight/client/VisualAcceptanceHarness.java")));
        assertFalse(Files.exists(ROOT.resolve(
                "src/main/java/org/rllabs/afterlight/gate/VisualAcceptanceServerHarness.java")));

        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            Set<String> entries = Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(entries.contains("org/rllabs/afterlight/client/FarRelayEffects.class"));
            assertTrue(entries.contains("org/rllabs/afterlight/client/GateRenderer.class"));
            assertTrue(entries.contains("org/rllabs/afterlight/relay/FarRelayStructurePlan.class"));
            assertTrue(entries.contains("org/rllabs/afterlight/relay/SignalTerminalBlock.class"));
            assertTrue(entries.contains("assets/afterlight/textures/block/gate_field.png"));
            assertTrue(entries.contains("assets/afterlight/textures/block/return_terminal_dormant.png"));
            assertTrue(entries.contains("assets/afterlight/sounds/gate_open.ogg"));
            assertTrue(entries.contains("data/afterlight/loot_table/blocks/gate_controller.json"));
            assertFalse(entries.stream().anyMatch(name -> name.contains("VisualAcceptance")));
            assertFalse(entries.stream().anyMatch(name -> name.startsWith(
                    "org/rllabs/afterlight/visual/")));
            assertFalse(entries.contains("visual-acceptance/server.properties"));
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
    void dependencyVerificationCoversColdNeoFormJunitBomMetadata() throws Exception {
        Document metadata = verificationMetadata();
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.9.3",
                "junit-bom-5.9.3.module",
                "b401fd25901e582a524aa5343c4b39e28bc56e24961c1069bf2b4bbfcee46b93",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.10.0",
                "junit-bom-5.10.0.module",
                "eb3ee6127608010694a898056e7407d117296003aba5f5db801df430b9887fcf",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.10.1",
                "junit-bom-5.10.1.module",
                "21b0afcfffe2ecb3770f5eb00ae7a19feaee94e771fa3918173850dae78067b7",
                CROSS_REPOSITORY_ORIGIN);
    }

    @Test
    void dependencyVerificationCoversUnlockedNeoFormRuntimeMetadata() throws Exception {
        Document metadata = verificationMetadata();
        assertVerificationArtifact(
                metadata,
                "org.apache",
                "apache",
                "23",
                "apache-23.pom",
                "bc10624e0623f36577fac5639ca2936d3240ed152fb6d8d533ab4d270543491c",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "commons-io",
                "commons-io",
                "2.11.0",
                "commons-io-2.11.0.pom",
                "2e016fd7e3244b5f2c20acad834d93aa4790486ee1e4564641361a3e831eef59",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.apache.commons",
                "commons-parent",
                "52",
                "commons-parent-52.pom",
                "75dbe8f34e98e4c3ff42daae4a2f9eb4cbcd3b5f1047d54460ace906dbb4502e",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "com.google.code.gson",
                "gson",
                "2.10",
                "gson-2.10.pom",
                "ac69d9f254260caeab3998eaad60f355599c25121e195156bfdffc8a355fc6bd",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "com.google.code.gson",
                "gson-parent",
                "2.10",
                "gson-parent-2.10.pom",
                "fb53ac0b06c19116ca61ac344b4dfe8a7c29cc4f81b353ce889493a5039004fb",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "com.google.guava",
                "guava",
                "31.1-jre",
                "guava-31.1-jre.pom",
                "9193d07bf4f660108d7358e58b27d21b44e34e80d6734e98e21916376f270de2",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "com.google.guava",
                "guava-parent",
                "31.1-jre",
                "guava-parent-31.1-jre.pom",
                "4439626783b44ad25ef05ff07621dd4bb796cc4eb4f2966a4a461fea4130e0fc",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.7.2",
                "junit-bom-5.7.2.module",
                "f3bceb1c59dd4f6993f4304dffa580172b8df65a76cd36fa4fd92c0578d28ad8",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.7.2",
                "junit-bom-5.7.2.pom",
                "cd14aaa869991f82021c585d570d31ff342bcba58bb44233b70193771b96487b",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.10.2",
                "junit-bom-5.10.2.module",
                "de23b114b3e4119a8fe6eb17bed5a3852816698bace67071579d6d927ebb080a",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.10.2",
                "junit-bom-5.10.2.pom",
                "169dd904a4b0f6520cffe658cc62292bfe9f3c14a989fa92120724cde43a9968",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "org.junit",
                "junit-bom",
                "5.11.4",
                "junit-bom-5.11.4.pom",
                "19d4b747b204805325b6334553296f986562277a4ac1cb5e593a5e4c4f5e4115",
                CROSS_REPOSITORY_ORIGIN);
        assertVerificationArtifact(
                metadata,
                "com.mojang",
                "logging",
                "1.1.1",
                "logging-1.1.1.module",
                "d24d0f25ce70e7187c4d63dfe5ae36261e06420449c023137c97a4f2cbb2a440",
                MOJANG_CHECKSUM_ORIGIN);
    }

    @Test
    void dependencyVerificationCoversLinuxNeoFormRuntimeNatives() throws Exception {
        Document metadata = verificationMetadata();
        assertLinuxNative(
                metadata, "lwjgl", "e663738c519a06f6d659882fa8e4e09af7f10e921929ee5cc54a7587f62ed4c9");
        assertLinuxNative(
                metadata,
                "lwjgl-freetype",
                "9fc63518a27b8ff1ab78196343710ce28e8000ce88f60be851a2d3db5beab8e9");
        assertLinuxNative(
                metadata,
                "lwjgl-glfw",
                "b8306062b17741f34269088751421f1ac21a597bfbbd0c6c61226301cde744b8");
        assertLinuxNative(
                metadata,
                "lwjgl-jemalloc",
                "4e4a13d7015d42605bbcf7ef9faead46deac6409e4377a8ed4aea815f14634b3");
        assertLinuxNative(
                metadata,
                "lwjgl-openal",
                "9030fed928a71eac2fdf4ce1643cc4ec724d8710e3b437bdce750bdc9e982b2a");
        assertLinuxNative(
                metadata,
                "lwjgl-opengl",
                "d823a92c6a2810b5112da304dcc6abcd4cb102706f74f7e934a223cea2051250");
        assertLinuxNative(
                metadata,
                "lwjgl-stb",
                "c4489068ddc6dc44b103071943f801a2d076634d4b74db470927f84a308219e1");
        assertLinuxNative(
                metadata,
                "lwjgl-tinyfd",
                "a076fa05a4d174762eab5852ee72c29d14ab10a5654f27fb00664ef1b52c0c05");
    }

    @Test
    void platformSpecificDependenciesUseStrictExecutionContextLockfiles() throws Exception {
        String build = Files.readString(ROOT.resolve("build.gradle"));

        assertTrue(build.contains("import org.gradle.api.artifacts.dsl.LockMode"));
        assertTrue(build.contains("providers.gradleProperty('afterlightLockContext').orNull"));
        assertTrue(build.contains("['linux', 'macos'] as Set"));
        assertTrue(build.contains("Missing required Gradle property: afterlightLockContext"));
        assertTrue(build.contains("Unsupported afterlightLockContext:"));
        assertTrue(build.contains("lockMode = LockMode.STRICT"));
        assertTrue(build.contains(
                "lockFile = file(\"gradle/dependency-locks/${lockContext}.lockfile\")"));
        assertTrue(build.contains("lockAllConfigurations()"));
        assertTrue(build.contains("systemProperty 'afterlight.lock.context', lockContext"));
        assertFalse(build.contains("deactivateDependencyLocking"));
        assertFalse(Files.exists(ROOT.resolve("gradle.lockfile")));

        String macosLock = Files.readString(
                ROOT.resolve("gradle/dependency-locks/macos.lockfile"));
        String linuxLock = Files.readString(
                ROOT.resolve("gradle/dependency-locks/linux.lockfile"));
        assertTrue(macosLock.contains(
                "ca.weblite:java-objc-bridge:1.1=additionalRuntimeClasspath"));
        assertTrue(macosLock.contains(
                "io.netty:netty-transport-native-epoll:4.1.97.Final=gameTestServerLegacyClasspath"));
        assertTrue(linuxLock.contains(
                "ca.weblite:java-objc-bridge:1.1=compileClasspath"));
        assertTrue(linuxLock.contains(
                "io.netty:netty-transport-native-epoll:4.1.97.Final=additionalRuntimeClasspath"));
    }

    @Test
    void developerAndReleaseCommandsRequireAnExplicitLockContext() throws Exception {
        String readme = Files.readString(ROOT.resolve("README.md"));

        assertTrue(readme.contains(
                "gradle clean test runGameTestServer build -PafterlightLockContext=macos --no-daemon"));
        assertTrue(readme.contains(
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true -PafterlightLockContext=macos --no-daemon --no-build-cache --rerun-tasks"));
        assertTrue(readme.contains("replace `macos` with `linux`"));
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

    private static Document verificationMetadata() throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(ROOT.resolve("gradle/verification-metadata.xml").toFile());
    }

    private static void assertLinuxNative(Document metadata, String module, String sha256) {
        assertVerificationArtifact(
                metadata,
                "org.lwjgl",
                module,
                "3.3.3",
                module + "-3.3.3-natives-linux.jar",
                sha256,
                CROSS_REPOSITORY_ORIGIN);
    }

    private static void assertVerificationArtifact(
            Document metadata,
            String group,
            String name,
            String version,
            String artifactName,
            String sha256,
            String origin) {
        List<Element> components = elements(metadata.getElementsByTagNameNS("*", "component"));
        List<Element> matchingComponents = components.stream()
                .filter(component -> component.getAttribute("group").equals(group))
                .filter(component -> component.getAttribute("name").equals(name))
                .filter(component -> component.getAttribute("version").equals(version))
                .toList();
        assertEquals(
                1,
                matchingComponents.size(),
                "wrong component count for " + group + ":" + name + ":" + version);

        List<Element> artifacts = childElements(matchingComponents.getFirst(), "artifact").stream()
                .filter(artifact -> artifact.getAttribute("name").equals(artifactName))
                .toList();
        assertEquals(1, artifacts.size(), "wrong artifact count for " + artifactName);

        List<Element> hashes = childElements(artifacts.getFirst(), "sha256");
        assertEquals(1, hashes.size(), "wrong SHA-256 count for " + artifactName);
        assertEquals(sha256, hashes.getFirst().getAttribute("value"));
        assertEquals(origin, hashes.getFirst().getAttribute("origin"));
    }

    private static List<Element> elements(org.w3c.dom.NodeList nodes) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static List<Element> childElements(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getLocalName().equals(localName)) {
                children.add(element);
            }
        }
        return children;
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
                "dev/ftb/mods/ftbquests/client/ClientQuestFile",
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
    void classScannerRejectsCheckcastNestedArrayClientOperand() {
        String common = "org/rllabs/afterlight/ArrayCastFixture";
        String client = "net/minecraft/client/renderer/LevelRenderer";
        Map<String, byte[]> classes = Map.of(
                common, classWithCheckcastArrayOperand(common, "[[L" + client + ";"));

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> ReleaseClassReferenceScanner.assertSafe(classes, Set.of(common)));

        assertTrue(error.getMessage().matches(".*" + java.util.regex.Pattern.quote(client) + ".*"));
    }

    @Test
    void classScannerRejectsInstructionTypeAnnotationClientDescriptor() {
        String common = "org/rllabs/afterlight/InsnAnnotationFixture";
        String client = "net/neoforged/neoforge/client/event/ScreenEvent";
        Map<String, byte[]> classes = Map.of(
                common, classWithInstructionTypeAnnotation(common, client));

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> ReleaseClassReferenceScanner.assertSafe(classes, Set.of(common)));

        assertTrue(error.getMessage().matches(".*" + java.util.regex.Pattern.quote(client) + ".*"));
    }

    @Test
    void independentArchiveRebuildIsByteForByteIdentical() throws Exception {
        assertTrue(Files.isRegularFile(RELEASE_JAR), "missing built JAR: " + RELEASE_JAR);
        assertTrue(Files.isRegularFile(REBUILT_JAR), "missing rebuilt JAR: " + REBUILT_JAR);
        assertArrayEquals(Files.readAllBytes(RELEASE_JAR), Files.readAllBytes(REBUILT_JAR));
    }

    @Test
    void workflowPinsToolchainRunsExactCiTwiceAndAuditsOutputs() throws Exception {
        assertWorkflowContract(ROOT.resolve(".github/workflows/build.yml"));
        assertFalse(Files.exists(ROOT.resolve("gradlew")));
        assertFalse(Files.exists(ROOT.resolve("gradle/wrapper/gradle-wrapper.jar")));
    }

    @Test
    void workflowRejectsSkippedSecondBuild(@TempDir Path temporaryDirectory) throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        assertWorkflowMutationRejected(
                temporaryDirectory,
                workflow.replace(
                        "      - name: Build source B\n",
                        "      - name: Build source B\n        if: false\n"));
    }

    @Test
    void workflowRejectsToleratedAuditFailure(@TempDir Path temporaryDirectory) throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        assertWorkflowMutationRejected(
                temporaryDirectory,
                workflow.replace(
                        "      - name: Compare and audit independent builds\n",
                        "      - name: Compare and audit independent builds\n"
                                + "        continue-on-error: true\n"));
    }

    @Test
    void workflowRejectsBuildTimeoutOverride(@TempDir Path temporaryDirectory) throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        assertWorkflowMutationRejected(
                temporaryDirectory,
                workflow.replace(
                        "      - name: Build source A\n",
                        "      - name: Build source A\n        timeout-minutes: 1\n"));
    }

    @Test
    void workflowRejectsDuplicateStepKey(@TempDir Path temporaryDirectory) throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        String command =
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true -PafterlightLockContext=linux --no-daemon --no-build-cache --rerun-tasks";
        String sourceBRun = "      - name: Build source B\n"
                + "        working-directory: source-b\n"
                + "        env:\n"
                + "          GRADLE_USER_HOME: ${{ runner.temp }}/gradle-b\n"
                + "        run: " + command + "\n";
        assertWorkflowMutationRejected(
                temporaryDirectory,
                workflow.replace(sourceBRun, sourceBRun + "        run: " + command + "\n"));
    }

    @Test
    void workflowRejectsCommandPreservedOnlyInComment(@TempDir Path temporaryDirectory)
            throws Exception {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/build.yml"));
        String command =
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true -PafterlightLockContext=linux --no-daemon --no-build-cache --rerun-tasks";
        String sourceBRun = "        run: " + command + "\n"
                + "      - name: Compare and audit independent builds\n";
        assertWorkflowMutationRejected(
                temporaryDirectory,
                workflow.replace(
                        sourceBRun,
                        "        # run: " + command + "\n"
                                + "      - name: Compare and audit independent builds\n"));
    }

    private static void assertWorkflowMutationRejected(
            Path temporaryDirectory, String workflow) throws Exception {
        Path mutation = temporaryDirectory.resolve("build.yml");
        Files.writeString(mutation, workflow);
        assertThrows(AssertionError.class, () -> assertWorkflowContract(mutation));
    }

    private static void assertWorkflowContract(Path workflowPath) throws Exception {
        ReleaseWorkflowModel workflow = ReleaseWorkflowModel.parse(workflowPath);
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
                        Set.of("name", "uses", "with"),
                        Set.of("name", "uses", "with"),
                        Set.of("uses", "with"),
                        Set.of("uses", "with"),
                        Set.of("name", "working-directory", "env", "run"),
                        Set.of("name", "working-directory", "env", "run"),
                        Set.of("name", "run")),
                steps.stream().map(ReleaseWorkflowModel.Step::keys).toList());
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
                "gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true -PafterlightLockContext=linux --no-daemon --no-build-cache --rerun-tasks";
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
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        Path binary = repository.resolve("src/main/resources/payload.bin");
        Files.write(binary, new byte[] {0, 1, 2, (byte) 0xff});
        Path script = repository.resolve("tools/release-check.sh");
        Files.writeString(script, "#!/bin/sh\nexit 0\n");
        Set<PosixFilePermission> executable = Files.getPosixFilePermissions(script);
        executable.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(script, executable);
        git(repository, "add", binary.toString(), script.toString());
        git(repository, "commit", "-m", "staging fixtures");

        CommandResult result = stagePolicy(repository, staging, stageIdentity(repository));

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
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        Path workingSource = repository.resolve("src/main/java/example/Main.java");
        byte[] committed = Files.readAllBytes(workingSource);
        assertEquals(0, stagePolicy(repository, staging, stageIdentity(repository)).exitCode());
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
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        String commit = successful(gitResult(repository, "rev-parse", "HEAD"));
        git(
                repository,
                "update-index",
                "--add",
                "--cacheinfo",
                "160000," + commit + ",vendor/module");
        git(repository, "commit", "-m", "gitlink fixture");

        StageIdentity unsupportedIdentity = new StageIdentity(
                successful(gitResult(repository, "rev-parse", "HEAD")), "0".repeat(64));
        CommandResult result = stagePolicy(repository, staging, unsupportedIdentity);

        assertRejected(result, "unsupported_git_entry mode=160000 type=commit path=vendor/module");
        assertFalse(Files.exists(staging));
    }

    @Test
    void stageReleaseRejectsUnsupportedSymlinkMode(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        Path link = repository.resolve("src/main/resources/linked.bin");
        Files.createSymbolicLink(link, Path.of("mod.json"));
        git(repository, "add", link.toString());
        git(repository, "commit", "-m", "symlink mode fixture");

        StageIdentity unsupportedIdentity = new StageIdentity(
                successful(gitResult(repository, "rev-parse", "HEAD")), "0".repeat(64));
        CommandResult result = stagePolicy(repository, staging, unsupportedIdentity);

        assertRejected(
                result,
                "unsupported_git_entry mode=120000 type=blob path=src/main/resources/linked.bin");
        assertFalse(Files.exists(staging));
    }

    @Test
    void stageReleaseWritesAuthenticatedManifestForExactExpectedIdentity(
            @TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity identity = stageIdentity(repository);

        CommandResult result = stagePolicy(repository, staging, identity);

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(
                "AFTERLIGHT_RELEASE_STAGE_V1\n"
                        + "sourceCommit=" + identity.commit() + "\n"
                        + "sourceTreeSha256=" + identity.digest() + "\n",
                Files.readString(staging.resolve(".afterlight-release-stage-manifest")));
        assertEquals(
                0,
                verifyStagedPolicy(repository, staging, identity).exitCode());
    }

    @Test
    void stageReleaseRejectsCleanHeadSwapBeforeStaging(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity expected = stageIdentity(repository);
        Files.writeString(repository.resolve("src/main/resources/mod.json"), "{\"changed\":true}\n");
        git(repository, "add", "src/main/resources/mod.json");
        git(repository, "commit", "-m", "clean head swap");
        String actual = successful(gitResult(repository, "rev-parse", "HEAD"));

        CommandResult result = stagePolicy(repository, staging, expected);

        assertRejected(
                result,
                "expected_head_mismatch phase=before_stage expected="
                        + expected.commit()
                        + " actual="
                        + actual);
        assertFalse(Files.exists(staging.resolve(".afterlight-release-stage-manifest")));
    }

    @Test
    void postBuildStageVerificationRejectsHeadChangedAfterStaging(
            @TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity expected = stageIdentity(repository);
        byte[] committed = Files.readAllBytes(repository.resolve("src/main/resources/mod.json"));
        assertEquals(0, stagePolicy(repository, staging, expected).exitCode());
        Files.writeString(repository.resolve("src/main/resources/mod.json"), "{\"changed\":true}\n");
        git(repository, "add", "src/main/resources/mod.json");
        git(repository, "commit", "-m", "head changed after staging");
        String actual = successful(gitResult(repository, "rev-parse", "HEAD"));

        CommandResult result = verifyStagedPolicy(repository, staging, expected);

        assertArrayEquals(committed, Files.readAllBytes(staging.resolve("src/main/resources/mod.json")));
        assertRejected(
                result,
                "expected_head_mismatch phase=post_build expected="
                        + expected.commit()
                        + " actual="
                        + actual);
    }

    @Test
    void releaseBuildRejectsCleanHeadSwapImmediatelyBeforeStage(
            @TempDir Path temporaryDirectory) throws Exception {
        BuildProbe probe = initializeBuildProbe(temporaryDirectory.resolve("before-stage"));
        Path initScript = temporaryDirectory.resolve("swap-before-stage.gradle");
        Files.writeString(
                initScript,
                """
                gradle.projectsEvaluated {
                    rootProject {
                        def swap = tasks.register('reviewSwapHeadBeforeStage', Exec) {
                            commandLine 'git', 'checkout', '--detach', '--quiet', '%s'
                        }
                        tasks.named('stageReleaseSource') {
                            dependsOn swap
                        }
                    }
                }
                """.formatted(probe.changedCommit()));

        CommandResult result = nestedReleaseProbe(probe.repository(), initScript);

        assertRejected(result, "expected_head_mismatch phase=before_stage");
        assertFalse(Files.exists(probe.repository().resolve(
                "build/libs/afterlight-signal-0.1.0+1.21.1.jar")));
    }

    @Test
    void releaseBuildRejectsHeadSwapAfterStageWithoutChangingCompilerInputs(
            @TempDir Path temporaryDirectory) throws Exception {
        BuildProbe probe = initializeBuildProbe(temporaryDirectory.resolve("after-stage"));
        Path initScript = temporaryDirectory.resolve("swap-after-stage.gradle");
        Files.writeString(
                initScript,
                """
                gradle.projectsEvaluated {
                    rootProject {
                        def stage = tasks.named('stageReleaseSource')
                        def swap = tasks.register('reviewSwapHeadAfterStage', Exec) {
                            dependsOn stage
                            commandLine 'git', 'checkout', '--detach', '--quiet', '%s'
                        }
                        tasks.named('compileJava') {
                            dependsOn swap
                        }
                        tasks.named('processResources') {
                            dependsOn swap
                        }
                    }
                }
                """.formatted(probe.changedCommit()));

        CommandResult result = nestedReleaseProbe(probe.repository(), initScript);

        assertArrayEquals(
                probe.originalPackBytes(),
                Files.readAllBytes(probe.repository().resolve(
                        ".gradle/release-source/src/main/resources/pack.mcmeta")));
        assertRejected(result, "expected_head_mismatch phase=post_build");
    }

    @Test
    void postBuildStageVerificationRejectsChangedStagedBytes(
            @TempDir Path temporaryDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity expected = stageIdentity(repository);
        assertEquals(0, stagePolicy(repository, staging, expected).exitCode());
        Path staged = staging.resolve("src/main/resources/mod.json");
        Files.setPosixFilePermissions(
                staged,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        Files.writeString(staged, "{\"tampered\":true}\n");
        Files.setPosixFilePermissions(staged, Set.of(PosixFilePermission.OWNER_READ));

        CommandResult result = verifyStagedPolicy(repository, staging, expected);

        assertRejected(result, "staged_content_mismatch path=src/main/resources/mod.json");
    }

    @Test
    void stageReleaseRejectsExternalGradleSymlink(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path external = temporaryDirectory.resolve("external-gradle");
        initializeRepository(repository);
        Files.createDirectories(external);
        Files.createSymbolicLink(repository.resolve(".gradle"), external);
        StageIdentity expected = stageIdentity(repository);

        CommandResult result = stagePolicy(
                repository, repository.resolve(".gradle/release-source"), expected);

        assertRejected(result, "unsafe_stage_ancestor path=.gradle kind=symbolic_link");
        assertFalse(Files.exists(external.resolve("release-source")));
    }

    @Test
    void stageReleaseRejectsExistingExternalReleaseSource(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path external = temporaryDirectory.resolve("external-release-source");
        initializeRepository(repository);
        Files.createDirectories(repository.resolve(".gradle"));
        Files.createDirectories(external);
        Path sentinel = external.resolve("sentinel.txt");
        Files.writeString(sentinel, "preserve\n");
        Files.createSymbolicLink(repository.resolve(".gradle/release-source"), external);
        StageIdentity expected = stageIdentity(repository);

        CommandResult result = stagePolicy(
                repository, repository.resolve(".gradle/release-source"), expected);

        assertRejected(
                result,
                "unsafe_stage_ancestor path=.gradle/release-source kind=symbolic_link");
        assertEquals("preserve\n", Files.readString(sentinel));
    }

    @Test
    void stageCleanupNeverDeletesReplacedExternalDestination(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path external = temporaryDirectory.resolve("external-release-source");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity expected = stageIdentity(repository);
        assertEquals(0, stagePolicy(repository, staging, expected).exitCode());
        Files.setPosixFilePermissions(
                staging,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        Files.move(staging, temporaryDirectory.resolve("original-release-source"));
        Files.createDirectories(external);
        Path sentinel = external.resolve("sentinel.txt");
        Files.writeString(sentinel, "preserve\n");
        Files.createSymbolicLink(staging, external);

        CommandResult result = stagePolicy(repository, staging, expected);

        assertRejected(
                result,
                "unsafe_stage_ancestor path=.gradle/release-source kind=symbolic_link");
        assertEquals("preserve\n", Files.readString(sentinel));
    }

    @Test
    void stageReleaseRejectsReplacedGradleAncestor(@TempDir Path temporaryDirectory)
            throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path external = temporaryDirectory.resolve("external-gradle");
        initializeRepository(repository);
        Path staging = repository.resolve(".gradle/release-source");
        StageIdentity expected = stageIdentity(repository);
        assertEquals(0, stagePolicy(repository, staging, expected).exitCode());
        Files.move(repository.resolve(".gradle"), temporaryDirectory.resolve("original-gradle"));
        Files.createDirectories(external);
        Path sentinel = external.resolve("sentinel.txt");
        Files.writeString(sentinel, "preserve\n");
        Files.createSymbolicLink(repository.resolve(".gradle"), external);

        CommandResult result = stagePolicy(repository, staging, expected);

        assertRejected(result, "unsafe_stage_ancestor path=.gradle kind=symbolic_link");
        assertEquals("preserve\n", Files.readString(sentinel));
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

    @Test
    void releaseProvenanceMatchesAuthenticatedStageManifest() throws Exception {
        assumeTrue(Boolean.getBoolean("afterlight.release.build"));
        Path manifest = Path.of(System.getProperty("afterlight.release.stage.manifest"));
        List<String> lines = Files.readAllLines(manifest);
        assertEquals(
                List.of(
                        "AFTERLIGHT_RELEASE_STAGE_V1",
                        "sourceCommit=" + System.getProperty("afterlight.source.commit"),
                        "sourceTreeSha256="
                                + System.getProperty("afterlight.source.tree.sha256")),
                lines);
        try (var zip = new ZipFile(RELEASE_JAR.toFile())) {
            JsonObject provenance = JsonParser.parseString(
                            readUtf8(zip, "META-INF/afterlight-provenance.json"))
                    .getAsJsonObject();
            assertEquals(
                    lines.get(1).substring("sourceCommit=".length()),
                    provenance.get("sourceCommit").getAsString());
            assertEquals(
                    lines.get(2).substring("sourceTreeSha256=".length()),
                    provenance.get("sourceTreeSha256").getAsString());
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

    private static StageIdentity stageIdentity(Path repository) throws Exception {
        return new StageIdentity(
                successful(gitResult(repository, "rev-parse", "HEAD")),
                successful(policy("digest-head", repository)));
    }

    private static CommandResult stagePolicy(
            Path repository, Path staging, StageIdentity identity) throws Exception {
        return execute(
                repository,
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                repository.resolve("tools/ReleaseSourcePolicy.java").toString(),
                "stage-release",
                repository.toString(),
                staging.toString(),
                identity.commit(),
                identity.digest());
    }

    private static CommandResult verifyStagedPolicy(
            Path repository, Path staging, StageIdentity identity) throws Exception {
        return execute(
                repository,
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                repository.resolve("tools/ReleaseSourcePolicy.java").toString(),
                "verify-staged-release",
                repository.toString(),
                staging.toString(),
                identity.commit(),
                identity.digest());
    }

    private static BuildProbe initializeBuildProbe(Path repository) throws Exception {
        Files.createDirectories(repository);
        for (String path : nulSeparated(successfulBytes(gitBytes(ROOT, "ls-files", "-z")))) {
            Path source = ROOT.resolve(path);
            Path target = repository.resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, Files.readAllBytes(source));
            if (Files.isExecutable(source)) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(target, permissions);
            }
        }
        git(repository, "init");
        git(repository, "config", "user.name", "Afterlight Review Probe");
        git(repository, "config", "user.email", "afterlight-review@example.invalid");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "probe source");
        String originalCommit = successful(gitResult(repository, "rev-parse", "HEAD"));
        Path pack = repository.resolve("src/main/resources/pack.mcmeta");
        byte[] originalPackBytes = Files.readAllBytes(pack);
        Files.writeString(
                pack,
                "{\n  \"pack\": {\n    \"description\": \"changed head probe\",\n"
                        + "    \"pack_format\": 48\n  }\n}\n");
        git(repository, "add", "src/main/resources/pack.mcmeta");
        git(repository, "commit", "-m", "changed probe head");
        String changedCommit = successful(gitResult(repository, "rev-parse", "HEAD"));
        git(repository, "checkout", "--detach", "--quiet", originalCommit);
        return new BuildProbe(repository, originalCommit, changedCommit, originalPackBytes);
    }

    private static CommandResult nestedReleaseProbe(Path repository, Path initScript)
            throws Exception {
        return execute(
                repository,
                "gradle",
                "--init-script",
                initScript.toString(),
                "clean",
                "verifyReleaseJar",
                "-x",
                "test",
                "-PafterlightRelease=true",
                "-PafterlightLockContext=" + System.getProperty("afterlight.lock.context"),
                "--offline",
                "--no-daemon",
                "--no-build-cache",
                "--rerun-tasks");
    }

    private static List<String> nulSeparated(byte[] content) {
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < content.length; index++) {
            if (content[index] == 0) {
                values.add(new String(
                        Arrays.copyOfRange(content, start, index), StandardCharsets.UTF_8));
                start = index + 1;
            }
        }
        assertEquals(content.length, start, "unterminated NUL-separated output");
        return List.copyOf(values);
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

    private static byte[] classWithCheckcastArrayOperand(
            String internalName, String arrayDescriptor) {
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
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitTypeInsn(Opcodes.CHECKCAST, arrayDescriptor);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithInstructionTypeAnnotation(
            String internalName, String annotationInternalName) {
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
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Object");
        method.visitInsnAnnotation(
                        TypeReference.newTypeArgumentReference(TypeReference.CAST, 0).getValue(),
                        null,
                        "L" + annotationInternalName + ";",
                        true)
                .visitEnd();
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

    private record StageIdentity(String commit, String digest) {}

    private record BuildProbe(
            Path repository,
            String originalCommit,
            String changedCommit,
            byte[] originalPackBytes) {}
}
