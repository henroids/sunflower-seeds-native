package dev.peanuts.mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.peanuts.ui.PUi;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The Mods sheet (DESIGN-MODS.md "a Mods section per game", this task's step 2): opened from the game's own sheet ({@code GameSheet}'s "Mods" row),
 * one {@link ModStore.Mod} per row — name, kind (Files/Code), size, an enable switch, move up/move down (order = priority, first enabled wins, same
 * order {@code DM_ROMFS_OVERLAY} itself checks), and Remove (confirmed). "Add mod…" imports a folder or zip through {@link ModImportUi}. Local-import
 * only, same as the file picker everywhere else in this app: never a catalog, never a network call (PEANUTS.md/DESIGN-MODS.md's copyright note — a
 * mod is someone else's unlicensed edit of the game, unlike a Companion's own original code, so this app must never host or fetch one).
 */
public final class ModsSheet {
    /** The open sheet, so {@code dev.peanuts.ui.LibrarySettings.result} (another package: the picker's request codes and onActivityResult hookup
     *  live there, since it already owns that range) can forward a picker result here, the same shape as {@code GameSheet.current} */
    public static ModsSheet current;

    private final Activity a;
    private final String titleId;
    private final String gameName;
    private AlertDialog dialog;
    private LinearLayout box;
    private boolean busy;
    private String busyText = "";

    private ModsSheet(Activity a, String titleId, String gameName) { this.a = a; this.titleId = titleId; this.gameName = gameName; }

    /** GameSheet's "Mods" row */
    public static void open(Activity a, String titleId, String gameName) {
        ModsSheet s = new ModsSheet(a, titleId, gameName);
        current = s;
        s.show();
    }

    private void show() {
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        render();
        dialog = PUi.sheet(a, "Mods", box).setNegativeButton("Close", null).create();
        dialog.setOnDismissListener(d -> { if (current == this) current = null; });
        dialog.show();
    }

    private File modsRoot() { return ModRuntime.modsRoot(a); }

    private void render() {
        box.removeAllViews();
        List<ModStore.Mod> mods = ModRuntime.mods(a, titleId);
        PUi.add(box, PUi.body(a, "Local romfs/cheat edits only, never downloaded or cataloged by this app. Order is priority: the first enabled mod "
                + "to have a file wins when two mods both touch it."), 0);
        if (mods.isEmpty()) {
            PUi.add(box, PUi.small(a, "No mods imported for this game yet."), 14);
        } else {
            LinearLayout card = PUi.add(box, PUi.listCard(a), 14);
            for (int i = 0; i < mods.size(); i++) {
                if (i > 0) card.addView(PUi.divider(a));
                card.addView(row(mods.get(i), i == 0, i == mods.size() - 1));
            }
            if (ModStore.needsBuild(mods)) {
                PUi.add(box, PUi.warnChip(a, "Needs a build with mods", "Needs a build with mods",
                        "One or more enabled mods carry exefs code (not just romfs/cheats). Their romfs and cheats apply right away; the code part "
                                + "only runs after a build that includes mods, which is not available from this sheet yet."), 12);
            }
        }
        if (busy) PUi.add(box, PUi.small(a, busyText), 14);
        PUi.add(box, PUi.button(a, "Add mod…", PUi.Kind.PRIMARY, v -> addMod()), 16, PUi.wrap());
    }

    private LinearLayout row(ModStore.Mod m, boolean first, boolean last) {
        LinearLayout r = PUi.row(a);
        r.setPadding(PUi.dp(a, 10), PUi.dp(a, 10), PUi.dp(a, 10), PUi.dp(a, 10));
        TextView tile = PUi.text(a, "🧩", 18, dev.nxnative.android.R.color.bn_text, 400);
        tile.setGravity(android.view.Gravity.CENTER);
        tile.setBackground(PUi.round(PUi.col(a, dev.nxnative.android.R.color.bn_tint), PUi.dp(a, 12), 0, 0));
        r.addView(tile, new LinearLayout.LayoutParams(PUi.dp(a, 40), PUi.dp(a, 40)));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(PUi.dp(a, 12), 0, PUi.dp(a, 8), 0);
        TextView name = PUi.text(a, m.name, 16, dev.nxnative.android.R.color.bn_text, 600);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(name);
        String kindWord = m.hasCode() ? "Code" : "Files";
        col.addView(PUi.small(a, kindWord + " · " + size(m.sizeBytes()) + (m.enabled ? "" : " · off")));
        LinearLayout.LayoutParams clp = PUi.weight();
        r.addView(col, clp);
        android.widget.Switch sw = new android.widget.Switch(a);
        sw.setChecked(m.enabled);
        sw.setOnCheckedChangeListener((btn, on) -> setEnabled(m, on));
        r.addView(sw);
        LinearLayout.LayoutParams gap = PUi.wrap(); gap.leftMargin = PUi.dp(a, 6);
        r.addView(glyphButton("▲", "Move " + m.name + " up", !first, v -> move(m, true)), gap);
        r.addView(glyphButton("▼", "Move " + m.name + " down", !last, v -> move(m, false)), gap);
        r.addView(glyphButton("🗑", "Remove " + m.name, true, v -> confirmRemove(m)), gap);
        return r;
    }

    /** A small borderless button drawn from one glyph (no res/drawable icon exists for up/down yet; adding one is a one-line follow-up, not worth a
     *  new vector asset for this sheet alone) — a plain clickable TextView, same touch target convention as PUi.iconButton (44 dp), disabled (first
     *  row's "up", last row's "down") the same way PUi's own buttons fade when disabled */
    private TextView glyphButton(String glyph, String description, boolean enabled, android.view.View.OnClickListener l) {
        TextView t = PUi.text(a, glyph, 16, dev.nxnative.android.R.color.bn_text, 700);
        t.setGravity(android.view.Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(PUi.dp(a, 36), PUi.dp(a, 36)));
        t.setContentDescription(description);
        t.setClickable(enabled);
        t.setFocusable(enabled);
        t.setAlpha(enabled ? 1f : 0.3f);
        if (enabled) t.setOnClickListener(l);
        return t;
    }

    private static String size(long bytes) {
        return bytes >= 1 << 20 ? String.format(Locale.US, "%.1f MB", bytes / 1048576.0) : String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    private void setEnabled(ModStore.Mod m, boolean on) {
        busy(true, "");
        new Thread(() -> {
            try { ModStore.setEnabled(modsRoot(), titleId, m.dir.getName(), on); } catch (java.io.IOException e) { /* the row re-renders from disk either way */ }
            a.runOnUiThread(() -> { busy(false, ""); render(); });
        }, "peanuts-mod-toggle").start();
    }

    private void move(ModStore.Mod m, boolean up) {
        new Thread(() -> {
            try { if (up) ModStore.moveUp(modsRoot(), titleId, m.dir.getName()); else ModStore.moveDown(modsRoot(), titleId, m.dir.getName()); }
            catch (java.io.IOException e) { /* re-render shows whatever is actually on disk */ }
            a.runOnUiThread(this::render);
        }, "peanuts-mod-move").start();
    }

    private void confirmRemove(ModStore.Mod m) {
        PUi.confirmDanger(a, "Remove this mod?", m.name + " and its files are deleted from this device. The game is not changed otherwise.", "Remove", () -> {
            new Thread(() -> {
                try { ModStore.remove(modsRoot(), titleId, m.dir.getName()); } catch (java.io.IOException e) { /* best effort */ }
                a.runOnUiThread(() -> { Toast.makeText(a, "Removed " + m.name, Toast.LENGTH_SHORT).show(); render(); });
            }, "peanuts-mod-remove").start();
        });
    }

    private void addMod() {
        ModImportUi.pick(a, titleId,
                () -> ModImportUi.pickFolder(a, dev.peanuts.ui.LibrarySettings.REQ_MOD_FOLDER),
                () -> ModImportUi.pickZip(a, dev.peanuts.ui.LibrarySettings.REQ_MOD_ZIP));
    }

    /** The folder picker's result (forwarded by {@code dev.peanuts.ui.LibrarySettings.result}) */
    public void pickedFolder(Uri tree) {
        busy(true, "Importing…");
        ModImportUi.importFolder(a, tree, modsRoot(), titleId, null,
                (doneB, total, name) -> busyProgress(doneB, total, name),
                (ok, message) -> { busy(false, ""); Toast.makeText(a, message, Toast.LENGTH_LONG).show(); render(); });
    }

    /** The zip picker's result */
    public void pickedZip(Uri doc, String displayNameHint) {
        busy(true, "Importing…");
        ModImportUi.importZip(a, doc, modsRoot(), titleId, displayNameHint,
                (doneB, total, name) -> busyProgress(doneB, total, name),
                (ok, message) -> { busy(false, ""); Toast.makeText(a, message, Toast.LENGTH_LONG).show(); render(); });
    }

    private void busyProgress(long done, long total, String name) {
        busyText = total > 0 ? "Importing… " + (done * 100 / Math.max(1, total)) + "% (" + name + ")" : "Importing… " + name;
        if (dialog != null && dialog.isShowing()) render();
    }

    private void busy(boolean on, String text) { busy = on; busyText = text; if (dialog != null && dialog.isShowing()) render(); }
}
