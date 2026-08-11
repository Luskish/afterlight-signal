package org.rllabs.afterlight.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Material;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Placement;
import org.rllabs.afterlight.relay.FarRelayStructurePlan.Plan;

class FarRelayStructurePlanTest {
    @Test
    void plansAreBuiltOnceAndReusedByEveryRuntimeProbe() {
        for (RelaySite site : RelaySite.values()) {
            assertSame(FarRelayStructurePlan.forSite(site), FarRelayStructurePlan.forSite(site));
        }
    }

    @Test
    void centralPlanBuildsARecoveredTerminalCathedralAroundTheSafeCore() {
        Plan plan = FarRelayStructurePlan.forSite(RelaySite.CENTRAL);

        assertEquals(plan, FarRelayStructurePlan.forSite(RelaySite.CENTRAL));
        assertTrue(plan.placements().size() >= 450, "central structure is not substantial");
        assertTrue(plan.constructionRadius() >= 11, "central silhouette is too narrow");
        assertTrue(plan.maximumY() >= 12, "central silhouette is too short");
        assertUniquePositions(plan);
        assertCorePlatform(plan);
        assertEquals(
                new Placement(3, 1, 0, Material.RETURN_TERMINAL, Direction.WEST, true),
                plan.placementAt(3, 1, 0).orElseThrow());
        assertEquals(
                new Placement(-3, 1, 0, Material.FUTURE_CONSOLE, Direction.EAST, true),
                plan.placementAt(-3, 1, 0).orElseThrow());
        assertTrue(materials(plan).containsAll(Set.of(
                Material.GATE_FRAME,
                Material.SIGNAL_GLASS,
                Material.POLISHED_BLACKSTONE_BRICKS,
                Material.POLISHED_BLACKSTONE_BRICK_WALL,
                Material.SOUL_LANTERN)));
        assertTrue(anchorNames(plan).containsAll(Set.of(
                "arrival_floor",
                "return_terminal",
                "future_console",
                "loot_chest",
                "signal_aperture",
                "cathedral_crown",
                "west_buttress",
                "east_buttress")));
    }

    @Test
    void everySatelliteBuildsAnOrientedBlackboxShrine() {
        for (RelaySite site : RelaySite.values()) {
            if (site == RelaySite.CENTRAL) {
                continue;
            }
            Plan plan = FarRelayStructurePlan.forSite(site);
            assertEquals(plan, FarRelayStructurePlan.forSite(site));
            assertTrue(plan.placements().size() >= 220, site + " structure is not substantial");
            assertTrue(plan.constructionRadius() >= 8, site + " silhouette is too narrow");
            assertTrue(plan.maximumY() >= 8, site + " silhouette is too short");
            assertUniquePositions(plan);
            assertCorePlatform(plan);
            assertTrue(materials(plan).containsAll(Set.of(
                    Material.FUTURE_CONSOLE,
                    Material.GATE_FRAME,
                    Material.SIGNAL_GLASS,
                    Material.SOUL_LANTERN)));
            assertTrue(anchorNames(plan).containsAll(Set.of(
                    "arrival_floor",
                    "future_console",
                    "loot_chest",
                    "blackbox_crown",
                    "signal_slit")));
            Placement console = plan.anchors().stream()
                    .filter(anchor -> anchor.name().equals("future_console"))
                    .map(anchor -> plan.placementAt(anchor.x(), anchor.y(), anchor.z()).orElseThrow())
                    .findFirst()
                    .orElseThrow();
            assertEquals(site.directionTowardCenter(), console.facing(), site.toString());
            assertTrue(console.active(), site.toString());
        }
    }

    private static void assertCorePlatform(Plan plan) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                assertEquals(
                        Material.RELAY_STONE,
                        plan.placementAt(x, 0, z).orElseThrow().material(),
                        x + "," + z);
            }
        }
    }

    private static void assertUniquePositions(Plan plan) {
        Set<String> positions = new HashSet<>();
        plan.placements().forEach(placement -> assertTrue(
                positions.add(placement.x() + ":" + placement.y() + ":" + placement.z()),
                placement.toString()));
    }

    private static Set<Material> materials(Plan plan) {
        return plan.placements().stream().map(Placement::material).collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> anchorNames(Plan plan) {
        return plan.anchors().stream()
                .map(FarRelayStructurePlan.Anchor::name)
                .collect(java.util.stream.Collectors.toSet());
    }
}
