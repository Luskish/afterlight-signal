package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.junit.jupiter.api.Test;

class GatePresentationContractTest {
    @Test
    void farRelayUsesBiomeFogAndSuppressesVanillaSkyCloudsAndWeather() {
        FarRelayEffects effects = new FarRelayEffects();

        assertEquals(DimensionSpecialEffects.SkyType.NONE, effects.skyType());
        Vec3 fog = effects.getBrightnessDependentFogColor(new Vec3(0.1, 0.3, 0.3), 0.25F);
        assertEquals(0.035, fog.x, 1.0E-12);
        assertEquals(0.21, fog.y, 1.0E-12);
        assertEquals(0.24, fog.z, 1.0E-12);
        assertTrue(effects.renderSky(null, 0, 0.0F, null, null, null, false, () -> {}));
        assertTrue(effects.renderClouds(null, 0, 0.0F, null, 0.0, 0.0, 0.0, null, null));
        assertTrue(effects.renderSnowAndRain(null, 0, 0.0F, null, 0.0, 0.0, 0.0));
        assertTrue(effects.tickRain(null, 0, null));
    }

    @Test
    void farRelayEffectsRegisterUnderTheDimensionTypeEffectsId() {
        Map<ResourceLocation, DimensionSpecialEffects> registered = new HashMap<>();

        FarRelayEffects.register(new RegisterDimensionSpecialEffectsEvent(registered));

        assertEquals(Set.of(id("far_relay")), registered.keySet());
        assertTrue(registered.get(id("far_relay")) instanceof FarRelayEffects);
    }

    @Test
    void gateTransitionInterpolationClampsAndUsesSmoothstep() {
        assertEquals(0.0F, GateRenderer.interpolationProgress(20L, 20L, 0.0F));
        assertEquals(0.5F, GateRenderer.interpolationProgress(24L, 20L, 0.0F));
        assertEquals(1.0F, GateRenderer.interpolationProgress(28L, 20L, 0.0F));
        assertEquals(1.0F, GateRenderer.interpolationProgress(200L, 20L, 0.0F));
    }

    @Test
    void gateRendererSamplesTheAnimatedFieldFromTheTickedBlockAtlas() throws Exception {
        Path source = Path.of(System.getProperty("afterlight.source.root", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("src/main/java/org/rllabs/afterlight/client/GateRenderer.java");
        String renderer = Files.readString(source);

        assertTrue(renderer.contains("InventoryMenu.BLOCK_ATLAS"));
        assertTrue(renderer.contains("TextureAtlasSprite"));
        assertTrue(renderer.contains("fieldSprite.getU0()"));
        assertTrue(renderer.contains("fieldSprite.getV1()"));
        assertFalse(renderer.contains("entityTranslucentEmissive(FIELD_TEXTURE)"));
        assertFalse(renderer.contains("TextureAtlas.LOCATION_BLOCKS"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }
}
