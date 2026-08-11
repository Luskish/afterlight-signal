package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VisualWorkflowContractTest {
    private static final Path ROOT = Path.of(System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void isolatedWorkflowRunsTheExplicitLinuxHarnessAndUploadsOnlyReviewedArtifacts()
            throws Exception {
        Path workflowPath = ROOT.resolve(".github/workflows/visual-acceptance.yml");
        assertTrue(Files.isRegularFile(workflowPath), "missing isolated visual workflow");
        String workflow = Files.readString(workflowPath);

        assertTrue(workflow.contains("workflow_dispatch:"));
        assertFalse(workflow.contains("\n  push:"));
        assertFalse(workflow.contains("\n  pull_request:"));
        assertTrue(workflow.contains("runs-on: ubuntu-24.04"));
        assertTrue(workflow.contains("./tools/run-visual-acceptance-linux.sh"));
        assertTrue(workflow.contains("path: build/visual-artifacts"));
        assertTrue(workflow.contains("if-no-files-found: error"));
        assertTrue(workflow.contains("retention-days: 14"));
    }

    @Test
    void linuxRunnerUsesXvfbMesaGeneratedLaunchersAndLoudArtifactChecks() throws Exception {
        Path runnerPath = ROOT.resolve("tools/run-visual-acceptance-linux.sh");
        assertTrue(Files.isRegularFile(runnerPath), "missing Linux visual runner");
        assertEquals(
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE),
                Files.getPosixFilePermissions(runnerPath));

        String runner = Files.readString(runnerPath);
        assertTrue(runner.contains("-PafterlightLockContext=linux"));
        assertTrue(runner.contains("--no-daemon"));
        assertTrue(runner.contains("createVisualServerLaunchScript"));
        assertTrue(runner.contains("createVisualClientLaunchScript"));
        assertTrue(runner.contains("build/moddev/runVisualServer.sh"));
        assertTrue(runner.contains("grep -Fq 'Done ('"));
        assertTrue(runner.contains("xvfb-run"));
        assertTrue(runner.contains("build/moddev/runVisualClient.sh"));
        assertTrue(runner.contains("LIBGL_ALWAYS_SOFTWARE=1"));
        assertTrue(runner.contains("MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"));
        assertTrue(runner.contains("visual-acceptance-success.txt"));
        assertTrue(runner.contains("test -s \"$server_marker\""));
        assertTrue(runner.contains("manifest.json"));
        assertTrue(runner.contains("expected_count=22"));
        assertTrue(runner.contains("timeout"));
    }

    @Test
    void visualServerSceneUsesADeterministicFlatOverworld() throws Exception {
        String build = Files.readString(ROOT.resolve("build.gradle"));

        assertTrue(build.contains("generate-structures=false"));
        assertTrue(build.contains("level-seed=afterlight-visual-acceptance"));
        assertTrue(build.contains("level-type=minecraft:flat"));
    }
}
