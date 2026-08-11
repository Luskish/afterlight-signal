package org.rllabs.afterlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.rllabs.afterlight.gate.GateControllerBlockEntity;
import org.rllabs.afterlight.gate.GateState;

public final class GateRenderer implements BlockEntityRenderer<GateControllerBlockEntity> {
    private static final ResourceLocation FIELD_SPRITE = sprite("gate_field");
    private static final ResourceLocation IDLE_TEXTURE = texture("gate_controller");
    private static final ResourceLocation OPEN_TEXTURE = texture("gate_controller_open");
    private static final ResourceLocation FAULT_TEXTURE = texture("gate_controller_fault");
    private static final float TRANSITION_TICKS = 8.0F;

    private final Map<GateControllerBlockEntity, Transition> transitions = new WeakHashMap<>();

    public GateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            GateControllerBlockEntity controller,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        if (controller.getLevel() == null) {
            return;
        }
        long gameTime = controller.getLevel().getGameTime();
        GateState state = controller.state();
        Transition transition = transitions.compute(controller, (key, current) -> {
            if (current == null) {
                return new Transition(GateState.IDLE, state, gameTime);
            }
            if (current.current() != state) {
                return new Transition(current.current(), state, gameTime);
            }
            return current;
        });
        float progress = interpolationProgress(gameTime, transition.startedAt(), partialTick);
        float fieldAlpha = fieldAlpha(transition, progress);
        Direction facing = controller.orientation();
        if (facing == null || !facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }

        if (fieldAlpha > 0.0F) {
            TextureAtlasSprite fieldSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(FIELD_SPRITE);
            VertexConsumer field = buffers.getBuffer(
                    RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
            renderField(poseStack.last().pose(), field, fieldSprite, facing, fieldAlpha);
        }
        ResourceLocation panelTexture = switch (state) {
            case OPEN -> OPEN_TEXTURE;
            case FAULT -> FAULT_TEXTURE;
            case IDLE -> IDLE_TEXTURE;
        };
        float panelAlpha = state == GateState.FAULT
                ? 0.72F + 0.28F * Mth.sin((gameTime + partialTick) * 0.45F)
                : 1.0F;
        VertexConsumer panel = buffers.getBuffer(RenderType.entityTranslucentEmissive(panelTexture));
        renderPanel(poseStack.last().pose(), panel, facing, panelAlpha);
    }

    @Override
    public boolean shouldRenderOffScreen(GateControllerBlockEntity controller) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(GateControllerBlockEntity controller) {
        return new AABB(controller.getBlockPos()).inflate(4.0, 9.0, 4.0);
    }

    static float interpolationProgress(long gameTime, long startedAt, float partialTick) {
        float linear = Mth.clamp(
                (gameTime - startedAt + partialTick) / TRANSITION_TICKS, 0.0F, 1.0F);
        return linear * linear * (3.0F - 2.0F * linear);
    }

    private static float fieldAlpha(Transition transition, float progress) {
        if (transition.current() == GateState.OPEN) {
            return progress;
        }
        if (transition.previous() == GateState.OPEN) {
            return 1.0F - progress;
        }
        return 0.0F;
    }

    private static void renderField(
            Matrix4f matrix,
            VertexConsumer consumer,
            TextureAtlasSprite fieldSprite,
            Direction facing,
            float alpha) {
        int opacity = Mth.clamp((int) (alpha * 205.0F), 0, 205);
        quad(
                matrix,
                consumer,
                facing,
                -2.5F,
                2.5F,
                1.0F,
                8.0F,
                0.502F,
                opacity,
                fieldSprite.getU0(),
                fieldSprite.getU1(),
                fieldSprite.getV0(),
                fieldSprite.getV1());
        quad(
                matrix,
                consumer,
                facing.getOpposite(),
                -2.5F,
                2.5F,
                1.0F,
                8.0F,
                0.502F,
                opacity,
                fieldSprite.getU0(),
                fieldSprite.getU1(),
                fieldSprite.getV0(),
                fieldSprite.getV1());
    }

    private static void renderPanel(
            Matrix4f matrix, VertexConsumer consumer, Direction facing, float alpha) {
        int opacity = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        quad(
                matrix,
                consumer,
                facing,
                -0.31F,
                0.31F,
                0.18F,
                0.82F,
                0.506F,
                opacity,
                0.0F,
                1.0F,
                0.0F,
                1.0F);
    }

    private static void quad(
            Matrix4f matrix,
            VertexConsumer consumer,
            Direction facing,
            float minimumU,
            float maximumU,
            float minimumY,
            float maximumY,
            float depth,
            int alpha,
            float minimumTextureU,
            float maximumTextureU,
            float minimumTextureV,
            float maximumTextureV) {
        Direction right = facing.getClockWise();
        vertex(
                matrix,
                consumer,
                facing,
                right,
                minimumU,
                minimumY,
                depth,
                minimumTextureU,
                maximumTextureV,
                alpha);
        vertex(
                matrix,
                consumer,
                facing,
                right,
                maximumU,
                minimumY,
                depth,
                maximumTextureU,
                maximumTextureV,
                alpha);
        vertex(
                matrix,
                consumer,
                facing,
                right,
                maximumU,
                maximumY,
                depth,
                maximumTextureU,
                minimumTextureV,
                alpha);
        vertex(
                matrix,
                consumer,
                facing,
                right,
                minimumU,
                maximumY,
                depth,
                minimumTextureU,
                minimumTextureV,
                alpha);
    }

    private static void vertex(
            Matrix4f matrix,
            VertexConsumer consumer,
            Direction facing,
            Direction right,
            float localU,
            float y,
            float depth,
            float textureU,
            float textureV,
            int alpha) {
        float x = 0.5F + right.getStepX() * localU + facing.getStepX() * depth;
        float z = 0.5F + right.getStepZ() * localU + facing.getStepZ() * depth;
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(textureU, textureV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(facing.getStepX(), 0.0F, facing.getStepZ());
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                "afterlight", "textures/block/" + name + ".png");
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", "block/" + name);
    }

    private record Transition(GateState previous, GateState current, long startedAt) {}
}
