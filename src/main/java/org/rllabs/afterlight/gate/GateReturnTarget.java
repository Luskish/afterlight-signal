package org.rllabs.afterlight.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record GateReturnTarget(
        ResourceKey<Level> level, BlockPos position, float yaw, float pitch) {
    public static final Codec<GateReturnTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceKey.codec(Registries.DIMENSION)
                            .fieldOf("level")
                            .forGetter(GateReturnTarget::level),
                    BlockPos.CODEC.fieldOf("position").forGetter(GateReturnTarget::position),
                    Codec.FLOAT.fieldOf("yaw").forGetter(GateReturnTarget::yaw),
                    Codec.FLOAT.fieldOf("pitch").forGetter(GateReturnTarget::pitch))
            .apply(instance, GateReturnTarget::new));

    public GateReturnTarget {
        Objects.requireNonNull(level, "level");
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
