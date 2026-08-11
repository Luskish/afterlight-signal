package org.rllabs.afterlight.relay;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.loot.LootTable;
import org.rllabs.afterlight.Afterlight;

public final class FarRelayKeys {
    private static final ResourceLocation FAR_RELAY = id("far_relay");

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, FAR_RELAY);
    public static final ResourceKey<DimensionType> DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, FAR_RELAY);
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, FAR_RELAY);
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, FAR_RELAY);
    public static final ResourceKey<LootTable> LOOT_TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, id("chests/far_relay"));
    public static final ResourceLocation GATE_OPENED = id("gate_opened");
    public static final ResourceLocation FAR_RELAY_ARRIVAL = id("far_relay_arrival");

    private FarRelayKeys() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Afterlight.MOD_ID, path);
    }
}
