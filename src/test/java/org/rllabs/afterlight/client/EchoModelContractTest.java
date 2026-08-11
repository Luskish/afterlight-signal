package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EchoModelContractTest {
    private static final Path MODEL = Path.of("src/main/resources/assets/afterlight/models/item/echo.json");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/afterlight/textures/item/echo.png");

    @Test
    void echoModelHasPhysicalCuboidsAndHandTransforms() throws Exception {
        JsonObject model = readModel();

        assertTrue(model.getAsJsonArray("elements").size() >= 6, "ECHO requires at least six cuboid elements");
        JsonObject display = model.getAsJsonObject("display");
        for (String transform : List.of(
                "thirdperson_righthand",
                "thirdperson_lefthand",
                "firstperson_righthand",
                "firstperson_lefthand")) {
            assertTrue(display.has(transform), "Missing hand transform: " + transform);
        }
    }

    @Test
    void echoModelReferencesOnlyAfterlightTextures() throws Exception {
        JsonObject textures = readModel().getAsJsonObject("textures");

        assertTrue(textures.size() > 0, "ECHO model must declare a texture");
        textures.entrySet().forEach(entry -> assertTrue(
                entry.getValue().getAsString().startsWith("afterlight:"),
                () -> "Foreign texture reference: " + entry.getValue().getAsString()));
    }

    @Test
    void echoTextureIsExact64PixelRgba() throws Exception {
        if (Files.notExists(TEXTURE)) {
            fail("Missing hand-authored ECHO texture: " + TEXTURE);
        }

        byte[] png = Files.readAllBytes(TEXTURE);
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(4, image.getColorModel().getNumComponents());
        assertEquals(8, Byte.toUnsignedInt(png[24]), "PNG must use 8-bit channels");
        assertEquals(6, Byte.toUnsignedInt(png[25]), "PNG must use truecolor RGBA encoding");
    }

    private static JsonObject readModel() throws Exception {
        if (Files.notExists(MODEL)) {
            return fail("Missing physical ECHO item model: " + MODEL);
        }
        return JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();
    }
}
