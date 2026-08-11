package org.rllabs.afterlight.echo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record EchoBond(boolean issued, int generation, long issuedAtEpochSecond) {
    public static final EchoBond UNISSUED = new EchoBond(false, 0, 0L);

    public static final Codec<EchoBond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.fieldOf("issued").forGetter(EchoBond::issued),
                    Codec.INT.fieldOf("generation").forGetter(EchoBond::generation),
                    Codec.LONG.fieldOf("issued_at_epoch_second").forGetter(EchoBond::issuedAtEpochSecond))
            .apply(instance, EchoBond::new));
}
