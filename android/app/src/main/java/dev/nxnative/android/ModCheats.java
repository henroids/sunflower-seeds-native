package dev.nxnative.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PEANUTS: the cheat files of the enabled mods (dev.peanuts.mods, a mod's cheats/ folder) in upstream's cheat list (Cheats.java, Game settings › Cheats). A Peanuts
 * Native file in upstream's launcher package, as ScreenMode.java: Cheats' merge and entries are package-private. No Android classes.
 * <p>
 * The host reads one cheats folder (DM_CHEATS). Upstream's start writes the profile's file and the player's imported files there as one merged file with the
 * player's toggles; this fork's start staged the mods' files into a folder of its own and passed that. With both, the later line won: the host ran the mods'
 * cheats while the in-game panel listed upstream's. {@link #install} is Cheats.install with the mods' files of the game's build added after the imported ones,
 * each under its own section ("Imported: mod &lt;mod&gt;: &lt;file&gt;"), so every cheat is in the one list the panel shows and toggles, and one folder is passed.
 * A game whose Cheats switch is off, or whose profile has no cheats, takes the mods' folder as before (GameActivity.start).
 */
final class ModCheats {
    private ModCheats() {}

    /** The mods' cheat files of this build, in the mods' order (the first enabled mod first), each folder's by name */
    static List<Cheats.Named> of(List<File> modCheatDirs, String build) throws IOException {
        List<Cheats.Named> out = new ArrayList<>();
        if (modCheatDirs == null) return out;
        for (File dir : modCheatDirs) {
            File[] l = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".txt"));
            if (l == null) continue;
            java.util.Arrays.sort(l, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
            String mod = dir.getParentFile() == null ? dir.getName() : dir.getParentFile().getName();
            for (File f : l) {
                String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (build.equals(Cheats.buildOf(t, f.getName()))) out.add(new Cheats.Named("mod " + mod + ": " + f.getName().replaceFirst("(?i)\\.txt$", ""), t));
            }
        }
        return out;
    }

    /** Cheats.install with the mods' files: the merged file and its toggles in folder; the merged file's entries, null when there is no cheat at all */
    static List<Cheats.Entry> install(File shipped, File folder, String build, Set<String> on, List<File> modCheatDirs) throws IOException {
        List<Cheats.Named> mods = of(modCheatDirs, build);
        if (mods.isEmpty()) return Cheats.install(shipped, folder, build, on);
        String s = shipped != null ? new String(Files.readAllBytes(shipped.toPath()), StandardCharsets.UTF_8) : "";
        List<Cheats.Named> all = new ArrayList<>(Cheats.importedOf(folder, build));
        all.addAll(mods);
        String merged = Cheats.merge(s, all);
        List<Cheats.Entry> list = Cheats.parse(merged);
        boolean any = false;
        for (Cheats.Entry e : list) if (e.toggles()) any = true;
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("cannot create " + folder);
        File[] old = folder.listFiles((d, n) -> n.endsWith(".txt") || n.endsWith(".enabled"));
        if (old != null) for (File f : old) if (!f.delete()) throw new IOException("cannot remove " + f);
        if (!any) return null;
        write(new File(folder, build + ".txt"), merged);
        write(new File(folder, build + ".enabled"), Cheats.enabledFile(list, on));
        return list;
    }

    private static void write(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(f)) { tmp.delete(); throw new IOException("cannot write " + f); }
    }
}
