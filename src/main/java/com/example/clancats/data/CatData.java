package com.example.clancats.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Persistent per-player data (stored with Fabric's Data Attachment API). */
public record CatData(boolean cat, String clan, int prey, int lives, String fur) {
    public static final CatData DEFAULT = new CatData(false, "", 0, 0, "");

    public static final Codec<CatData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("cat", false).forGetter(CatData::cat),
            Codec.STRING.optionalFieldOf("clan", "").forGetter(CatData::clan),
            Codec.INT.optionalFieldOf("prey", 0).forGetter(CatData::prey),
            Codec.INT.optionalFieldOf("lives", 0).forGetter(CatData::lives),
            Codec.STRING.optionalFieldOf("fur", "").forGetter(CatData::fur)
    ).apply(i, CatData::new));

    public CatRank rank() { return CatRank.forPrey(prey); }
    public Optional<CatClan> clanOpt() { return CatClan.byId(clan); }

    public CatData withCat(boolean v) { return new CatData(v, clan, prey, lives, fur); }
    public CatData withClan(String v) { return new CatData(cat, v, prey, lives, fur); }
    public CatData withPrey(int v) { return new CatData(cat, clan, v, lives, fur); }
    public CatData withLives(int v) { return new CatData(cat, clan, prey, v, fur); }
    public CatData withFur(String v) { return new CatData(cat, clan, prey, lives, v); }
}
