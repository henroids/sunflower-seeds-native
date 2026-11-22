package dev.peanuts.ui;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The library's walk over one root (LibraryScanner.scan): the dump folders and the files it finds, each root walked two levels deep. A dump folder holds the
 * game's code in a layout upstream's DumpLayout reads ({@link #isDump}), and is not walked further. Mods are left out (issue #46): a mod for Eden or Atmosphere is
 * a folder with exefs/ and romfs/ too, but its exefs/ holds patches and at most its own main.npdm, never the game's main; and the walk does not go into a folder
 * named mods, load (Eden's and yuzu's) or atmosphere below the root, where a mod that brings a whole main would sit. Before, a mod with a main.npdm was taken as
 * a dump of the game, and a dump wins over the game's NSP. Plain Java (no android.*) so peanuts/tests/test_library_walk.py runs it on the JVM.
 */
final class LibraryWalk {
    private LibraryWalk() {}

    /** Folders the walk does not go into: where emulators and Atmosphere keep mods */
    private static final Set<String> MOD_FOLDERS = new HashSet<>(Arrays.asList("mods", "load", "atmosphere"));

    /** What the walk found: a dump folder, or a file (any name: the scanner keeps the extensions it reads) */
    interface Sink {
        void dump(File dir);
        void file(File f);
    }

    static void walk(File dir, int depth, Sink out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            String n = f.getName();
            if (n.startsWith(".") || n.endsWith("-work")) continue;
            if (f.isDirectory()) {
                if (MOD_FOLDERS.contains(n.toLowerCase(Locale.ROOT))) continue;
                if (isDump(f)) out.dump(f);
                else if (depth < 2) walk(f, depth + 1, out);
            } else out.file(f);
        }
    }

    /**
     * Whether a folder is a dump folder: a version of it has the game's code (exefs/main) where upstream's DumpLayout.versions reads it. The base, base/ (in the
     * dump tools' layout, or in the console's as OunceCake serves it: base/program/0/); an update, updates/&lt;version&gt;/ in either layout (a dump of an
     * update alone carries the whole game); or one version's folder, exefs/ at its top. Before, the walk saw only base/exefs/main and exefs/main: an OunceCake
     * dump was not found, and an update-only dump was listed as its version's folder (updates/&lt;version&gt;) instead of the dump
     */
    static boolean isDump(File d) {
        if (new File(root(new File(d, "base")), "exefs/main").isFile()) return true;
        File[] ups = new File(d, "updates").listFiles(File::isDirectory);
        if (ups != null) for (File u : ups) if (u.getName().matches("\\d+") && new File(root(u), "exefs/main").isFile()) return true;
        return new File(d, "exefs/main").isFile();
    }

    /**
     * A version folder's package, the folder with its exefs/ and romfs/, as upstream's DumpLayout.root finds it: the version folder itself, or in the console's
     * layout program/0/ in it (program/0/program/ when a data content shares the index)
     */
    static File root(File verDir) {
        for (String r : new String[] {"", "program/0", "program/0/program"}) {
            File d = r.isEmpty() ? verDir : new File(verDir, r);
            if (new File(d, "exefs").isDirectory() || new File(d, "romfs").isDirectory()) return d;
        }
        return verDir;
    }
}
