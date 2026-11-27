package com.example.clancats.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

/**
 * A wild camp that a clan has claimed. {@code center} is the floor under the Great Rock and
 * {@code markerY} is how far above that the top of the rock is (where the clan wool goes).
 */
public record CampClaim(BlockPos center, int rot, String clan, int markerY) {
    public static final Codec<CampClaim> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(CampClaim::center),
            Codec.INT.fieldOf("rot").forGetter(CampClaim::rot),
            Codec.STRING.fieldOf("clan").forGetter(CampClaim::clan),
            Codec.INT.optionalFieldOf("marker_y", 7).forGetter(CampClaim::markerY)
    ).apply(i, CampClaim::new));
}
