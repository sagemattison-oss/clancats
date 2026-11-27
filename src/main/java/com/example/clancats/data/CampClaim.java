package com.example.clancats.data;

import com.example.clancats.CampUpgrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A wild camp that a clan has claimed. {@code center} is the floor under the Great Rock,
 * {@code markerY} is the height of the rock's top above it, {@code kind} is cave (0) or surface (1),
 * and {@code upgrades} are the ids of the {@link CampUpgrade}s the camp has bought.
 */
public record CampClaim(BlockPos center, int rot, String clan, int markerY, int kind, List<String> upgrades) {
    public static final Codec<CampClaim> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(CampClaim::center),
            Codec.INT.fieldOf("rot").forGetter(CampClaim::rot),
            Codec.STRING.fieldOf("clan").forGetter(CampClaim::clan),
            Codec.INT.optionalFieldOf("marker_y", 7).forGetter(CampClaim::markerY),
            Codec.INT.optionalFieldOf("kind", 0).forGetter(CampClaim::kind),
            Codec.STRING.listOf().optionalFieldOf("upgrades", List.of()).forGetter(CampClaim::upgrades)
    ).apply(i, CampClaim::new));

    public boolean has(CampUpgrade u) {
        return upgrades.contains(u.id);
    }

    public CampClaim withUpgrade(CampUpgrade u) {
        List<String> list = new ArrayList<>(upgrades);
        list.add(u.id);
        return new CampClaim(center, rot, clan, markerY, kind, List.copyOf(list));
    }
}
