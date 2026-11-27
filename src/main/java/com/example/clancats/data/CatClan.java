package com.example.clancats.data;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Formatting;

import java.util.Optional;

/** Four original clans. Rename or add your own. */
public enum CatClan {
    ASH("ash", "AshClan", Formatting.GRAY, Blocks.GRAY_WOOL),
    BROOK("brook", "BrookClan", Formatting.AQUA, Blocks.LIGHT_BLUE_WOOL),
    BRAMBLE("bramble", "BrambleClan", Formatting.GREEN, Blocks.GREEN_WOOL),
    CREST("crest", "CrestClan", Formatting.GOLD, Blocks.ORANGE_WOOL);

    public final String id;
    public final String display;
    public final Formatting color;
    /** Wool placed on top of the Great Rock when a camp is claimed. */
    public final Block marker;

    CatClan(String id, String display, Formatting color, Block marker) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.marker = marker;
    }

    public static Optional<CatClan> byId(String id) {
        for (CatClan c : values()) {
            if (c.id.equalsIgnoreCase(id)) return Optional.of(c);
        }
        return Optional.empty();
    }
}
