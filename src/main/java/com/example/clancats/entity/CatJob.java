package com.example.clancats.entity;

/** What a recruited cat does around camp. Sneak + right-click (empty hand) cycles it. */
public enum CatJob {
    HUNTER("Hunter", "hunts nearby prey and stocks the fresh-kill barrel"),
    GUARD("Guard", "attacks hostile mobs near the camp"),
    HEALER("Healer", "gives clanmates regeneration while they're close");

    public final String title;
    public final String blurb;

    CatJob(String title, String blurb) {
        this.title = title;
        this.blurb = blurb;
    }

    public CatJob next() {
        CatJob[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
