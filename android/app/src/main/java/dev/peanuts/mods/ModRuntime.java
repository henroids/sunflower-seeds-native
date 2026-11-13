package dev.peanuts.mods;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Play-time wiring for {@link ModStore} (DESIGN-MODS.md "Launch hook"/"Cheats"): called from {@code GameActivity.start()}'s env assembly, once per
 * launch, to turn the enabled mods into the two env lines the host already understands — no new host mechanism, both already exist and are tested
 * (dm/host/tests/t_fs.c "base + overlay" for the overlay; dm/host/hle_cheats.c's own build-ID scan for cheats):
 * <ul>
 * <li>{@code DM_ROMFS_OVERLAY=<dir1>:<dir2>:...} — hle_fs.c:222-223 reads it once per {@code rom} mount; {@code rom_overlay_path} (hle_fs.c:375-388)
 *     checks the roots in that order and the <b>first one that has the file wins</b>; {@code dir_snapshot} (hle_fs.c:1289-1326) unions every root's
 *     directory listing so files the mods don't touch still show from the base RomFS. No copying: the directories are read live, so this is cheap
 *     even for a multi-GB romfs mod.</li>
 * <li>{@code DM_CHEATS=<folder>} — hle_cheats.c scans one flat folder (plus its {@code atmosphere/contents/<tid>/cheats} tree) for
 *     {@code <build id>.txt} by name; only one folder is ever read, so two enabled mods' cheats are staged (copied, not symlinked — shared storage
 *     refuses symlinks, the same FUSE limit {@code GameActivity}'s own DLC-link comment notes) into one folder under the app's own files, first
 *     enabled mod's file winning a same-named collision.</li>
 * </ul>
 * Only "files"-kind content (romfs, cheats) is ever wired here. A "code" mod's exefs/exefs_patches never reach the normal build's process at
 * all — {@link ModStore#needsBuild} is what the Mods sheet reads to show "Needs a build with mods" instead of pretending the code ran.
 */
public final class ModRuntime {
    private ModRuntime() {}

    public static File modsRoot(Context c) { return new File(c.getFilesDir(), "mods"); }

    /** The mods currently installed for this title, highest priority (wins first) first */
    public static List<ModStore.Mod> mods(Context c, String titleId) { return ModStore.list(modsRoot(c), titleId); }

    /**
     * The {@code DM_ROMFS_OVERLAY} line to add to the launch env, or null when there is nothing for this method to add (no enabled mod ships a romfs
     * and there is no scene overlay either — the caller's existing scene-overlay line, if any, is then left exactly as it was).
     * <p>
     * Order: enabled mods first, in their list order (list order IS priority — {@link ModStore#list}'s {@code order} field), then the 3D-scene
     * overlay last (lowest priority) when one is active, since a mod intentionally replacing a file should win over a scene/resolution overlay that
     * only exists to serve a higher-resolution variant of the same file. This line is added to the env list after the scene code's own
     * {@code DM_ROMFS_OVERLAY} line, so it wins (the host's env is built with {@code setenv(..., overwrite=1)}; the last line for a given key wins —
     * see the scene code's own comment, "a DM_ROMFS_OVERLAY line of the user's comes later and wins").
     */
    public static String romfsOverlayEnv(Context c, String titleId, File sceneOverlayDirOrNull) {
        List<ModStore.Mod> mods = mods(c, titleId);
        List<File> dirs = ModStore.enabledRomfsDirs(mods);
        if (dirs.isEmpty() && sceneOverlayDirOrNull == null) return null;
        StringBuilder b = new StringBuilder();
        for (File d : dirs) { if (b.length() > 0) b.append(':'); b.append(d.getPath()); }
        if (sceneOverlayDirOrNull != null) { if (b.length() > 0) b.append(':'); b.append(sceneOverlayDirOrNull.getPath()); }
        return "DM_ROMFS_OVERLAY=" + b;
    }

    /**
     * The {@code DM_CHEATS} line, staging the enabled mods' {@code cheats/*.txt} into {@code <files>/mods_stage/<titleId>/cheats} (first enabled
     * mod wins a same-named file); null when no enabled mod ships any cheats, leaving the host's own default ({@code <DM_ROOT>/cheats}) in force.
     * The staging folder is small (plain text cheat files), so this runs synchronously on the launch path, same as the DLC-link setup beside it.
     */
    public static String cheatsEnv(Context c, String titleId) {
        List<ModStore.Mod> mods = mods(c, titleId);
        List<File> dirs = ModStore.enabledCheatsDirs(mods);
        if (dirs.isEmpty()) return null;
        File stage = new File(new File(c.getFilesDir(), "mods_stage"), titleId.toUpperCase(java.util.Locale.ROOT) + "/cheats");
        try {
            deleteTree(stage);
            stage.mkdirs();
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (!f.isFile()) continue;
                    File dst = new File(stage, f.getName());
                    if (dst.exists()) continue;   // the first (highest-priority) enabled mod's file for this name wins
                    Files.copy(f.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            return null;   // staging failed: play with the host's own default cheats folder rather than fail the launch over cosmetic cheats
        }
        return "DM_CHEATS=" + stage.getPath();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** For the Mods sheet's "Needs a build with mods" line: true when an enabled mod carries exefs/exefs_patches, which the normal build never
     *  applies (only this title's play-time romfs/cheats do, from the same mod) */
    public static boolean needsBuild(Context c, String titleId) { return ModStore.needsBuild(mods(c, titleId)); }
}
