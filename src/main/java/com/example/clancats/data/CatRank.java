package com.example.clancats.data;

import net.minecraft.util.Formatting;

/** Rank is derived from how much prey a cat has caught. */
public enum CatRank {
    //     title        prey  attack  health  color
    KIT("Kit",            0, 1.0, -6.0, Formatting.GRAY),
    APPRENTICE("Apprentice", 10, 1.5, -4.0, Formatting.GREEN),
    WARRIOR("Warrior",   40, 2.0, -2.0, Formatting.YELLOW),
    DEPUTY("Deputy",    100, 2.5,  0.0, Formatting.GOLD),
    LEADER("Leader",    200, 3.0,  2.0, Formatting.LIGHT_PURPLE);

    public final String title;
    public final int preyNeeded;
    /** Bonus attack damage (claws). */
    public final double attackBonus;
    /** Max health modifier relative to the normal 20. */
    public final double healthBonus;
    public final Formatting color;

    CatRank(String title, int preyNeeded, double attackBonus, double healthBonus, Formatting color) {
        this.title = title;
        this.preyNeeded = preyNeeded;
        this.attackBonus = attackBonus;
        this.healthBonus = healthBonus;
        this.color = color;
    }

    public static CatRank forPrey(int prey) {
        CatRank result = KIT;
        for (CatRank r : values()) {
            if (prey >= r.preyNeeded) result = r;
        }
        return result;
    }

    /** The next rank, or null if already at the top. */
    public CatRank next() {
        CatRank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }
}
