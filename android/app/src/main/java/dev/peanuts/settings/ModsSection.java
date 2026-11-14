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
            if (variantProblem(c, titleId, variantPack) == null) return variantPack.getPath();
            if (notBuilt != null) notBuilt.run();
        } catch (RuntimeException e) {
            android.util.Log.w("peanuts", "mods: the pack to play: " + e);
        }
        return packDir;
    }

    /**
     * Why a start cannot take the variant's pack, null when it can: there is none, its build did not finish (ModdedBuild.variantProblem), or its own profile
     * is of another version of the app ({@link #staleProfile}). Play then starts the normal pack and says the mods are not built; "Build with mods" says why
     */
    private static String variantProblem(android.content.Context c, String titleId, File variantPack) {
        String why = ModdedBuild.variantProblem(variantPack);
        return why == null && staleProfile(c, titleId, variantPack) ? "it was made by another version of the app" : why;
    }

    /**
     * Whether the variant's own profile (profile.bin beside its pack: scripts/pack_android.py writes one for a build that adds a module or has its own memory
     * numbers, and the host reads it in place of the app's, dev.peanuts.build.GenericProfile) is of another schema than this app's profile of the game: their
     * first eight bytes, the magic and the schema version. An update of the app that changes the schema left the variant with a profile the host refuses at
     * the game's start ("schema version does not match"), until its mods were built again. Without a profile of its own, or without the app's to compare: no
     */
    private static boolean staleProfile(android.content.Context c, String titleId, File variantPack) {
        File own = new File(variantPack, "profile.bin");
        if (!own.isFile()) return false;
        byte[] a = new byte[8], b = new byte[8];
        try (java.io.DataInputStream x = new java.io.DataInputStream(new java.io.FileInputStream(own));
             java.io.DataInputStream y = new java.io.DataInputStream(c.getAssets().open("profiles/" + titleId + ".profile.bin"))) {
            x.readFully(a);
            y.readFully(b);
            return !java.util.Arrays.equals(a, b);
        } catch (IOException e) {
            return false;
        }
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
        // what an install or a delete left when the app ended in the middle of it: once per run, and never while one goes on (its folders are what a sweep looks for)
        if (deleteTitle == null && importTitle == null && SWEPT.add(home.getPath())) new Thread(() -> ModStore.sweepDeleted(ModRuntime.modsRoot(home), titleId), "peanuts-mod-sweep").start();
        if (importTitle == null && SWEPT.add("the app's cache")) ModImportUi.sweepStaging(a);
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
            bin.setOnClickListener(dev.peanuts.ui.Feel.row(v -> confirmDelete(m)));   // it opens the question: a tick, as a row that opens something
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
                if (ferr != null) dev.peanuts.ui.Feel.refused(a);
                if (ferr != null) Toast.makeText(a, "Could not turn " + m.name + (on ? " on: " : " off: ") + ferr, Toast.LENGTH_LONG).show();
                else if (!m.hasCode()) Toast.makeText(a, m.name + (on ? " is on" : " is off") + " from the game's next start.", Toast.LENGTH_SHORT).show();   // a code mod: the Build with mods row says what comes next
                renderCurrent();   // from disk: the switch shows what was saved
            });
        }, "peanuts-mod-toggle").start();
    }

    private void confirmDelete(ModStore.Mod m) {
        new AlertDialog.Builder(a).setTitle("Delete this mod?").setMessage(m.name + " and its files are deleted from this device" + (m.hasCode() ? ", and the builds with mods made with it" : "")
                        + ". The game and its saves are not changed.")
                .setPositiveButton("Delete", dev.peanuts.ui.Feel.confirm((d, w) -> startDelete(m))).setNegativeButton("Cancel", null).show();
    }

    /**
     * Deletes off the UI thread with its own row and percentage: a mod's files can be GBs on a memory card and take minutes (Luminescent: 1.7 GB, 3,464 files).
     * The mod leaves the list and the game at once (ModStore.remove renames its folder first); while the files go, install, the switches, the bins and Build
     * with mods are off. The end is shown in the row's place when this section is on screen, and not as a message over whatever is on screen by then
     * (a toast came up in another game).
     */
    private void startDelete(ModStore.Mod m) {
        if (deleteTitle != null || importTitle != null) return;
        boolean code = m.hasCode();
        // the build reads the mod's files, and the delete below takes the builds made with it away
        if (code && titleId.equals(buildTitle)) { message("Delete this mod", gameName + " is being built with its mods now. Delete " + m.name + " when the build has ended."); return; }
        deleteTitle = titleId; deleteName = m.name; deleteText = "Counting its files…"; deletePct = -1;
        renderCurrent();
        File root = ModRuntime.modsRoot(home), gameHome = home;
        String id = titleId, folder = m.dir.getName(), name = m.name;
        android.content.Context app = a.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                ModStore.remove(root, id, folder, (done, total) -> {
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct == deletePct) return;
                    MAIN.post(() -> { deletePct = pct; deleteText = pct < 0 ? "Counting its files…" : pct + " %."; renderCurrent(); });
                });
            } catch (IOException e) { err = e.getMessage(); }
            if (code) sweepVariants(app, id, gameHome);   // the mod is out of the list by now, also when not all of its files went
            final String ferr = err;
            MAIN.post(() -> {
                deleteTitle = null; deleteName = null; deleteText = null; deletePct = -1;
                if (ferr != null) SWEPT.remove(gameHome.getPath());   // so that the page's next opening looks for what is left, as the message below says
                if (ferr != null) android.util.Log.w("peanuts", "mods: delete of " + name + ": " + ferr);
                if (current != null && ferr != null) dev.peanuts.ui.Feel.refused(current.a);
                if (current != null && ferr != null) current.message("Delete failed", name + ": " + ferr + "\n\nWhat is left of it is removed the next time this page is opened.");
                renderCurrent();
            });
        }, "peanuts-mod-delete").start();
    }
    private static final java.util.Set<String> SWEPT = java.util.Collections.synchronizedSet(new java.util.HashSet<>());   // game folders whose interrupted deletes were looked for in this run

    /**
     * After a code mod is deleted: the builds with mods no choice of the switches gives any more go with it (ModStore.reachableVariantIds from the mods that
     * are left, ModdedBuild.sweepVariants). Each is a pack of its own, the modules and a copy of the shader library, which nothing else in the app removes; and
     * a mod installed again under the same name had the deleted one's build as "Ready". On the delete's thread. Nothing goes when the mods cannot be listed
     */
    private static void sweepVariants(android.content.Context c, String titleId, File home) {
        try {
            if (!ModRuntime.modsRoot(home).isDirectory()) return;
            java.util.Set<String> keep = ModStore.reachableVariantIds(titleId, ModRuntime.mods(home, titleId));
            File files = c.getExternalFilesDir(null);   // the app's packs/<id>, as dev.nxnative.android.AppStorage.pack (its root and packs are not public)
            int n = ModdedBuild.sweepVariants(titleId, keep, new File(files != null ? files : c.getFilesDir(), "packs"), dev.peanuts.PeanutsPrefs.buildGameDir(c, titleId), home);
            if (n > 0) android.util.Log.i("peanuts", "mods: " + titleId + ": " + n + " build(s) with mods of a deleted mod removed");
        } catch (RuntimeException e) {
            android.util.Log.w("peanuts", "mods: removing the builds of a deleted mod: " + e);
        }
    }

    // ---- Install mod…: the pickers' results come back through MainActivity.onActivityResult → dev.peanuts.ui.LibrarySettings.result ----

    private void install() {
        pickFor = this;
        ModImportUi.pick(a, titleId, () -> ModImportUi.pickFolder(a, LibrarySettings.REQ_MOD_FOLDER), () -> ModImportUi.pickZip(a, LibrarySettings.REQ_MOD_ZIP));
    }

    /** The folder picker's result. a: the activity that got it (the one the pick was started in may have been made again meanwhile) */
    public static void pickedFolder(Activity a, Uri tree) {
        ModsSection s = startImport();
        if (s != null) ModImportUi.importFolder(a, tree, ModRuntime.modsRoot(s.home), s.titleId, null, ModdedBuild.buildIds(s.pack), ModsSection::importProgress, (ok, message) -> importDone(a, ok, message));
    }

    /** The zip picker's result; displayName: the picked file's name, the mod's name when its layout has none */
    public static void pickedZip(Activity a, Uri doc, String displayName) {
        ModsSection s = startImport();
        if (s != null) ModImportUi.importZip(a, doc, ModRuntime.modsRoot(s.home), s.titleId, displayName, ModdedBuild.buildIds(s.pack), ModsSection::importProgress, (ok, message) -> importDone(a, ok, message));
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
    private static void importDone(Activity a, boolean ok, String message) {
        importTitle = null; importText = null;
        if (ok) dev.peanuts.ui.Feel.accepted(a); else dev.peanuts.ui.Feel.refused(a);   // installed, or refused (a mod for another game)
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
        // No module and no patch file in them (an exefs/ with a .pchtxt or a main.npdm alone): the build's extra arguments would be empty, and a job without
        // them is a build of the game itself, into the variant's folder and remembered as the game's own job (PackService)
        if (variantId == null || plan.source.isEmpty() && plan.patchSets.isEmpty()) {
            explain("⚠️", "Nothing to build", "The code mods that are on have no module (main, subsdk…) and no IPS patch in them. A .pchtxt patch is not one the app can build. Turn them off, or install them again.");
            return;
        }
        File variantPack = dev.nxnative.android.AppStorage.pack(a, variantId);
        String problem = variantProblem(a, titleId, variantPack);   // as packToPlay asks: the row says what Play does
        boolean ready = problem == null, built = !ModdedBuild.NOT_BUILT.equals(problem), building = buildTitle != null, mine = titleId.equals(buildTitle);
        String failed = FAILED.get(variantId);
        String value = mine ? "Building: " + buildText
                : building ? "Another game is being built with mods. Wait for it to finish."
                : failed != null ? "Failed: " + failed
                : ready ? "Ready. Play starts the game with its code mods."
                : built ? "The build with mods must be made again: " + problem + ". Until then Play starts the game without its code mods."
                : "Code mods need a build of their own: until it is made, Play starts the game without them. The normal build is kept as it is.";
        body.addView(ui.setting("🔨", "Build with mods", value, failed == null || mine ? null : v -> message("Build with mods failed", failed)), SectionUi.lp(0, 0));
        if (mine) return;
        LinearLayout buttons = SectionUi.buttons(a);
        SectionUi.add(buttons, ui.button(built ? "Rebuild with mods" : "Build with mods", ready ? SectionUi.QUIET : SectionUi.PRIMARY, dev.peanuts.ui.Feel.heavy(v -> startBuild(variantId, variantPack, plan))), !building && deleteTitle == null);
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
                if (ok) dev.peanuts.ui.Feel.done(act); else if (!BuildListener.CANCELLED.equals(code)) dev.peanuts.ui.Feel.refused(act);   // felt only while the app shows; a stop the user asked for is quiet
                Toast.makeText(act.getApplicationContext(), ok ? name + " with mods is ready." : "Build with mods failed: " + message, Toast.LENGTH_LONG).show();
                renderCurrent();
            }
        });
        render();
    }
}
