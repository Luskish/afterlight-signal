package org.rllabs.afterlight.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.relay.FarRelayKeys;

class GateTravelServiceTest {
    private static final Path ROOT = Path.of(
                    System.getProperty("afterlight.source.root", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void returnTargetCodecPreservesLevelPositionAndLook() {
        GateReturnTarget target = new GateReturnTarget(
                Level.OVERWORLD, new BlockPos(42, 73, -19), 137.5F, -28.25F);

        var encoded = GateReturnTarget.CODEC.encodeStart(JsonOps.INSTANCE, target).getOrThrow();

        assertEquals(target, GateReturnTarget.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void returnAttachmentIsCodecBackedAndCopiesOnDeath() throws Exception {
        AttachmentType<GateReturnTarget> attachment = EchoContent.GATE_RETURN_TARGET.get();
        Field serializerField = AttachmentType.class.getDeclaredField("serializer");
        serializerField.setAccessible(true);
        Field copyOnDeathField = AttachmentType.class.getDeclaredField("copyOnDeath");
        copyOnDeathField.setAccessible(true);

        assertTrue(serializerField.get(attachment) != null);
        assertTrue(copyOnDeathField.getBoolean(attachment));
    }

    @Test
    void exactSafeReturnPositionWins() {
        BlockPos source = new BlockPos(10, 70, -4);

        Optional<BlockPos> result = GateTravelService.findSafePosition(
                source, candidate -> candidate.equals(source));

        assertEquals(Optional.of(source), result);
    }

    @Test
    void safeSearchReachesHorizontalFiveAndVerticalSix() {
        BlockPos source = new BlockPos(10, 70, -4);
        BlockPos boundary = source.offset(5, 6, 0);

        Optional<BlockPos> result = GateTravelService.findSafePosition(
                source, candidate -> candidate.equals(boundary));

        assertEquals(Optional.of(boundary), result);
    }

    @Test
    void safeSearchRejectsPositionsOutsideEitherBoundary() {
        BlockPos source = new BlockPos(10, 70, -4);
        Set<BlockPos> outside = Set.of(source.offset(6, 0, 0), source.offset(0, 7, 0));

        Optional<BlockPos> result = GateTravelService.findSafePosition(source, outside::contains);

        assertTrue(result.isEmpty());
    }

    @Test
    void advancementResourcesUseOnlyManualServerCriteria() throws Exception {
        Map<ResourceLocation, String> advancements = Map.of(
                FarRelayKeys.GATE_OPENED, "gate_opened.json",
                FarRelayKeys.FAR_RELAY_ARRIVAL, "far_relay_arrival.json");

        for (Map.Entry<ResourceLocation, String> entry : advancements.entrySet()) {
            Path path = ROOT.resolve("src/main/resources/data/afterlight/advancement")
                    .resolve(entry.getValue());
            assertTrue(Files.isRegularFile(path), "missing advancement: " + entry.getKey());
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject criteria = root.getAsJsonObject("criteria");
            assertEquals(1, criteria.size(), entry.getKey().toString());
            assertEquals(
                    "minecraft:impossible",
                    criteria.getAsJsonObject("server_transition").get("trigger").getAsString());
            assertFalse(root.has("rewards"), entry.getKey().toString());
        }
    }
}
