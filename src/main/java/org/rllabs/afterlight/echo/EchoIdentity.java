package org.rllabs.afterlight.echo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;

public record EchoIdentity(UUID owner, int generation) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            EchoIdentity::parseUuid,
            UUID::toString);

    public static final Codec<EchoIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("owner").forGetter(EchoIdentity::owner),
                    Codec.INT.fieldOf("generation").forGetter(EchoIdentity::generation))
            .apply(instance, EchoIdentity::new));

    private static DataResult<UUID> parseUuid(String value) {
        try {
            return DataResult.success(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Invalid UUID: " + value);
        }
    }
}
