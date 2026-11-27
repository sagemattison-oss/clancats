package com.example.clancats;

import java.util.concurrent.ThreadLocalRandom;

/** Warrior-style names: a nature prefix plus a suffix. Kits end in "kit", apprentices in "paw". */
public final class CatNames {
    private CatNames() {}

    private static final String[] PREFIXES = {
            "Ash", "Bramble", "Cinder", "Dusk", "Fern", "Flint", "Gorse", "Heather", "Ivy", "Juniper",
            "Lark", "Maple", "Moss", "Nettle", "Owl", "Pebble", "Quill", "Reed", "Sage", "Thorn",
            "Willow", "Wren", "Yarrow", "Clover", "Sorrel", "Thistle", "Marigold", "Sparrow", "Rowan",
            "Hazel", "Birch", "Alder", "Briar", "Dapple", "Ember", "Frost", "Larch", "Mist", "Rain", "Sunny"
    };
    private static final String[] SUFFIXES = {
            "fur", "pelt", "tail", "claw", "whisker", "fang", "leaf", "storm", "heart", "shade",
            "step", "wing", "stripe", "flame", "song", "stream"
    };

    private static String prefix() {
        return PREFIXES[ThreadLocalRandom.current().nextInt(PREFIXES.length)];
    }

    public static String random() {
        return prefix() + SUFFIXES[ThreadLocalRandom.current().nextInt(SUFFIXES.length)];
    }

    public static String kit() {
        return prefix() + "kit";
    }

    public static String toApprentice(String name) {
        return name.endsWith("kit") ? name.substring(0, name.length() - 3) + "paw" : name;
    }

    public static String toWarrior(String name) {
        return name.endsWith("paw")
                ? name.substring(0, name.length() - 3) + SUFFIXES[ThreadLocalRandom.current().nextInt(SUFFIXES.length)]
                : name;
    }
}
