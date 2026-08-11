package org.rllabs.afterlight.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FarRelayDataContractTest {
    private static final Path DATA_ROOT = Path.of(
                    System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize()
            .resolve("src/main/resources/data");
    private static final Path AFTERLIGHT_DATA = DATA_ROOT.resolve("afterlight");

    @Test
    void dimensionUsesFixedFarRelayBiomeAndNoiseSettings() throws Exception {
        assertEquals(
                json("""
                        {
                          "type": "afterlight:far_relay",
                          "generator": {
                            "type": "minecraft:noise",
                            "biome_source": {
                              "type": "minecraft:fixed",
                              "biome": "afterlight:far_relay"
                            },
                            "settings": "afterlight:far_relay"
                          }
                        }
                        """),
                data("dimension/far_relay.json"));
    }

    @Test
    void dimensionTypePinsSafeEndLikeRules() throws Exception {
        assertEquals(
                json("""
                        {
                          "ambient_light": 0.0,
                          "bed_works": false,
                          "coordinate_scale": 1.0,
                          "effects": "afterlight:far_relay",
                          "fixed_time": 6000,
                          "has_ceiling": false,
                          "has_raids": false,
                          "has_skylight": false,
                          "height": 256,
                          "infiniburn": "#minecraft:infiniburn_end",
                          "logical_height": 256,
                          "min_y": 0,
                          "monster_spawn_block_light_limit": 0,
                          "monster_spawn_light_level": 0,
                          "natural": false,
                          "piglin_safe": false,
                          "respawn_anchor_works": false,
                          "ultrawarm": false
                        }
                        """),
                data("dimension_type/far_relay.json"));
    }

    @Test
    void biomeHasPinnedAtmosphereAndNoConfiguredSpawnsOrEndFeatures() throws Exception {
        JsonObject biome = data("worldgen/biome/far_relay.json");

        assertEquals(
                json("""
                        {
                          "fog_color": 1193018,
                          "sky_color": 329741,
                          "water_color": 2186600,
                          "water_fog_color": 600882,
                          "particle": {
                            "options": {"type": "minecraft:white_ash"},
                            "probability": 0.002
                          }
                        }
                        """),
                biome.getAsJsonObject("effects"));
        JsonObject spawners = biome.getAsJsonObject("spawners");
        assertEquals(
                Set.of(
                        "ambient",
                        "axolotls",
                        "creature",
                        "misc",
                        "monster",
                        "underground_water_creature",
                        "water_ambient",
                        "water_creature"),
                spawners.keySet());
        spawners.entrySet().forEach(entry -> assertTrue(
                entry.getValue().getAsJsonArray().isEmpty(),
                () -> "configured spawn list: " + entry.getKey()));
        assertTrue(biome.getAsJsonObject("spawn_costs").isEmpty());
        assertTrue(biome.getAsJsonObject("carvers").isEmpty());

        JsonArray features = biome.getAsJsonArray("features");
        assertEquals(11, features.size());
        features.forEach(step -> assertTrue(step.getAsJsonArray().isEmpty()));
        assertFalse(biome.toString().contains("minecraft:end_spike"));
        assertFalse(biome.toString().contains("minecraft:end_platform"));
    }

    @Test
    void noiseSettingsExactlyPinAuthenticatedVanillaEndSettingsWithRelayStone() throws Exception {
        JsonObject settings = data("worldgen/noise_settings/far_relay.json");

        assertEquals(
                Set.of(
                        "aquifers_enabled",
                        "default_block",
                        "default_fluid",
                        "disable_mob_generation",
                        "legacy_random_source",
                        "noise",
                        "noise_router",
                        "ore_veins_enabled",
                        "sea_level",
                        "spawn_target",
                        "surface_rule"),
                settings.keySet());
        assertEquals(
                "afterlight:relay_stone",
                settings.getAsJsonObject("default_block").get("Name").getAsString());
        assertEquals(
                "afterlight:relay_stone",
                settings.getAsJsonObject("surface_rule")
                        .getAsJsonObject("result_state")
                        .get("Name")
                        .getAsString());
        assertTrue(settings.get("disable_mob_generation").getAsBoolean());
        assertEquals(
                "07fcf314540a85bf7028faa7d5836159aaa72a54f765df47dc84956466794cb8",
                sha256(settings.toString()));
        assertEquals(
                "2283809a472a61658b20b38ace8043a0b13ac801ad922ec0ccf17bd060578b8c",
                sha256(settings.getAsJsonObject("noise_router").toString()));
    }

    @Test
    void expeditionLootUsesOnlyModestVanillaSupplies() throws Exception {
        assertEquals(
                json("""
                        {
                          "type": "minecraft:chest",
                          "pools": [
                            {
                              "rolls": {
                                "type": "minecraft:uniform",
                                "min": 2.0,
                                "max": 4.0
                              },
                              "entries": [
                                {"type": "minecraft:item", "name": "minecraft:ender_pearl", "weight": 4},
                                {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 4},
                                {"type": "minecraft:item", "name": "minecraft:echo_shard", "weight": 2},
                                {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 2}
                              ]
                            }
                          ],
                          "random_sequence": "afterlight:chests/far_relay"
                        }
                        """),
                data("loot_table/chests/far_relay.json"));
    }

    @Test
    void farRelayDataNeverOverridesVanillaNamespace() {
        assertFalse(Files.exists(DATA_ROOT.resolve("minecraft")));
    }

    private static JsonObject data(String relativePath) throws IOException {
        Path path = AFTERLIGHT_DATA.resolve(relativePath);
        assertTrue(Files.isRegularFile(path), () -> "missing Far Relay data: " + path);
        return json(Files.readString(path));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
