package com.example.clancats.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

/** A wild camp that a clan has claimed. {@code center} is the cavern floor under the Great Rock. */
public record CampClaim(BlockPos center, int rot, String clan) {
    public static final Codec<CampClaim> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(CampClaim::center),
            Codec.INT.fieldOf("rot").forGetter(CampClaim::rot),
            Codec.STRING.fieldOf("clan").forGetter(CampClaim::clan)
    ).apply(i, CampClaim::new));
}
