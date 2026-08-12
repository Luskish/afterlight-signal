package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VisualAcceptanceServerHarnessTest {
    @Test
    void authorizedVisualLoginPreservesSurvivalForInventoryCapture() throws Exception {
        String harness = harnessSource();
        int loginStart = harness.indexOf("public static void onPlayerLoggedIn(");
        int scenePreparation = harness.indexOf("private static void prepareScenes(", loginStart);

        assertTrue(loginStart >= 0, "missing visual server login path");
        assertTrue(scenePreparation > loginStart, "missing visual server scene preparation");
        assertFalse(
                harness.substring(loginStart, scenePreparation)
                        .contains("player.setGameMode(GameType.CREATIVE)"),
                "visual server login forces creative before the inventory capture");
    }

    @Test
    void droppedEchoSpawnsWithoutVelocityForStableCapture() throws Exception {
        String harness = harnessSource();
        int itemViewsStart = harness.indexOf("private static void prepareItemViews(");
        int readyMarkerStart = harness.indexOf("private static void writeReadyMarker(", itemViewsStart);

        assertTrue(itemViewsStart >= 0, "missing visual item-view setup");
        assertTrue(readyMarkerStart > itemViewsStart, "missing visual ready-marker boundary");
        String itemViews = harness.substring(itemViewsStart, readyMarkerStart);
        assertTrue(
                itemViews.contains(
                        "new ItemEntity(level, 72.5, 102.2, 0.5, echo.copy(), 0.0, 0.0, 0.0)"),
                "dropped ECHO uses the random-velocity ItemEntity constructor");
    }

    @Test
    void visualGateCoreRegistersExactItemOnModBusOnlyForVisualAcceptance() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/gate/VisualAcceptanceGateCore.java");
        assertTrue(Files.exists(source), "missing test-only visual Gate core fixture");
        String fixture = Files.readString(source);

        assertTrue(
                fixture.contains("void register(RegisterEvent event)"),
                "visual Gate core fixture does not use the auto-routed registry event");
        assertFalse(
                fixture.contains("EventBusSubscriber.Bus"),
                "visual Gate core fixture uses deprecated explicit bus selection");
        assertTrue(
                fixture.contains(
                        "ResourceLocation.fromNamespaceAndPath(\"kubejs\", \"gate_of_return_core\")"),
                "visual Gate core fixture does not use the production registry identity");
        int propertyCheck = fixture.indexOf(
                "\"true\".equals(System.getProperty(\"afterlight.visual.acceptance\"))");
        int registration = fixture.indexOf("event.register(");
        assertTrue(propertyCheck >= 0, "visual Gate core registration is not acceptance-gated");
        assertTrue(registration > propertyCheck, "visual Gate core registers before its acceptance gate");
        assertTrue(fixture.contains("Registries.ITEM"), "visual Gate core does not use the item registry");
        assertTrue(
                fixture.contains("BuiltInRegistries.ITEM.containsKey(GATE_CORE_ID)"),
                "visual Gate core lookup does not fail loudly when registration is absent");
    }

    @Test
    void openVisualGateInsertsRegisteredCoreBeforeActivation() throws Exception {
        String harness = harnessSource();
        int openBranch = harness.indexOf("if (state == GateState.OPEN)");
        int faultBranch = harness.indexOf("else if (state == GateState.FAULT)", openBranch);

        assertTrue(openBranch >= 0, "missing visual OPEN Gate setup");
        assertTrue(faultBranch > openBranch, "missing visual FAULT Gate boundary");
        String openSetup = harness.substring(openBranch, faultBranch);
        int coreInsertion =
                openSetup.indexOf("controller.insertCore(VisualAcceptanceGateCore.stack())");
        int activation = openSetup.indexOf("controller.applyActivation(");
        assertTrue(coreInsertion >= 0, "visual OPEN Gate does not insert its registered core");
        assertTrue(activation > coreInsertion, "visual OPEN Gate activates before inserting its core");
    }

    private static String harnessSource() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/test/java/org/rllabs/afterlight/gate/VisualAcceptanceServerHarness.java");
        return Files.readString(source);
    }
}
