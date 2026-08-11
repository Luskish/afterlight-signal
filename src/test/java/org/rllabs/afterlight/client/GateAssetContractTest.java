package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class GateAssetContractTest {
    private static final Path ROOT = Path.of(
                    System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/afterlight");
    private static final Path DATA = ROOT.resolve("src/main/resources/data/afterlight");
    private static final List<String> BLOCKS = List.of(
            "gate_frame",
            "signal_glass",
            "gate_controller",
            "gate_field",
            "relay_stone",
            "return_terminal",
            "future_console");
    private static final List<String> PLACEABLE_ITEMS = List.of(
            "gate_frame",
            "signal_glass",
            "gate_controller",
            "relay_stone",
            "return_terminal",
            "future_console");
    private static final List<String> CONTROLLER_STATES = List.of(
            "gate_controller_open", "gate_controller_fault");
    private static final List<String> TERMINAL_MODELS = List.of(
            "return_terminal_base",
            "return_terminal_dormant",
            "future_console_base",
            "future_console_dormant");
    private static final List<String> TERMINAL_DORMANT_TEXTURES = List.of(
            "return_terminal_dormant", "future_console_dormant");
    private static final List<String> SOUNDS = List.of(
            "gate_open", "gate_close", "gate_fault");

    @Test
    void everyRegisteredGateBlockShipsItsCompleteRuntimeAssetSet() {
        List<Path> required = new ArrayList<>();
        BLOCKS.forEach(name -> {
            required.add(ASSETS.resolve("blockstates/" + name + ".json"));
            required.add(ASSETS.resolve("models/block/" + name + ".json"));
            required.add(ASSETS.resolve("textures/block/" + name + ".png"));
        });
        PLACEABLE_ITEMS.forEach(name -> {
            required.add(ASSETS.resolve("models/item/" + name + ".json"));
            required.add(DATA.resolve("loot_table/blocks/" + name + ".json"));
        });
        CONTROLLER_STATES.forEach(name -> {
            required.add(ASSETS.resolve("models/block/" + name + ".json"));
            required.add(ASSETS.resolve("textures/block/" + name + ".png"));
        });
        TERMINAL_MODELS.forEach(name ->
                required.add(ASSETS.resolve("models/block/" + name + ".json")));
        TERMINAL_DORMANT_TEXTURES.forEach(name ->
                required.add(ASSETS.resolve("textures/block/" + name + ".png")));
        required.add(ASSETS.resolve("textures/block/gate_field.png.mcmeta"));
        required.add(ASSETS.resolve("sounds.json"));
        SOUNDS.forEach(name -> required.add(ASSETS.resolve("sounds/" + name + ".ogg")));

        List<String> missing = required.stream()
                .filter(Files::notExists)
                .map(ROOT::relativize)
                .map(Path::toString)
                .toList();

        assertEquals(List.of(), missing, "Missing Gate presentation assets");
    }

    @Test
    void everyRegisteredGateBlockHasATranslationAndOnlyPlaceableBlocksHaveLoot() throws Exception {
        JsonObject translations = json(ASSETS.resolve("lang/en_us.json"));
        for (String block : BLOCKS) {
            assertTrue(
                    translations.has("block.afterlight." + block),
                    "Missing translation for " + block);
        }

        for (String block : PLACEABLE_ITEMS) {
            JsonObject loot = json(DATA.resolve("loot_table/blocks/" + block + ".json"));
            assertEquals("minecraft:block", loot.get("type").getAsString());
            assertTrue(loot.toString().contains("afterlight:" + block), block);
        }
        assertFalse(Files.exists(DATA.resolve("loot_table/blocks/gate_field.json")));
    }

    @Test
    void blockstatesAndModelsParseAndReferenceOnlyShippedAfterlightTextures() throws Exception {
        for (String block : BLOCKS) {
            JsonObject blockstate = json(ASSETS.resolve("blockstates/" + block + ".json"));
            assertTrue(blockstate.has("variants"), block);
        }

        for (String modelName : combined(BLOCKS, CONTROLLER_STATES, TERMINAL_MODELS)) {
            Path modelPath = ASSETS.resolve("models/block/" + modelName + ".json");
            BlockModel model = assertDoesNotThrow(
                    () -> BlockModel.fromString(Files.readString(modelPath)), modelName);
            assertNotNull(model);
            JsonObject source = json(modelPath);
            if (source.has("textures")) {
                source.getAsJsonObject("textures").entrySet().stream()
                        .map(entry -> entry.getValue().getAsString())
                        .filter(value -> value.startsWith("afterlight:block/"))
                        .map(value -> value.substring("afterlight:block/".length()))
                        .forEach(texture -> assertTrue(
                                Files.isRegularFile(ASSETS.resolve("textures/block/" + texture + ".png")),
                                () -> modelName + " references missing texture " + texture));
            }
        }

        for (String item : PLACEABLE_ITEMS) {
            JsonObject model = json(ASSETS.resolve("models/item/" + item + ".json"));
            assertEquals("afterlight:block/" + item, model.get("parent").getAsString());
        }
    }

    @Test
    void terminalsUseDirectionalActiveStateVariantsAndAuthoredGeometry() throws Exception {
        Set<String> expectedVariants = Set.of(
                "active=false,facing=north",
                "active=false,facing=east",
                "active=false,facing=south",
                "active=false,facing=west",
                "active=true,facing=north",
                "active=true,facing=east",
                "active=true,facing=south",
                "active=true,facing=west");

        for (String terminal : List.of("return_terminal", "future_console")) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + terminal + ".json"))
                    .getAsJsonObject("variants");
            assertEquals(expectedVariants, variants.keySet(), terminal + " variants");

            JsonObject active = json(ASSETS.resolve("models/block/" + terminal + ".json"));
            JsonObject dormant = json(ASSETS.resolve(
                    "models/block/" + terminal + "_dormant.json"));
            JsonObject base = json(ASSETS.resolve("models/block/" + terminal + "_base.json"));
            assertEquals("afterlight:block/" + terminal + "_base", active.get("parent").getAsString());
            assertEquals("afterlight:block/" + terminal + "_base", dormant.get("parent").getAsString());
            assertTrue(base.getAsJsonArray("elements").size() >= 4, terminal + " geometry");
            assertFalse(base.toString().contains("minecraft:block/cube_all"), terminal);
            assertEquals(
                    "afterlight:block/" + terminal,
                    active.getAsJsonObject("textures").get("panel").getAsString());
            assertEquals(
                    "afterlight:block/" + terminal + "_dormant",
                    dormant.getAsJsonObject("textures").get("panel").getAsString());
        }
    }

    @Test
    void gateFieldIsAnExactFourFrameAnimatedRgbaTexture() throws Exception {
        Path texture = ASSETS.resolve("textures/block/gate_field.png");
        BufferedImage image = ImageIO.read(texture.toFile());

        assertNotNull(image);
        assertEquals(32, image.getWidth());
        assertEquals(128, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        JsonObject animation = json(ASSETS.resolve("textures/block/gate_field.png.mcmeta"))
                .getAsJsonObject("animation");
        assertEquals(3, animation.get("frametime").getAsInt());
        assertTrue(animation.get("interpolate").getAsBoolean());
        assertEquals(List.of(0, 1, 2, 3), animation.getAsJsonArray("frames").asList().stream()
                .map(value -> value.getAsInt())
                .toList());
    }

    @Test
    void controllerPaletteUsesAmberIdleCyanOpenAndFaultRedOnlyForFault() throws Exception {
        Map<String, BufferedImage> textures = new LinkedHashMap<>();
        for (String name : combined(BLOCKS, CONTROLLER_STATES)) {
            textures.put(name, ImageIO.read(
                    ASSETS.resolve("textures/block/" + name + ".png").toFile()));
        }
        textures.forEach((name, image) -> assertNotNull(image, name));

        assertTrue(countPixels(textures.get("gate_controller"), GateAssetContractTest::isAmber) >= 12);
        assertTrue(countPixels(textures.get("gate_controller_open"), GateAssetContractTest::isCyan) >= 12);
        assertTrue(countPixels(textures.get("gate_field"), GateAssetContractTest::isCyan) >= 48);
        assertTrue(countPixels(textures.get("gate_controller_fault"), GateAssetContractTest::isFaultRed) >= 12);
        assertTrue(countPixels(textures.get("return_terminal"), GateAssetContractTest::isCyan) >= 12);
        assertTrue(countPixels(textures.get("future_console"), GateAssetContractTest::isAmber) >= 12);
        assertEquals(
                0L,
                countPixels(textures.get("future_console"), GateAssetContractTest::isVividBlue),
                "future_console");
        textures.forEach((name, image) -> {
            if (!name.equals("gate_controller_fault")) {
                assertEquals(0L, countPixels(image, GateAssetContractTest::isFaultRed), name);
            }
        });
    }

    @Test
    void threeRegisteredSoundEventsResolveUsableOggResources() throws Exception {
        JsonObject sounds = json(ASSETS.resolve("sounds.json"));
        assertEquals(Set.copyOf(SOUNDS), sounds.keySet());

        for (String sound : SOUNDS) {
            String resourceName = sounds.getAsJsonObject(sound)
                    .getAsJsonArray("sounds")
                    .get(0)
                    .getAsJsonObject()
                    .get("name")
                    .getAsString();
            assertEquals("afterlight:" + sound, resourceName);
            Path resource = ASSETS.resolve("sounds/" + sound + ".ogg");
            byte[] bytes = Files.readAllBytes(resource);
            assertTrue(bytes.length >= 512, sound + " is not a usable audio resource");
            assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII), sound);
            assertTrue(BuiltInRegistries.SOUND_EVENT
                    .getOptional(ResourceLocation.fromNamespaceAndPath("afterlight", sound))
                    .isPresent(), "Unregistered sound event " + sound);
        }
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), () -> "Missing JSON: " + ROOT.relativize(path));
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @SafeVarargs
    private static List<String> combined(List<String>... groups) {
        List<String> values = new ArrayList<>();
        for (List<String> group : groups) {
            values.addAll(group);
        }
        return List.copyOf(values);
    }

    private static long countPixels(BufferedImage image, PixelPredicate predicate) {
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (predicate.test(
                        argb >>> 24 & 255,
                        argb >>> 16 & 255,
                        argb >>> 8 & 255,
                        argb & 255)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isAmber(int alpha, int red, int green, int blue) {
        return alpha > 96 && red > 120 && green > 55 && red > green * 1.35 && green > blue * 1.8;
    }

    private static boolean isCyan(int alpha, int red, int green, int blue) {
        return alpha > 80 && green > 100 && blue > 110 && red * 1.5 < green && red * 1.5 < blue;
    }

    private static boolean isFaultRed(int alpha, int red, int green, int blue) {
        return alpha > 80 && red > 110 && red > green * 1.8 && red > blue * 1.8;
    }

    private static boolean isVividBlue(int alpha, int red, int green, int blue) {
        return alpha > 80 && blue > 110 && blue > red * 1.8 && blue > green * 1.8;
    }

    @FunctionalInterface
    private interface PixelPredicate {
        boolean test(int alpha, int red, int green, int blue);
    }
}
