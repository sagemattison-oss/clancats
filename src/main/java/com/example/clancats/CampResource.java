package com.example.clancats;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/** What a camp counts when you stock its fresh-kill barrel. */
public enum CampResource {
    PREY("prey"),
    WOOL("wool"),
    WHEAT("wheat or hay"),
    HERBS("herbs");

    public final String label;

    CampResource(String label) {
        this.label = label;
    }

    /** How many units of this resource one item of the stack is worth (0 = not this resource). */
    public int worth(ItemStack s) {
        if (s.isEmpty()) return 0;
        return switch (this) {
            case PREY -> CatEvents.CAT_FOOD.contains(s.getItem()) ? 1 : 0;
            case WOOL -> s.isIn(ItemTags.WOOL) ? 1 : 0;
            case WHEAT -> s.isOf(Items.WHEAT) ? 1 : s.isOf(Items.HAY_BLOCK) ? 9 : 0;
            case HERBS -> (s.isIn(ItemTags.FLOWERS) || s.isOf(Items.FERN)
                    || s.isOf(Items.SWEET_BERRIES) || s.isOf(Items.GLOW_BERRIES)) ? 1 : 0;
        };
    }
}
