package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.EchoContent;

class GateRegistryContractTest {
    private static final Path ROOT = Path.of(System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void afterlightNamespaceHasExactBlocksItemsAndPlaceableBindings() {
        Map<ResourceLocation, Block> placeableBlocks = Map.of(
                id("gate_frame"), EchoContent.GATE_FRAME.get(),
                id("signal_glass"), EchoContent.SIGNAL_GLASS.get(),
                id("gate_controller"), EchoContent.GATE_CONTROLLER.get(),
                id("relay_stone"), EchoContent.RELAY_STONE.get(),
                id("return_terminal"), EchoContent.RETURN_TERMINAL.get(),
                id("future_console"), EchoContent.FUTURE_CONSOLE.get());
        Set<ResourceLocation> expectedBlocks = Set.of(
                id("gate_frame"),
                id("signal_glass"),
                id("gate_controller"),
                id("gate_field"),
                id("relay_stone"),
                id("return_terminal"),
                id("future_console"));
        Set<ResourceLocation> expectedItems = Set.of(
                id("echo"),
                id("gate_frame"),
                id("signal_glass"),
                id("gate_controller"),
                id("relay_stone"),
                id("return_terminal"),
                id("future_console"));

        Set<ResourceLocation> actualBlocks = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(blockId -> blockId.getNamespace().equals("afterlight"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ResourceLocation> actualItems = BuiltInRegistries.ITEM.keySet().stream()
                .filter(itemId -> itemId.getNamespace().equals("afterlight"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(expectedBlocks, actualBlocks);
        assertEquals(expectedItems, actualItems);
        placeableBlocks.forEach((blockId, block) -> {
            assertSame(block, BuiltInRegistries.BLOCK.get(blockId));
            assertEquals(blockId, BuiltInRegistries.BLOCK.getKey(block));
            Item item = BuiltInRegistries.ITEM.get(blockId);
            assertTrue(item instanceof BlockItem, blockId.toString());
            assertSame(block, ((BlockItem) item).getBlock(), blockId.toString());
        });

        assertTrue(BuiltInRegistries.ITEM.getOptional(id("gate_field")).isEmpty());
        assertSame(
                EchoContent.GATE_CONTROLLER_BLOCK_ENTITY.get(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("gate_controller")));
        assertTrue(EchoContent.GATE_CONTROLLER_BLOCK_ENTITY
                .get()
                .isValid(EchoContent.GATE_CONTROLLER.get().defaultBlockState()));
        assertSame(
                EchoContent.GATE_FIELD_BLOCK_ENTITY.get(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("gate_field")));
        assertTrue(EchoContent.GATE_FIELD_BLOCK_ENTITY
                .get()
                .isValid(EchoContent.GATE_FIELD.get().defaultBlockState()));
    }

    @Test
    void gateFieldIsBrightIntangibleAndNotGenerallyReplaceable() {
        Block field = EchoContent.GATE_FIELD.get();
        var state = field.defaultBlockState();

        assertEquals(15, state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertTrue(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertFalse(state.canBeReplaced());
        assertEquals(BuiltInLootTables.EMPTY, field.getLootTable());
        assertSame(Items.AIR, field.asItem());
    }

    @Test
    void gateFrameRecipeUsesExactPostgameIngredientsAndConditions() throws IOException {
        assertEquals(
                json("""
                        {
                          "type": "minecraft:crafting_shaped",
                          "category": "building",
                          "neoforge:conditions": [
                            {"type": "neoforge:mod_loaded", "modid": "immersiveengineering"},
                            {"type": "neoforge:mod_loaded", "modid": "mekanism"}
                          ],
                          "pattern": ["CSC", "SRS", "CSC"],
                          "key": {
                            "C": {"item": "minecraft:crying_obsidian"},
                            "S": {"item": "immersiveengineering:ingot_steel"},
                            "R": {"item": "mekanism:ingot_refined_obsidian"}
                          },
                          "result": {"id": "afterlight:gate_frame", "count": 2}
                        }
                        """),
                recipe("gate_frame"));
    }

    @Test
    void signalGlassRecipeUsesExactStabilizerIngredientsAndCondition() throws IOException {
        assertEquals(
                json("""
                        {
                          "type": "minecraft:crafting_shaped",
                          "category": "building",
                          "neoforge:conditions": [
                            {"type": "neoforge:mod_loaded", "modid": "ae2"}
                          ],
                          "pattern": ["TFT", "FEF", "TFT"],
                          "key": {
                            "T": {"item": "minecraft:tinted_glass"},
                            "F": {"item": "ae2:fluix_crystal"},
                            "E": {"item": "minecraft:echo_shard"}
                          },
                          "result": {"id": "afterlight:signal_glass", "count": 2}
                        }
                        """),
                recipe("signal_glass"));
    }

    @Test
    void gateControllerRecipeUsesExactCircuitAndStabilizerIngredients() throws IOException {
        assertEquals(
                json("""
                        {
                          "type": "minecraft:crafting_shaped",
                          "category": "redstone",
                          "neoforge:conditions": [
                            {"type": "neoforge:mod_loaded", "modid": "pneumaticcraft"},
                            {"type": "neoforge:mod_loaded", "modid": "ae2"},
                            {"type": "neoforge:mod_loaded", "modid": "kubejs"}
                          ],
                          "pattern": ["PLP", "SCS", "PLP"],
                          "key": {
                            "P": {"item": "pneumaticcraft:printed_circuit_board"},
                            "L": {"item": "ae2:logic_processor"},
                            "S": {"item": "kubejs:undercurrent_stabilizer"},
                            "C": {"item": "minecraft:lodestone"}
                          },
                          "result": {"id": "afterlight:gate_controller", "count": 1}
                        }
                        """),
                recipe("gate_controller"));
    }

    private static JsonObject recipe(String name) throws IOException {
        return json(Files.readString(ROOT.resolve(
                "src/main/resources/data/afterlight/recipe/" + name + ".json")));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("afterlight", path);
    }
}
