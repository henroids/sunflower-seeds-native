package dev.nxnative.android;

/** PEANUTS(sunflower): a game version in the player's words for the dev.peanuts screens ("version 1.0.19" from game.tsv's build rows, Profile.shownVersion), "" when unnamed */
public final class Versions {
    private Versions() {}
    public static String shown(String titleId, long version) { return Profile.shownVersion(titleId, version); }
}
