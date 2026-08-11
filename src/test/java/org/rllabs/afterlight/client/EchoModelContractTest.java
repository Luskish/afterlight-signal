package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.datafixers.util.Either;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class EchoModelContractTest {
    private static final Path MODEL = Path.of("src/main/resources/assets/afterlight/models/item/echo.json");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/afterlight/textures/item/echo.png");

    @Test
    void minecraftParserAcceptsCompleteBoundedRenderableCuboids() throws Exception {
        BlockModel model = parsedModel();

        assertTrue(model.getElements().size() >= 6, "ECHO requires at least six cuboid elements");
        for (int index = 0; index < model.getElements().size(); index++) {
            BlockElement element = model.getElements().get(index);
            assertAxis(index, "x", element.from.x(), element.to.x());
            assertAxis(index, "y", element.from.y(), element.to.y());
            assertAxis(index, "z", element.from.z(), element.to.z());
            assertEquals(
                    EnumSet.allOf(Direction.class),
                    element.faces.keySet(),
                    "Cuboid " + index + " must define all six faces");

            for (Direction direction : Direction.values()) {
                BlockElementFace face = element.faces.get(direction);
                assertNotNull(face, "Cuboid " + index + " missing " + direction + " face");
                assertNotNull(face.uv().uvs, "Cuboid " + index + " missing " + direction + " UV tuple");
                assertEquals(4, face.uv().uvs.length, "Cuboid " + index + " has invalid " + direction + " UV tuple");
                for (float coordinate : face.uv().uvs) {
                    assertTrue(Float.isFinite(coordinate), "UV coordinate must be finite");
                    assertTrue(coordinate >= 0.0F && coordinate <= 16.0F, "UV coordinate must stay inside 0 through 16");
                }
            }
        }
    }

    @Test
    void everyFaceResolvesThroughALocalAfterlightTextureThatExists() throws Exception {
        BlockModel model = parsedModel();

        assertEquals(1, model.textureMap.size());
        for (MapEntry texture : textures(model)) {
            assertTrue(texture.material().texture().getNamespace().equals("afterlight"));
            assertTrue(Files.isRegularFile(texture.path()), "Missing resolved texture: " + texture.path());
        }

        for (BlockElement element : model.getElements()) {
            for (BlockElementFace face : element.faces.values()) {
                assertTrue(face.texture().startsWith("#"), "Faces must use a local texture variable");
                String variable = face.texture().substring(1);
                assertTrue(model.textureMap.containsKey(variable), "Unknown local texture variable: " + variable);
                ResourceLocation resolved = model.getMaterial(face.texture()).texture();
                assertEquals("afterlight", resolved.getNamespace());
                assertTrue(Files.isRegularFile(texturePath(resolved)), "Missing face texture: " + resolved);
            }
        }
    }

    @Test
    void modelDefinesEveryRequiredHandInventoryAndWorldTransform() throws Exception {
        BlockModel model = parsedModel();

        for (ItemDisplayContext context : List.of(
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.GUI,
                ItemDisplayContext.GROUND,
                ItemDisplayContext.FIXED)) {
            assertTrue(model.getTransforms().hasTransform(context), "Missing display transform: " + context);
        }
    }

    @Test
    void echoTextureIsExact64PixelRgba() throws Exception {
        if (Files.notExists(TEXTURE)) {
            fail("Missing hand-authored ECHO texture: " + TEXTURE);
        }

        byte[] png = Files.readAllBytes(TEXTURE);
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        assertNotNull(image);
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(4, image.getColorModel().getNumComponents());
        assertEquals(8, Byte.toUnsignedInt(png[24]), "PNG must use 8-bit channels");
        assertEquals(6, Byte.toUnsignedInt(png[25]), "PNG must use truecolor RGBA encoding");
        assertEquals(0, Byte.toUnsignedInt(png[28]), "PNG must be non-interlaced");
    }

    private static BlockModel parsedModel() throws Exception {
        String json = Files.readString(MODEL);
        return assertDoesNotThrow(
                () -> BlockModel.fromString(json),
                "Minecraft BlockModel parser must accept the shipped ECHO model");
    }

    private static void assertAxis(int index, String axis, float from, float to) {
        assertTrue(from < to, "Cuboid " + index + " must satisfy from < to on " + axis);
        assertTrue(from >= -16.0F, "Cuboid " + index + " exceeds minimum bound on " + axis);
        assertTrue(to <= 32.0F, "Cuboid " + index + " exceeds maximum bound on " + axis);
    }

    private static List<MapEntry> textures(BlockModel model) {
        return model.textureMap.entrySet().stream().map(entry -> {
            Either<Material, String> value = entry.getValue();
            Material material = value.left().orElseGet(() -> model.getMaterial("#" + entry.getKey()));
            return new MapEntry(material, texturePath(material.texture()));
        }).toList();
    }

    private static Path texturePath(ResourceLocation texture) {
        return Path.of(
                "src/main/resources/assets",
                texture.getNamespace(),
                "textures",
                texture.getPath() + ".png");
    }

    private record MapEntry(Material material, Path path) {
    }
}
