package dev.peanuts.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.peanuts.build.BuildListener;
import dev.peanuts.build.ModBuild;
import dev.peanuts.build.ModBuildPipeline;
import dev.peanuts.build.ModBuildRequest;
import dev.peanuts.build.ModdedBuild;
import dev.peanuts.mods.ModImportUi;
import dev.peanuts.mods.ModRuntime;
import dev.peanuts.mods.ModStore;
import dev.peanuts.ui.LibrarySettings;

/**
 * The Mods section of a game's settings page (GameSettings.build, the Game tab, above Companion; HOOKS.md): one row per installed mod with what it carries
 * (files, cheats, code), a switch (on or off from the game's next start) and a bin (delete, after a question), then "Install mod…" (a folder or a zip:
 * dev.peanuts.mods.ModImportUi). A game that is not built has one row that says so: a mod is checked against the pack's build IDs when it is installed, and a
 * code mod is built from the pack. Local files only, never a catalog and never a download (DESIGN-MODS.md: a mod is someone else's edit of the game).
 * <p>
 * Files and cheats apply on the normal build (dev.peanuts.mods.ModRuntime, at the game's start). Code (exefs, IPS patches) needs a build of its own, a variant
 * pack beside the normal one (ModStore.variantId: one per set of enabled code mods), which the section's "Build with mods" makes. The normal build is never
 * changed. Every start of the game (the page's Play, Home, a shortcut, a frontend: all go through MainActivity.launch) asks {@link #packToPlay} for the pack:
 * the variant's while code mods are on and their build is ready, else the normal one. So a mod that is on plays, and one that is off or deleted does not.
 * <p>
 * The page is built again after a choice of another row, and a pick, an import or a build outlives that: what is in progress is kept per process (PICK, IMPORT,
 * BUILD, FAILED), and whichever section shows now ({@link #current}) is the one told to render again.
 */
public final class ModsSection {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** The section on the page now: the one an import's or a build's news is rendered into (null: none shows) */
    private static ModsSection current;
    /** The section "Install mod…" was tapped in, for the picker's result (LibrarySettings.result): the game the pick was for, whatever the page shows by then */
    private static ModsSection pickFor;
    /** The import or the build with mods in progress: the game's title ID and its line; null: none */
    private static String importTitle, importText, buildTitle, buildText;
    private static String deleteTitle, deleteName, deleteText;   // a mod being deleted (one at a time, as an import): its game, its name and how far it is
    private static int deletePct = -1;
    /** Variant ID → why its build failed, until it is started again */
    private static final Map<String, String> FAILED = new HashMap<>();

    private final Activity a;
    private final SectionUi ui;
    private final String titleId, gameName;
    private final File game, pack, home;   // the dump folder or .nup, its pack (null: not built), and the game's folder the mods are in (ModRuntime.home, as GameActivity finds it)
    private final LinearLayout body;

    /**
     * The section. gameDir and packDir are the game's entry in upstream's list (Game.gameDir, Game.packDir).
     */
    public static View view(Activity a, String titleId, String gameName, String gameDir, String packDir) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        SectionUi ui = new SectionUi(a);
        box.addView(ui.section("Mods"), SectionUi.lp(0, 12));
        ModsSection s = new ModsSection(a, ui, titleId, gameName, gameDir, packDir);
        box.addView(s.body, SectionUi.lp(0, 0));
        current = s;
        // the page left, or built again: this section's views are gone, so nothing more is rendered into them (a newer section has taken current by then)
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { if (current == s) current = null; }
        });
        s.render();
        return box;
    }

    private ModsSection(Activity a, SectionUi ui, String titleId, String gameName, String gameDir, String packDir) {
        this.a = a; this.ui = ui; this.titleId = titleId; this.gameName = gameName;
        game = gameDir == null || gameDir.isEmpty() ? null : new File(gameDir);
        File p = packDir == null || packDir.isEmpty() ? null : new File(packDir);
        pack = p != null && new File(p, "pack.tsv").isFile() ? p : null;
        home = pack == null ? null : ModRuntime.home(a, titleId, game, p.getParentFile());
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
    }

    /**
     * The pack a start of this game uses (MainActivity.launch's PEANUTS hook): the variant's when code mods are on and their build is ready, else packDir as it
     * is. With code mods on and no build of them yet the game starts without them, and notBuilt (may be null) is run to say so. Anything unreadable: packDir
     */
    public static String packToPlay(android.content.Context c, String titleId, String gameDir, String packDir, Runnable notBuilt) {
        try {
            File p = packDir == null || packDir.isEmpty() ? null : new File(packDir);
            if (p == null || !new File(p, "pack.tsv").isFile()) return packDir;
            File game = gameDir == null || gameDir.isEmpty() ? null : new File(gameDir);
            File home = ModRuntime.home(c, titleId, game, p.getParentFile());
            List<ModStore.Mod> codeMods = ModStore.enabledCodeMods(ModRuntime.mods(home, titleId));
            if (codeMods.isEmpty()) return packDir;
            String variantId = ModStore.variantId(titleId, codeMods);
            if (variantId == null) return packDir;
            File variantPack = dev.nxnative.android.AppStorage.pack(c, variantId);
            if (new File(variantPack, "pack.tsv").isFile()) return variantPack.getPath();
            if (notBuilt != null) notBuilt.run();
        } catch (RuntimeException e) {
            android.util.Log.w("peanuts", "mods: the pack to play: " + e);
        }
        return packDir;
    }

    private static void renderCurrent() { if (current != null) current.render(); }

    /** Builds the rows again from what is on disk (the mods' folders and manifests are small: read here, as the Companion row reads its package) */
    private void render() {
        body.removeAllViews();
        if (home == null) {
            View row = ui.setting("🧩", "Mods", "Build the game first", null);
            row.setEnabled(false);
            row.setAlpha(0.5f);
            body.addView(row, SectionUi.lp(0, 0));
            return;
        }
        List<ModStore.Mod> mods = ModRuntime.mods(home, titleId);
        boolean deleting = titleId.equals(deleteTitle), importing = titleId.equals(importTitle) || deleting;   // either: the list's switches and bins wait
        if (deleteTitle == null && SWEPT.add(home.getPath())) new Thread(() -> ModStore.sweepDeleted(ModRuntime.modsRoot(home), titleId), "peanuts-mod-sweep").start();
        if (mods.isEmpty() && !importing) body.addView(ui.setting("🧩", "No mods installed", "Files, cheats and code mods from this device", null), SectionUi.lp(0, 0));
        for (ModStore.Mod m : mods) body.addView(modRow(m, !importing), SectionUi.lp(0, 0));
        if (titleId.equals(importTitle)) body.addView(ui.setting("📥", "Installing a mod", importText, null), SectionUi.lp(0, 0));
        if (deleting) body.addView(ui.setting("🗑", "Deleting " + deleteName, deleteText + " Install, the switches and Build with mods wait for it.", null), SectionUi.lp(0, 0));
        if (ModStore.needsBuild(mods)) buildRows(mods);
        LinearLayout buttons = SectionUi.buttons(a);
        SectionUi.add(buttons, ui.button("Install mod…", SectionUi.PRIMARY, v -> install()), importTitle == null && deleteTitle == null);   // one import or delete at a time: its line is one field
        body.addView(buttons, SectionUi.lp(0, 4));
    }

    /** One mod: its name, what it carries, its switch, and the bin after the switch. The switch and the bin are off while a mod is being installed (its folder is being written) */
    private View modRow(ModStore.Mod m, boolean enabled) {
        View row = ui.toggle(m.hasCode() ? "⚙️" : "🧩", m.name, what(m), m.enabled, (b, on) -> setEnabled(m, on));
        if (!enabled) setEnabledDeep(row, false);
        if (row instanceof LinearLayout) {
            TextView bin = new TextView(a);
            bin.setText("🗑");
            bin.setTextSize(17);
            bin.setGravity(Gravity.CENTER);
            bin.setContentDescription("Delete " + m.name);
            bin.setFocusable(true);
            bin.setEnabled(enabled);
            bin.setAlpha(enabled ? 1f : 0.4f);
            bin.setOnClickListener(v -> confirmDelete(m));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(SectionUi.dp(44), SectionUi.dp(44));
            p.leftMargin = SectionUi.dp(4);
            ((LinearLayout) row).addView(bin, p);
        }
        return row;
    }

    private static void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) setEnabledDeep(((android.view.ViewGroup) v).getChildAt(i), on);
    }

    /** What a mod carries, as ModStore knows it: "Files · Cheats", "Code: needs a build with mods" */
    private static String what(ModStore.Mod m) {
        List<String> parts = new ArrayList<>();
        if (m.hasRomfs()) parts.add("Files");
        if (m.hasCheats()) parts.add("Cheats");
        if (m.hasCode()) parts.add("Code: needs a build with mods");
        return parts.isEmpty() ? "Empty" : String.join(" · ", parts);
    }

    private void setEnabled(ModStore.Mod m, boolean on) {
        new Thread(() -> {
            String err = null;
            try { ModStore.setEnabled(ModRuntime.modsRoot(home), titleId, m.dir.getName(), on); } catch (IOException e) { err = e.getMessage(); }
            final String ferr = err;
            MAIN.post(() -> {
                if (ferr != null) Toast.makeText(a, "Could not turn " + m.name + (on ? " on: " : " off: ") + ferr, Toast.LENGTH_LONG).show();
                else if (!m.hasCode()) Toast.makeText(a, m.name + (on ? " is on" : " is off") + " from the game's next start.", Toast.LENGTH_SHORT).show();   // a code mod: the Build with mods row says what comes next
                renderCurrent();   // from disk: the switch shows what was saved
            });
        }, "peanuts-mod-toggle").start();
    }

    private void confirmDelete(ModStore.Mod m) {
        new AlertDialog.Builder(a).setTitle("Delete this mod?").setMessage(m.name + " and its files are deleted from this device. The game and its saves are not changed.")
                .setPositiveButton("Delete", (d, w) -> startDelete(m)).setNegativeButton("Cancel", null).show();
    }

    /**
     * Deletes off the UI thread with its own row and percentage: a mod's files can be GBs on a memory card and take minutes (Luminescent: 1.7 GB, 3,464 files).
     * The mod leaves the list and the game at once (ModStore.remove renames its folder first); while the files go, install, the switches, the bins and Build
     * with mods are off. The end is shown in the row's place when this section is on screen, and not as a message over whatever is on screen by then
     * (a toast came up in another game).
     */
    private void startDelete(ModStore.Mod m) {
        if (deleteTitle != null || importTitle != null) return;
        deleteTitle = titleId; deleteName = m.name; deleteText = "Counting its files…"; deletePct = -1;
        renderCurrent();
        File root = ModRuntime.modsRoot(home);
        String id = titleId, folder = m.dir.getName(), name = m.name;
        new Thread(() -> {
            String err = null;
            try {
                ModStore.remove(root, id, folder, (done, total) -> {
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct == deletePct) return;
                    MAIN.post(() -> { deletePct = pct; deleteText = pct < 0 ? "Counting its files…" : pct + " %."; renderCurrent(); });
                });
            } catch (IOException e) { err = e.getMessage(); }
            final String ferr = err;
            MAIN.post(() -> {
                deleteTitle = null; deleteName = null; deleteText = null; deletePct = -1;
                if (ferr != null) android.util.Log.w("peanuts", "mods: delete of " + name + ": " + ferr);
                if (current != null && ferr != null) current.message("Delete failed", name + ": " + ferr + "\n\nWhat is left of it is removed the next time this page is opened.");
                renderCurrent();
            });
        }, "peanuts-mod-delete").start();
    }
    private static final java.util.Set<String> SWEPT = java.util.Collections.synchronizedSet(new java.util.HashSet<>());   // game folders whose interrupted deletes were looked for in this run

    // ---- Install mod…: the pickers' results come back through MainActivity.onActivityResult → dev.peanuts.ui.LibrarySettings.result ----

    private void install() {
        pickFor = this;
        ModImportUi.pick(a, titleId, () -> ModImportUi.pickFolder(a, LibrarySettings.REQ_MOD_FOLDER), () -> ModImportUi.pickZip(a, LibrarySettings.REQ_MOD_ZIP));
    }

    /** The folder picker's result. a: the activity that got it (the one the pick was started in may have been made again meanwhile) */
    public static void pickedFolder(Activity a, Uri tree) {
        ModsSection s = startImport();
        if (s != null) ModImportUi.importFolder(a, tree, ModRuntime.modsRoot(s.home), s.titleId, null, ModdedBuild.buildIds(s.pack), ModsSection::importProgress, (ok, message) -> importDone(a, message));
    }

    /** The zip picker's result; displayName: the picked file's name, the mod's name when its layout has none */
    public static void pickedZip(Activity a, Uri doc, String displayName) {
        ModsSection s = startImport();
        if (s != null) ModImportUi.importZip(a, doc, ModRuntime.modsRoot(s.home), s.titleId, displayName, ModdedBuild.buildIds(s.pack), ModsSection::importProgress, (ok, message) -> importDone(a, message));
    }

    /** The section the pick was for, with its import marked as started; null when there is none (the app was started again while the picker showed) or another import runs */
    private static ModsSection startImport() {
        ModsSection s = pickFor;
        pickFor = null;
        if (s == null || s.home == null || importTitle != null) return null;
        importTitle = s.titleId; importText = "Starting…";
        renderCurrent();
        return s;
    }

    private static void importProgress(long done, long total, String name) {
        importText = total > 0 ? (done * 100 / total) + " % · " + name : name;
        renderCurrent();
    }

    /** The end of an import: its sentence (installed, or why not: a dialog, since a refusal such as a mod for another game is longer than a toast holds) */
    private static void importDone(Activity a, String message) {
        importTitle = null; importText = null;
        if (!a.isFinishing() && !a.isDestroyed()) new AlertDialog.Builder(a).setTitle("Install mod").setMessage(message).setPositiveButton("OK", null).show();
        renderCurrent();
    }

    // ---- Code mods: the build with mods (dev.peanuts.build.ModBuild / ModdedBuild / ModBuildPipeline) and its Play ----

    /** The rows under the list while a code mod is on: what stops a build, or its state (building, failed and why, ready) with Build with mods */
    private void buildRows(List<ModStore.Mod> mods) {
        List<String> profileModules = ModdedBuild.normalModules(pack);
        if (profileModules == null) { explain("⚠️", "Cannot build with mods", "This game's pack has no module list (pack.tsv). Rebuild the game, then build with mods."); return; }
        List<ModStore.Mod> codeMods = ModStore.enabledCodeMods(mods);
        ModBuild.Plan plan = ModBuild.compose(profileModules, codeMods, dev.nxnative.android.BuildConfig.NX_GENERIC_HOST);
        if (plan.refusal != null) { explain("⚠️", "Cannot build with these mods", plan.refusal); return; }
        String exl = ModdedBuild.exlaunchWarning(profileModules, plan);
        if (exl != null) explain("⚠️", "A module's hooks will not run", exl);
        String variantId = ModStore.variantId(titleId, codeMods);
        if (variantId == null) { explain("⚠️", "Nothing to build", "The code mods that are on have no exefs files or IPS patches in them. Turn them off, or install them again."); return; }
        File variantPack = dev.nxnative.android.AppStorage.pack(a, variantId);
        boolean ready = new File(variantPack, "pack.tsv").isFile(), building = buildTitle != null, mine = titleId.equals(buildTitle);
        String failed = FAILED.get(variantId);
        String value = mine ? "Building: " + buildText
                : building ? "Another game is being built with mods. Wait for it to finish."
                : failed != null ? "Failed: " + failed
                : ready ? "Ready. Play starts the game with its code mods."
                : "Code mods need a build of their own: until it is made, Play starts the game without them. The normal build is kept as it is.";
        body.addView(ui.setting("🔨", "Build with mods", value, failed == null || mine ? null : v -> message("Build with mods failed", failed)), SectionUi.lp(0, 0));
        if (mine) return;
        LinearLayout buttons = SectionUi.buttons(a);
        SectionUi.add(buttons, ui.button(ready ? "Rebuild with mods" : "Build with mods", ready ? SectionUi.QUIET : SectionUi.PRIMARY, v -> startBuild(variantId, variantPack, plan)), !building && deleteTitle == null);
        body.addView(buttons, SectionUi.lp(0, 0));
    }

    /** A row whose sentence may be longer than its three lines: a tap shows all of it */
    private void explain(String emoji, String title, String text) {
        body.addView(ui.setting(emoji, title, text, v -> message(title, text)), SectionUi.lp(0, 0));
    }

    private void message(String title, String text) { new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("OK", null).show(); }

    private void startBuild(String variantId, File variantPack, ModBuild.Plan plan) {
        ModBuildRequest r = new ModBuildRequest();
        r.titleId = titleId; r.name = gameName; r.dump = game; r.normalPack = pack; r.variantId = variantId;
        r.workDir = ModdedBuild.workDir(home, variantId);
        r.packDir = variantPack;
        r.modArgs = ModdedBuild.rewriteArgvJoined(plan);
        FAILED.remove(variantId);
        buildTitle = titleId; buildText = "starting…";
        String name = gameName;
        Activity act = a;
        ModBuildPipeline.start(a, r, new BuildListener() {   // a request that cannot be built comes back through done() too, with why
            @Override public void progress(String step, int percent, String detail) { buildText = detail + " (" + percent + " %)"; renderCurrent(); }
            @Override public void done(boolean ok, String message, String code) {
                buildTitle = null; buildText = null;
                if (!ok) FAILED.put(variantId, message);
                Toast.makeText(act.getApplicationContext(), ok ? name + " with mods is ready." : "Build with mods failed: " + message, Toast.LENGTH_LONG).show();
                renderCurrent();
            }
        });
        render();
    }
}
