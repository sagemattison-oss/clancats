package com.example.clancats;

import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/** The vanilla cat coat patterns, by short name (tabby, siamese, ...). */
public final class CatVariants {
    private CatVariants() {}

    public static List<String> names() {
        return Registries.CAT_VARIANT.streamEntries()
                .map(r -> r.registryKey().getValue().getPath())
                .sorted()
                .toList();
    }

    public static List<String> namesWithDefault() {
        List<String> all = new ArrayList<>();
        all.add("default");
        all.addAll(names());
        return all;
    }

    /** "british_shorthair" -> "British Shorthair" */
    public static String pretty(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
