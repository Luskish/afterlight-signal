package org.rllabs.afterlight.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.EchoContent;

class FarRelayInitializerTest {
    @Test
    void relayKeysUseOnlyTheFarRelayAndExpeditionLootIds() {
        assertEquals(id("far_relay"), FarRelayKeys.LEVEL.location());
        assertEquals(id("far_relay"), FarRelayKeys.DIMENSION_TYPE.location());
        assertEquals(id("far_relay"), FarRelayKeys.BIOME.location());
        assertEquals(id("far_relay"), FarRelayKeys.NOISE_SETTINGS.location());
        assertEquals(id("chests/far_relay"), FarRelayKeys.LOOT_TABLE.location());
    }

    @Test
    void sitesUseExactDiscoverableVectorsInStableOrder() {
        assertEquals(
                List.of(
                        RelaySite.CENTRAL,
                        RelaySite.EAST,
                        RelaySite.WEST,
                        RelaySite.SOUTH,
                        RelaySite.NORTH),
                List.of(RelaySite.values()));
        assertEquals(
                Map.of(
                        RelaySite.CENTRAL, List.of(0, 0),
                        RelaySite.EAST, List.of(256, 0),
                        RelaySite.WEST, List.of(-256, 0),
                        RelaySite.SOUTH, List.of(0, 256),
                        RelaySite.NORTH, List.of(0, -256)),
                Map.of(
                        RelaySite.CENTRAL,
                                List.of(RelaySite.CENTRAL.x(), RelaySite.CENTRAL.z()),
                        RelaySite.EAST, List.of(RelaySite.EAST.x(), RelaySite.EAST.z()),
                        RelaySite.WEST, List.of(RelaySite.WEST.x(), RelaySite.WEST.z()),
                        RelaySite.SOUTH, List.of(RelaySite.SOUTH.x(), RelaySite.SOUTH.z()),
                        RelaySite.NORTH, List.of(RelaySite.NORTH.x(), RelaySite.NORTH.z())));
    }

    @Test
    void savedDataPersistsSchemaOneSitesAndPresentationVersionsInEnumOrder() {
        FarRelaySavedData data = new FarRelaySavedData();
        assertTrue(data.markInitialized(RelaySite.NORTH, 72));
        assertTrue(data.markInitialized(RelaySite.CENTRAL, 64));
        assertTrue(data.markPresented(RelaySite.NORTH, 2));
        assertTrue(data.markPresented(RelaySite.CENTRAL, 2));

        CompoundTag tag = data.save(new CompoundTag(), RegistryAccess.EMPTY);

        assertEquals(1, tag.getInt("schema"));
        assertEquals(
                List.of("CENTRAL", "NORTH"),
                tag.getList("initialized_sites", Tag.TAG_STRING).stream()
                        .map(Tag::getAsString)
                        .toList());
        assertEquals(64, tag.getCompound("platform_heights").getInt("CENTRAL"));
        assertEquals(72, tag.getCompound("platform_heights").getInt("NORTH"));
        assertEquals(2, tag.getCompound("presentation_versions").getInt("CENTRAL"));
        assertEquals(2, tag.getCompound("presentation_versions").getInt("NORTH"));
        FarRelaySavedData loaded = FarRelaySavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(Set.of(RelaySite.CENTRAL, RelaySite.NORTH), loaded.initializedSites());
        assertEquals(64, loaded.platformY(RelaySite.CENTRAL).orElseThrow());
        assertEquals(72, loaded.platformY(RelaySite.NORTH).orElseThrow());
        assertEquals(2, loaded.presentationVersion(RelaySite.CENTRAL));
        assertEquals(2, loaded.presentationVersion(RelaySite.NORTH));
        assertFalse(loaded.isDirty());
    }

    @Test
    void schemaOneWithoutStoredHeightRemainsDiscoverable() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        var initialized = new net.minecraft.nbt.ListTag();
        initialized.add(net.minecraft.nbt.StringTag.valueOf("WEST"));
        tag.put("initialized_sites", initialized);

        FarRelaySavedData loaded = FarRelaySavedData.load(tag, RegistryAccess.EMPTY);

        assertTrue(loaded.isInitialized(RelaySite.WEST));
        assertTrue(loaded.platformY(RelaySite.WEST).isEmpty());
        assertEquals(0, loaded.presentationVersion(RelaySite.WEST));
    }

    @Test
    void markingAnInitializedSiteIsIdempotent() {
        FarRelaySavedData data = new FarRelaySavedData();

        assertTrue(data.markInitialized(RelaySite.EAST, 70));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.markInitialized(RelaySite.EAST, 70));
        assertFalse(data.isDirty());
    }

    @Test
    void markingCurrentPresentationVersionIsIdempotent() {
        FarRelaySavedData data = new FarRelaySavedData();

        assertTrue(data.markPresented(RelaySite.EAST, 2));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.markPresented(RelaySite.EAST, 2));
        assertFalse(data.isDirty());
    }

    @Test
    void currentPresentationTerminalRecoveryUsesPlanAndPreservesCustomState() {
        FarRelaySavedData data = new FarRelaySavedData();
        data.markInitialized(RelaySite.CENTRAL, 64);
        data.markPresented(
                RelaySite.CENTRAL, FarRelayStructurePlan.PRESENTATION_VERSION);
        FarRelayStructurePlan.Plan plan = FarRelayStructurePlan.forSite(RelaySite.CENTRAL);
        FarRelayStructurePlan.Placement returnPlacement =
                plan.placementAt(3, 1, 0).orElseThrow();
        FarRelayStructurePlan.Placement consolePlacement =
                plan.placementAt(-3, 1, 0).orElseThrow();
        BlockState expectedReturn = EchoContent.RETURN_TERMINAL
                .get()
                .defaultBlockState()
                .setValue(SignalTerminalBlock.FACING, Direction.WEST)
                .setValue(SignalTerminalBlock.ACTIVE, true);
        BlockState expectedConsole = EchoContent.FUTURE_CONSOLE
                .get()
                .defaultBlockState()
                .setValue(SignalTerminalBlock.FACING, Direction.EAST)
                .setValue(SignalTerminalBlock.ACTIVE, true);

        assertEquals(
                FarRelayStructurePlan.PRESENTATION_VERSION,
                data.presentationVersion(RelaySite.CENTRAL));
        assertEquals(
                expectedReturn,
                FarRelayInitializer.terminalRecoveryState(
                        Blocks.AIR.defaultBlockState(), returnPlacement));
        assertEquals(
                expectedConsole,
                FarRelayInitializer.terminalRecoveryState(
                        Blocks.SHORT_GRASS.defaultBlockState(), consolePlacement));

        BlockState customReturn = expectedReturn
                .setValue(SignalTerminalBlock.FACING, Direction.SOUTH)
                .setValue(SignalTerminalBlock.ACTIVE, false);
        BlockState customConsole = expectedConsole
                .setValue(SignalTerminalBlock.FACING, Direction.NORTH)
                .setValue(SignalTerminalBlock.ACTIVE, false);
        assertEquals(
                customReturn,
                FarRelayInitializer.terminalRecoveryState(customReturn, returnPlacement));
        assertEquals(
                customConsole,
                FarRelayInitializer.terminalRecoveryState(customConsole, consolePlacement));
    }

    @Test
    void initializedSiteViewCannotMutateSavedState() {
        FarRelaySavedData data = new FarRelaySavedData();
        data.markInitialized(RelaySite.WEST, 68);

        assertThrows(
                UnsupportedOperationException.class,
                () -> data.initializedSites().add(RelaySite.SOUTH));
        assertEquals(Set.of(RelaySite.WEST), data.initializedSites());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }
}
