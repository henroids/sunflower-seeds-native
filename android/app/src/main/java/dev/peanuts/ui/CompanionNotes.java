package dev.peanuts.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A companion release's notes as Peanuts shows them. The catalog writes them for Eden Duo: a first sentence names the game, its title ID and the
 * game versions; the install steps say how to add the file in Eden Duo; a SHA-256 line closes them. Peanuts installs and checks the file itself, so
 * the version page shows the game versions as a fact and leaves the steps and the checksum line out. No Android classes, so it runs on the JVM.
 */
final class CompanionNotes {
    private CompanionNotes() {}

    // the paragraph's start or after a "thank you! ": a name may hold a full stop ("Super Mario Bros. Wonder")
    private static final Pattern GAME = Pattern.compile("(?:^|(?<=!\\s))[^\\n!?]*?\\(\\s*`?[0-9A-Fa-f]{16}`?\\s*\\),\\s*(?:game versions?|base game|Switch update)\\s+([^\\n]+?)\\.(?=\\s|$)\\s*");
    private static final Pattern SHA = Pattern.compile("(?im)^[ \\t]*SHA-?256[^:\\n]*:[ \\t]*`?[0-9a-f]{64}`?[ \\t]*$");
    private static final Pattern EDEN = Pattern.compile("(?i)eden duo|long-press|rename it|renaming it");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+(?=\\S)");
    private static final Pattern LIST_LINE = Pattern.compile("(?m)^\\s*(?:[-*+•]\\s|\\d{1,9}[.)]\\s)");

    /** The game versions the release is for, as its first sentence says them ("4.0.1, 4.0.0 or 3.0.3"); "" when the notes do not say */
    static String gameVersion(String notes) {
        if (notes == null) return "";
        Matcher m = GAME.matcher(first(paragraphs(notes)));
        return m.find() ? GitHubText.plain(m.group(1)) : "";
    }

    /** The notes without what Peanuts shows otherwise or does itself: the game sentence, Eden Duo's install steps, the SHA-256 line */
    static String body(String notes) {
        if (notes == null) return "";
        List<String> out = new ArrayList<>();
        List<String> ps = paragraphs(SHA.matcher(notes.replace("\r\n", "\n")).replaceAll(""));
        for (int i = 0; i < ps.size(); i++) {
            String p = ps.get(i);
            if (i == 0) p = GAME.matcher(p).replaceFirst("");
            if (EDEN.matcher(p).find()) p = LIST_LINE.matcher(p).find() ? lines(p) : sentences(p);
            if (!p.trim().isEmpty()) out.add(p.trim());
        }
        return String.join("\n\n", out);
    }

    /** One line for the versions list: the first thing the release says that is not the game sentence, a credit ("🥇 Commissioned by …", "By …") or a
     *  label ("Fixes:"); the first line at all when it says nothing else */
    static String summary(String notes) {
        List<GitHubText.Block> bs = GitHubText.parse(body(notes));
        for (GitHubText.Block b : bs) {
            String t = b.text.trim();
            boolean credit = !t.isEmpty() && Character.getType(t.codePointAt(0)) == Character.OTHER_SYMBOL || t.startsWith("By ") && t.length() < 130;
            if (credit || t.length() < 40 && t.endsWith(":")) continue;
            String p = GitHubText.plain(java.util.Collections.singletonList(b));
            if (!p.isEmpty()) return p;
        }
        return GitHubText.plain(bs);
    }

    private static List<String> paragraphs(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.replace("\r\n", "\n").split("\\n[ \\t]*\\n")) if (!p.trim().isEmpty()) out.add(p);
        return out;
    }

    private static String first(List<String> ps) { return ps.isEmpty() ? "" : ps.get(0); }

    /** A paragraph's sentences without those about Eden Duo */
    private static String sentences(String p) {
        StringBuilder b = new StringBuilder();
        for (String s : SENTENCE_END.split(p.trim())) if (!EDEN.matcher(s).find()) b.append(b.length() > 0 ? " " : "").append(s.trim());
        return b.toString();
    }

    /** A list's lines without those about Eden Duo */
    private static String lines(String p) {
        StringBuilder b = new StringBuilder();
        for (String l : p.split("\n")) if (!EDEN.matcher(l).find()) b.append(b.length() > 0 ? "\n" : "").append(l);
        return b.toString();
    }
}
