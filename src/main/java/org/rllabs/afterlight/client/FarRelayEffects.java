package org.rllabs.afterlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

public final class FarRelayEffects extends DimensionSpecialEffects {
    private static final ResourceLocation EFFECTS_ID =
            ResourceLocation.fromNamespaceAndPath("afterlight", "far_relay");

    public FarRelayEffects() {
        super(Float.NaN, false, SkyType.NONE, false, true);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS_ID, new FarRelayEffects());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float brightness) {
        return biomeFogColor.multiply(0.35, 0.70, 0.80);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(
            ClientLevel level,
            int ticks,
            float partialTick,
            Matrix4f modelViewMatrix,
            Camera camera,
            Matrix4f projectionMatrix,
            boolean foggy,
            Runnable setupFog) {
        return true;
    }

    @Override
    public boolean renderClouds(
            ClientLevel level,
            int ticks,
            float partialTick,
            PoseStack poseStack,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(
            ClientLevel level,
            int ticks,
            float partialTick,
            LightTexture lightTexture,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }
}
