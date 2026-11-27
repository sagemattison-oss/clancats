package com.example.clancats;

import java.util.Optional;

/** The camp upgrade tree. Costs are paid from the camp's fresh-kill barrel. */
public enum CampUpgrade {
    BEDDING("bedding", "Warm Bedding",
            "Clanmates near camp slowly regenerate.",
            new String[]{},
            new Cost(CampResource.WOOL, 12), new Cost(CampResource.WHEAT, 16)),
    HERB_STORE("herb_store", "Herb Store",
            "Healers heal clanmates twice as strongly.",
            new String[]{"bedding"},
            new Cost(CampResource.HERBS, 12), new Cost(CampResource.WOOL, 8)),
    HUNTING_PARTY("hunting_party", "Hunting Party",
            "Hunters bring back double and hunt twice as often.",
            new String[]{"bedding"},
            new Cost(CampResource.PREY, 24)),
    NURSERY("nursery", "Kit Nursery",
            "Kits are born in the nursery den and grow up in camp.",
            new String[]{"bedding"},
            new Cost(CampResource.PREY, 20), new Cost(CampResource.WOOL, 16), new Cost(CampResource.WHEAT, 16)),
    TRAINING("training", "Training Ground",
            "Apprentices train into warriors (more health and damage).",
            new String[]{"nursery"},
            new Cost(CampResource.PREY, 40), new Cost(CampResource.HERBS, 8)),
    BORDER_PATROL("border_patrol", "Border Patrol",
            "Guard cats hit harder and move faster.",
            new String[]{"training"},
            new Cost(CampResource.PREY, 24), new Cost(CampResource.WOOL, 16)),
    GATHERING_ROCK("gathering_rock", "Gathering Rock",
            "Clanmates near camp gain Strength and Speed, and kits are born twice as fast.",
            new String[]{"training", "herb_store"},
            new Cost(CampResource.PREY, 64), new Cost(CampResource.WOOL, 32),
            new Cost(CampResource.WHEAT, 32), new Cost(CampResource.HERBS, 16));

    public record Cost(CampResource resource, int amount) {}

    public final String id;
    public final String title;
    public final String blurb;
    public final String[] requires;
    public final Cost[] costs;

    CampUpgrade(String id, String title, String blurb, String[] requires, Cost... costs) {
        this.id = id;
        this.title = title;
        this.blurb = blurb;
        this.requires = requires;
        this.costs = costs;
    }

    public static Optional<CampUpgrade> byId(String id) {
        for (CampUpgrade u : values()) {
            if (u.id.equalsIgnoreCase(id)) return Optional.of(u);
        }
        return Optional.empty();
    }

    public String costString() {
        StringBuilder sb = new StringBuilder();
        for (Cost c : costs) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.amount()).append(' ').append(c.resource().label);
        }
        return sb.toString();
    }
}
