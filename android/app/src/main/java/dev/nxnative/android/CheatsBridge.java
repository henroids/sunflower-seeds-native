package dev.nxnative.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.peanuts.mods.CheatFile;
import dev.peanuts.mods.CheatImport;
import dev.peanuts.mods.CheatPick;
import dev.peanuts.mods.CheatSources;

/**
 * PEANUTS: Game settings' Cheat files for every game, on the game's cheat sources (dev.peanuts.mods.CheatSources) instead of upstream's imported/ folder. A
 * Peanuts Native file in upstream's launcher package, as ModCheats.java: Prefs, Pack, Profile, Ui and PadFocus are package-private. CheatsUi.rows asks
 * {@link #filesRow} for the row (PEANUTS hook); the sheet, its pickers and their results are here (LibrarySettings.result asks {@link #result} first).
 * <ul>
 * <li>Import cheat file… takes a .txt or a .zip, Import cheat folder… a folder, each in the Atmosphère, Eden / Yuzu or Ryujinx layout or as a bare build file
 *     (dev.peanuts.mods.CheatImport): the files of the game's build become a source, the other versions' files are kept with it; another game's, a file that
 *     names no build, or another version alone are refused with upstream's words.</li>
 * <li>A source's line: on or off as a whole, or removed. Export cheats… writes the sources in upstream's export format; Import cheats… reads one as before (into
 *     imported/, which a start or this sheet makes sources: CheatSources.migrate).</li>
 * </ul>
 */
public final class CheatsBridge {
    private CheatsBridge() {}

    /** Request codes: a cheat file (.txt, .zip), a cheat folder, an export, an export to import (60 to 73: LibrarySettings and its neighbours; 80 to 82: CheatsUi's) */
    public static final int REQ_FILE = 83, REQ_FOLDER = 84, REQ_EXPORT = 85, REQ_IMPORT_ZIP = 86;
    private static Game pendingFor;
    private static Runnable pendingAgain;

    /** The game's cheats folder: the merged file a start writes (DM_CHEATS) and sources/ with the player's imports */
    public static File folder(Context c, String titleId) { return CheatsUi.folder(c, titleId); }

    /** The game's main builds, first 16 hex digits upper case: its pack's build rows, else its profile's builds (a game not prepared yet); empty when neither knows */
    public static Set<String> builds(String titleId, String packDir) {
        if (packDir != null && !packDir.isEmpty()) {
            try { Set<String> b = Cheats.builds16(String.join(" ", Pack.read(new File(packDir)).builds)); if (!b.isEmpty()) return b; } catch (IOException | RuntimeException ignored) { }
        }
        Profile p = Profile.of(titleId);
        return Cheats.builds16(p == null ? "" : p.builds);
    }

    /** The version game.tsv names for a main build ("2.0.2"), null when it names none */
    public static String version(String titleId, String build16) {
        Profile p = Profile.of(titleId);
        if (p == null || build16 == null) return null;
        try {
            for (Map.Entry<Long, Map<String, String>> e : p.buildMap().entrySet()) {
                String main = e.getValue().get("main");
                if (main != null && main.length() >= 16 && main.substring(0, 16).equalsIgnoreCase(build16)) return p.display(e.getKey());
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    /** The cheats that come with the app for this build (the profile's file in the pack), null when the pack has none */
    public static String appText(String packDir, String build) {
        File f = packDir == null || packDir.isEmpty() ? null : Cheats.packFile(new File(packDir), build);
        if (f == null) return null;
        try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (IOException e) { return null; }
    }

    /** Upstream's imported/ files made sources (once; CheatSources.migrate), quietly: a failure leaves them where they are, and a start reads them there */
    static void migrate(File folder) {
        try { CheatSources.migrate(folder, System.currentTimeMillis()); } catch (IOException | RuntimeException e) { android.util.Log.w("peanuts", "cheats: imported files not moved: " + e); }
    }

    private static String first(Set<String> s) { return s.isEmpty() ? null : s.iterator().next(); }

    // ---- The row and its sheet ----

    /** CheatsUi.rows (PEANUTS hook): Cheat files, what the game has, opening the sheet. Its tile is the Peanuts folder icon (the sheet imports files and folders) */
    static Ui.Setting filesRow(MainActivity a, Game g, Runnable again) {
        File folder = folder(a, g.titleId);
        migrate(folder);
        int n = CheatSources.list(folder).size();
        Profile p = Profile.of(g.titleId);
        boolean own = p != null && p.cheats;   // a profile with its own cheat file
        String value = own ? (n == 0 ? "The game's own" : "The game's own and " + n + " imported") : n == 0 ? "None imported yet" : n + " imported";
        Ui.Setting row = new Ui.Setting(a, "", a.getString(R.string.cheats_files_title), v -> sheet(a, g, again)).value(value)
                .note("Atmosphère, Eden or Ryujinx cheats for this game's build: a .txt, a .zip or a folder").chevron(a).sub();
        dev.peanuts.ui.Icons.tile(row.tile, "folder");   // after sub(): the icon fits the smaller tile
        return row;
    }

    /** The sheet: the game's own file, each source (a tap: on or off, remove), then Import cheat file…, Import cheat folder…, Export cheats…, Import cheats… */
    static void sheet(MainActivity a, Game g, Runnable again) {
        File folder = folder(a, g.titleId);
        migrate(folder);
        String build = first(builds(g.titleId, g.packDir));
        Profile p = Profile.of(g.titleId);
        File shipped = g.packDir != null && build != null ? Cheats.packFile(new File(g.packDir), build) : null;
        List<CheatSources.Source> sources = CheatSources.list(folder);
        List<String> items = new ArrayList<>();
        boolean own = p != null && p.cheats;
        if (own) items.add(shipped != null ? a.getString(R.string.cheats_shipped, shipped.getName()) : a.getString(R.string.cheats_shipped_missing));
        for (CheatSources.Source s : sources) items.add(sourceLine(s, build));
        items.add(a.getString(R.string.cheats_import_file));
        items.add("Import cheat folder…");
        items.add(a.getString(R.string.cheats_export));
        items.add(a.getString(R.string.cheats_import_zip));
        int base = own ? 1 : 0;
        new PadFocus.Builder(a).setTitle(a.getString(R.string.cheats_files_title)).setItems(items.toArray(new String[0]), (d, w) -> {
            if (w < base) return;   // the game's own file: nothing to do
            if (w < base + sources.size()) { source(a, folder, sources.get(w - base), build, again); return; }
            pendingFor = g;
            pendingAgain = again;
            int k = w - base - sources.size();
            if (k == 0) pick(a, new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_FILE);
            else if (k == 1) pick(a, new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER);
            else if (k == 2) pick(a, new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, Cheats.zipName(g.titleId, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()))), REQ_EXPORT);
            else pick(a, new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT_ZIP);
        }).setNegativeButton("Close", null).show();
    }

    /** A source's line in the sheet: its name, how many cheats it has for this build, off */
    static String sourceLine(CheatSources.Source s, String build) {
        String t = null;
        try { t = s.text(build); } catch (IOException ignored) { }
        String what = t == null ? "for another version" : CheatFile.parse(t, -1).switchable() + " cheats";
        return s.name + " (" + what + (s.enabled ? "" : ", off") + ")";
    }

    private static void source(MainActivity a, File folder, CheatSources.Source s, String build, Runnable again) {
        String t = null;
        try { t = s.text(build); } catch (IOException ignored) { }
        int n = t == null ? 0 : CheatFile.parse(t, -1).switchable();
        String msg = (t == null ? "None of its cheats are for this game's build: they start to work when the game is that version." : n + " cheats for this game's build.")
                + (s.enabled ? " They are listed in the in-game panel from the next start." : " It is off: its cheats are not in the in-game panel.");
        new PadFocus.Builder(a).setTitle(s.name).setMessage(msg)
                .setPositiveButton(s.enabled ? "Turn off" : "Turn on", (x, y) -> {
                    try { CheatSources.enabled(folder, s.id, !s.enabled); } catch (IOException e) { a.error(a.getString(R.string.cheats_refused, "It could not be changed. Its files are as they were.")); }
                    again.run();
                })
                .setNeutralButton(a.getString(R.string.cheats_remove), (x, y) -> new PadFocus.Builder(a).setTitle(a.getString(R.string.cheats_remove) + " " + s.name + "?")
                        .setMessage("Its cheats leave the in-game panel from the next start, and its files are deleted from this device. The game and its saves are not changed.")
                        .setPositiveButton(a.getString(R.string.cheats_remove), (u, v) -> { CheatSources.remove(folder, s.id); again.run(); })
                        .setNegativeButton("Cancel", null).show())
                .setNegativeButton("Cancel", null).show();
    }

    private static void pick(Activity a, Intent i, int request) {
        try { a.startActivityForResult(i, request); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(a, "This device has no file picker.", Toast.LENGTH_LONG).show(); }
    }

    // ---- The pickers' results ----

    /** LibrarySettings.result (PEANUTS): the documents of the sheet's pickers; true when the request was one of them */
    public static boolean result(Activity act, int request, int result, Intent data) {
        if (request < REQ_FILE || request > REQ_IMPORT_ZIP) return false;
        Game g = pendingFor;
        Runnable again = pendingAgain;
        pendingFor = null; pendingAgain = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || g == null || !(act instanceof MainActivity)) return true;
        MainActivity a = (MainActivity) act;
        Uri doc = data.getData();
        File folder = folder(a, g.titleId);
        Set<String> builds = builds(g.titleId, g.packDir);
        if (request == REQ_FILE || request == REQ_FOLDER) {
            CheatPick.read(a, doc, request == REQ_FOLDER, g.titleId, builds, a.getString(R.string.cheats_import_file).replace("…", ""), r -> {
                taken(a, g, folder, builds, r);
                a.gameSettingsTop();
            });
            return true;
        }
        try {
            if (request == REQ_EXPORT) {
                try (OutputStream os = a.getContentResolver().openOutputStream(doc)) {
                    if (os == null) throw new IOException("the place picked cannot be written");
                    CheatSources.exportZip(os, g.titleId, first(builds), folder, Prefs.cheats(a, g.titleId), Prefs.cheatsOn(a, g.titleId));
                }
                Toast.makeText(a, a.getString(R.string.cheats_exported), Toast.LENGTH_LONG).show();
            } else {   // upstream's Import cheats…: its zip into imported/, then made sources
                Cheats.ZipContent c;
                try (InputStream in = a.getContentResolver().openInputStream(doc)) {
                    if (in == null) throw new IOException("the zip cannot be opened");
                    c = Cheats.readZip(in, g.titleId, builds);
                }
                Cheats.takeZip(folder, c, builds);
                int n = CheatSources.dropDuplicates(folder, CheatSources.migrate(folder, System.currentTimeMillis()), first(builds));   // its files as sources; one the game has already is not added twice
                if (c.master != null) Prefs.cheats(a, g.titleId, c.master);
                if (c.on != null) Prefs.cheatsOn(a, g.titleId, c.on);
                Toast.makeText(a, a.getString(R.string.cheats_zip_done, n), Toast.LENGTH_LONG).show();
            }
        } catch (IOException | RuntimeException e) {
            a.error(a.getString(R.string.cheats_refused, e.getMessage()));
        }
        if (again != null) a.gameSettingsTop();
        return true;
    }

    /** A pick read: its sources kept (the game's build and the other versions with it), or upstream's refusal with the reason */
    static void taken(MainActivity a, Game g, File folder, Set<String> builds, CheatPick.Result r) {
        if (r.error != null) { a.error(a.getString(R.string.cheats_refused, r.error)); return; }
        CheatImport.Found f = r.found;
        String build = first(builds);
        if (f.mod) { a.error(a.getString(R.string.cheats_refused, "It is a mod (it has romfs or exefs folders). Install it with Install mod… in the Mods section below.")); return; }
        if (builds.isEmpty()) { a.error(a.getString(R.string.cheats_refused, "This game's build ID is not known yet: Prepare the game first.")); return; }
        if (f.forGame.isEmpty()) {
            String why;
            if (!f.otherVersion.isEmpty()) {
                List<String> theirs = new ArrayList<>();
                for (CheatImport.Hit h : f.otherVersion) if (!theirs.contains(h.bid)) theirs.add(h.bid);
                why = "It is for build " + String.join(", ", theirs) + "; this game is build " + String.join(", ", builds) + ". Cheats of another build write the wrong places, so it was not imported.";
            } else if (!f.otherGame.isEmpty()) why = "It is for another game (" + f.otherGame.get(0).tid + "), not " + g.titleId + ".";
            else if (!f.noBuild.isEmpty()) why = "It does not say which build of the game it is for: name it <build ID>.txt (as Atmosphère cheat files are named) or start it with a line \"build <build ID>\". This game is build " + build + ".";
            else why = "There is no cheat file in it ([Name] followed by code lines).";
            a.error(a.getString(R.string.cheats_refused, why));
            return;
        }
        List<String> added = new ArrayList<>(), same = new ArrayList<>();
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Map<String, String>> e : f.sources().entrySet()) {
                CheatSources.Source had = null;
                for (Map.Entry<String, String> file : e.getValue().entrySet()) if (builds.contains(file.getKey())) had = CheatSources.sameAs(folder, file.getKey(), file.getValue());
                if (had != null) { same.add(had.name); continue; }
                added.add(CheatSources.add(folder, e.getKey(), r.from, e.getValue(), now++).name);
            }
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("peanuts", "cheats: keeping a source: " + e);
            a.error(a.getString(R.string.cheats_refused, "It could not be kept in the app's storage. Free some space and import it again."));
            return;
        }
        if (added.isEmpty()) { Toast.makeText(a, "Imported already: " + String.join(", ", same) + ".", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(a, a.getString(R.string.cheats_imported, String.join(", ", added)), Toast.LENGTH_LONG).show();
    }
}
