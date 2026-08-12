package org.rllabs.afterlight.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class DedicatedMigrationAcceptanceContractTest {
    private static final Path ROOT = Path.of(System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();
    private static final String TOKEN = "a".repeat(64);
    private static final String GATE_TRAVEL_SERVICE =
            "org/rllabs/afterlight/gate/GateTravelService";
    private static final String PUBLIC_OUTBOUND_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)"
                    + "Lorg/rllabs/afterlight/gate/GateTravelService$TravelResult;";
    private static final String RETURN_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;)Z";

    @TempDir
    Path temp;

    @Test
    void authenticatedMarkersRejectWrongChallengeAndPhase() throws Exception {
        Path marker = temp.resolve("prepare.txt");
        DedicatedMigrationAcceptanceMarker.write(
                marker,
                DedicatedMigrationAcceptanceMarker.Phase.PREPARE,
                TOKEN,
                Map.of(
                        "dimension", "afterlight:far_relay",
                        "presentation_version", "0"));

        DedicatedMigrationAcceptanceMarker.Marker verified =
                DedicatedMigrationAcceptanceMarker.readAndVerify(
                        marker,
                        DedicatedMigrationAcceptanceMarker.Phase.PREPARE,
                        TOKEN);

        assertEquals("afterlight:far_relay", verified.metadata().get("dimension"));
        assertEquals("0", verified.metadata().get("presentation_version"));
        assertThrows(
                IllegalStateException.class,
                () -> DedicatedMigrationAcceptanceMarker.readAndVerify(
                        marker,
                        DedicatedMigrationAcceptanceMarker.Phase.PREPARE,
                        "b".repeat(64)));
        assertThrows(
                IllegalStateException.class,
                () -> DedicatedMigrationAcceptanceMarker.readAndVerify(
                        marker,
                        DedicatedMigrationAcceptanceMarker.Phase.VERIFY,
                        TOKEN));
    }

    @Test
    void runnerLaunchesFreshPrepareThenPersistedVerifyWithOneChallenge() throws Exception {
        Path runner = ROOT.resolve("tools/run-dedicated-migration-acceptance.sh");
        assertTrue(Files.isRegularFile(runner), "missing dedicated migration runner");
        assertEquals(0, command(Map.of(), "bash", "-n", runner.toString()).exitCode());
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

        Path capture = temp.resolve("gradle-invocations.txt");
        Path fakeGradle = temp.resolve("fake-gradle");
        Files.writeString(
                fakeGradle,
                "#!/usr/bin/env bash\n"
                        + "set -euo pipefail\n"
                        + "printf '%s\\n' \"$*\" >>\"$AFTERLIGHT_DEDICATED_CAPTURE\"\n");
        Files.setPosixFilePermissions(fakeGradle, Files.getPosixFilePermissions(runner));

        CommandResult result = command(
                Map.of(
                        "GRADLE_COMMAND", fakeGradle.toString(),
                        "AFTERLIGHT_DEDICATED_ACCEPTANCE_TOKEN", TOKEN,
                        "AFTERLIGHT_DEDICATED_CAPTURE", capture.toString()),
                runner.toString());

        assertEquals(0, result.exitCode(), result.output());
        List<String> invocations = Files.readAllLines(capture);
        assertEquals(2, invocations.size());
        assertInvocation(invocations.get(0), "prepare");
        assertInvocation(invocations.get(1), "verify");
        assertTrue(result.output().contains("DEDICATED MIGRATION ACCEPTANCE: OK"));
    }

    @Test
    void dedicatedHarnessCallsOnlyPublicOutboundAndProductionReturn() throws Exception {
        String resource = "/org/rllabs/afterlight/acceptance/"
                + "DedicatedMigrationAcceptanceHarness.class";
        byte[] classBytes;
        try (InputStream input = DedicatedMigrationAcceptanceContractTest.class
                .getResourceAsStream(resource)) {
            assertTrue(input != null, "missing compiled dedicated migration harness");
            classBytes = input.readAllBytes();
        }
        Set<String> outboundDescriptors = new LinkedHashSet<>();
        int[] returnCalls = {0};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface) {
                        if (GATE_TRAVEL_SERVICE.equals(owner)
                                && "travelToFarRelay".equals(invokedName)) {
                            outboundDescriptors.add(invokedDescriptor);
                        }
                        if (GATE_TRAVEL_SERVICE.equals(owner)
                                && "returnPlayer".equals(invokedName)
                                && RETURN_DESCRIPTOR.equals(invokedDescriptor)) {
                            returnCalls[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(Set.of(PUBLIC_OUTBOUND_DESCRIPTOR), outboundDescriptors);
        assertEquals(1, returnCalls[0]);
        assertFalse(outboundDescriptors.stream().anyMatch(value -> value.contains("ServerLevel")));
    }

    private static void assertInvocation(String invocation, String phase) {
        assertTrue(invocation.contains("runGameTestServer"), invocation);
        assertTrue(invocation.contains("-PafterlightDedicatedMigrationAcceptance=true"), invocation);
        assertTrue(invocation.contains("-PafterlightDedicatedMigrationPhase=" + phase), invocation);
        assertTrue(invocation.contains("-PafterlightDedicatedMigrationToken=" + TOKEN), invocation);
        assertTrue(invocation.contains("-PafterlightLockContext=macos"), invocation);
        assertTrue(invocation.contains("--no-daemon"), invocation);
    }

    private static CommandResult command(Map<String, String> environment, String... command)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record CommandResult(int exitCode, String output) {}
}
