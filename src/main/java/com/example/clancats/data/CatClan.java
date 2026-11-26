package com.example.clancats.data;

import net.minecraft.util.Formatting;

import java.util.Optional;

/** Four original clans. Rename or add your own. */
public enum CatClan {
    ASH("ash", "AshClan", Formatting.GRAY),
    BROOK("brook", "BrookClan", Formatting.AQUA),
    BRAMBLE("bramble", "BrambleClan", Formatting.GREEN),
    CREST("crest", "CrestClan", Formatting.GOLD);

    public final String id;
    public final String display;
    public final Formatting color;

    CatClan(String id, String display, Formatting color) {
        this.id = id;
        this.display = display;
        this.color = color;
    }

    public static Optional<CatClan> byId(String id) {
        for (CatClan c : values()) {
            if (c.id.equalsIgnoreCase(id)) return Optional.of(c);
        }
        return Optional.empty();
    }
}
