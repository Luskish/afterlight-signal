package org.rllabs.afterlight.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

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
    void savedDataPersistsSchemaOneAndSitesInEnumOrder() {
        FarRelaySavedData data = new FarRelaySavedData();
        assertTrue(data.markInitialized(RelaySite.NORTH));
        assertTrue(data.markInitialized(RelaySite.CENTRAL));

        CompoundTag tag = data.save(new CompoundTag(), RegistryAccess.EMPTY);

        assertEquals(1, tag.getInt("schema"));
        assertEquals(
                List.of("CENTRAL", "NORTH"),
                tag.getList("initialized_sites", Tag.TAG_STRING).stream()
                        .map(Tag::getAsString)
                        .toList());
        FarRelaySavedData loaded = FarRelaySavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(Set.of(RelaySite.CENTRAL, RelaySite.NORTH), loaded.initializedSites());
        assertFalse(loaded.isDirty());
    }

    @Test
    void markingAnInitializedSiteIsIdempotent() {
        FarRelaySavedData data = new FarRelaySavedData();

        assertTrue(data.markInitialized(RelaySite.EAST));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.markInitialized(RelaySite.EAST));
        assertFalse(data.isDirty());
    }

    @Test
    void initializedSiteViewCannotMutateSavedState() {
        FarRelaySavedData data = new FarRelaySavedData();
        data.markInitialized(RelaySite.WEST);

        assertThrows(
                UnsupportedOperationException.class,
                () -> data.initializedSites().add(RelaySite.SOUTH));
        assertEquals(Set.of(RelaySite.WEST), data.initializedSites());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }
}
