package dev.peanuts.mods;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * DESIGN-MODS.md's "Mods" section, the shared contract between the per-game Mods UI (play-time: romfs overlay + cheats, this package) and the build-time
 * code-mod pipeline (IPS/exefs, a different agent's work under {@code dev/peanuts/build/} and {@code dm/peanuts/}). A mod lives at
 * {@code <app files>/mods/<TITLEID>/<modName>/}, with any of {@code romfs/}, {@code exefs/}, {@code exefs_patches/**}, {@code cheats/*.txt} and a
 * {@code mod.json} describing it: {@code {"name":..., "version":..., "enabled":bool, "order":int, "kind":"files"|"code"}}. "files": only romfs and/or
 * cheats, applies at play time on the normal build (no rebuild). "code": the mod also ships exefs or exefs_patches, which needs a modded build (the other
 * agent's path) — the files/cheats part of a "code" mod still applies at play time on the normal build; only the code part waits for a build.
 * <p>
 * This class is plain Java (no {@code android.*}, no {@code org.json}) so it can be exercised by a JVM-only test the same way
 * {@code dev.peanuts.build.RuntimeShaderMap} is ({@code peanuts/tests/test_mod_store.py}): javac it alone, run a small driver against a real temp
 * directory. The hand-rolled JSON in {@link #readJson}/{@link #writeJson} covers exactly the five flat fields {@code mod.json} ever has — never a
 * general-purpose parser, and never used for anything else in this package.
 */
public final class ModStore {
    private ModStore() {}

    public static final String KIND_FILES = "files";
    public static final String KIND_CODE = "code";

    /** One installed mod: its manifest fields plus the folder it lives in (so a caller can look at romfs/exefs/cheats directly) */
    public static final class Mod {
        public final File dir;      // <app files>/mods/<TITLEID>/<modName>/
        public String name;
        public String version;
        public boolean enabled;
        public int order;           // lower = applied/checked first = wins first (DM_ROMFS_OVERLAY's first-match-wins order)
        public String kind;         // KIND_FILES or KIND_CODE

        Mod(File dir) { this.dir = dir; }

        public File romfs() { return new File(dir, "romfs"); }
        public File exefs() { return new File(dir, "exefs"); }
        public File exefsPatches() { return new File(dir, "exefs_patches"); }
        public File cheats() { return new File(dir, "cheats"); }

        public boolean hasRomfs() { return romfs().isDirectory() && romfs().list() != null && romfs().list().length > 0; }
        public boolean hasCheats() { return cheats().isDirectory() && cheats().list() != null && cheats().list().length > 0; }
        public boolean hasCode() { return KIND_CODE.equals(kind); }

        // PEANUTS: build-side helpers (dev.peanuts.build.ModBuild) added alongside the UI/play-time half above; additive only, nothing above this
        // comment is changed by them. A "code" mod's exefs/ (whole replacement or added modules) and exefs_patches/<name>/<build id>.ips(32) (byte
        // patches, dm/tools/patch_ips.py) are independent: a mod may ship either, or both.
        public boolean hasExefs() { return exefs().isDirectory() && exefs().list() != null && exefs().list().length > 0; }
        public boolean hasExefsPatches() { return exefsPatches().isDirectory() && exefsPatches().list() != null && exefsPatches().list().length > 0; }

        /** A rough size on disk (romfs is usually the only big part; a mod folder with no romfs is cheap to walk) */
        public long sizeBytes() { return dirSize(dir); }
    }

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += dirSize(k);
        return total;
    }

    /** {@code <modsRoot>/<titleId>/} */
    public static File gameDir(File modsRoot, String titleId) { return new File(modsRoot, titleId.toUpperCase(Locale.ROOT)); }

    /** Every mod under this title, sorted by {@code order} (ties broken by folder name, so the list order is always stable and deterministic) */
    public static List<Mod> list(File modsRoot, String titleId) {
        List<Mod> out = new ArrayList<>();
        File dir = gameDir(modsRoot, titleId);
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) {
            if (!k.isDirectory()) continue;
            File manifest = new File(k, "mod.json");
            if (!manifest.isFile()) continue;
            Mod m = new Mod(k);
            try { readJson(manifest, m); } catch (IOException e) { continue; }   // a manifest that fails to parse is skipped, not a crash
            if (m.name == null || m.name.isEmpty()) m.name = k.getName();
            if (m.kind == null) m.kind = KIND_FILES;
            out.add(m);
        }
        out.sort(Comparator.comparingInt((Mod m) -> m.order).thenComparing(m -> m.dir.getName()));
        normalizeOrder(out);
        return out;
    }

    /** Renumbers 0..n-1 in list order, without touching anything on disk (the in-memory order {@link #list} always returns) */
    private static void normalizeOrder(List<Mod> mods) { for (int i = 0; i < mods.size(); i++) mods.get(i).order = i; }

    /** Writes one mod's manifest back (enabled/order changes; name/version/kind are set at import and not re-derived here) */
    public static void save(Mod m) throws IOException {
        File manifest = new File(m.dir, "mod.json");
        writeJson(manifest, m);
    }

    /** Enables/disables a mod by folder name and persists it; does not reorder */
    public static void setEnabled(File modsRoot, String titleId, String modFolderName, boolean enabled) throws IOException {
        for (Mod m : list(modsRoot, titleId)) if (m.dir.getName().equals(modFolderName)) { m.enabled = enabled; save(m); return; }
    }

    /** Moves a mod one place earlier (higher priority: wins first) in the enable-order list; a no-op at the top. Renumbers and saves every mod whose
     *  order actually changed (never the whole list unconditionally, so an import racing a reorder cannot clobber an unrelated mod's manifest) */
    public static void moveUp(File modsRoot, String titleId, String modFolderName) throws IOException { move(modsRoot, titleId, modFolderName, -1); }

    /** Moves a mod one place later (lower priority) */
    public static void moveDown(File modsRoot, String titleId, String modFolderName) throws IOException { move(modsRoot, titleId, modFolderName, 1); }

    private static void move(File modsRoot, String titleId, String modFolderName, int delta) throws IOException {
        List<Mod> mods = list(modsRoot, titleId);
        int i = -1;
        for (int j = 0; j < mods.size(); j++) if (mods.get(j).dir.getName().equals(modFolderName)) { i = j; break; }
        if (i < 0) return;
        int j = i + delta;
        if (j < 0 || j >= mods.size()) return;   // already at that end
        Collections.swap(mods, i, j);
        normalizeOrder(mods);
        save(mods.get(i));
        save(mods.get(j));
    }

    /** Deletes a mod's whole folder (the confirm dialog is the UI's job, not this method's) */
    public static void remove(File modsRoot, String titleId, String modFolderName) throws IOException {
        File dir = new File(gameDir(modsRoot, titleId), modFolderName);
        deleteTree(dir);
    }

    private static void deleteTree(File f) throws IOException {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        if (!f.delete() && f.exists()) throw new IOException("could not delete " + f);
    }

    /** True when at least one enabled mod is a code mod (its exefs/exefs_patches part needs a modded build, not play-time) */
    public static boolean needsBuild(List<Mod> mods) {
        for (Mod m : mods) if (m.enabled && m.hasCode()) return true;
        return false;
    }

    /** The enabled mods' romfs directories, in priority order (first wins — {@code DM_ROMFS_OVERLAY}'s own order), for every enabled mod that actually
     *  ships a romfs (a "code"-kind mod's files parts still apply this way; only its exefs half waits for a build) */
    public static List<File> enabledRomfsDirs(List<Mod> mods) {
        List<File> out = new ArrayList<>();
        for (Mod m : mods) if (m.enabled && m.hasRomfs()) out.add(m.romfs());
        return out;
    }

    /** The enabled mods' cheats directories, in priority order (first wins when two mods both ship a same-named {@code <build id>.txt}) */
    public static List<File> enabledCheatsDirs(List<Mod> mods) {
        List<File> out = new ArrayList<>();
        for (Mod m : mods) if (m.enabled && m.hasCheats()) out.add(m.cheats());
        return out;
    }

    /** The enabled "code" mods, in the same first-wins priority order as {@link #enabledRomfsDirs} (dev.peanuts.build.ModBuild: the order a later
     *  mod's exefs/exefs_patches apply over an earlier one's, same rule as the romfs overlay) */
    public static List<Mod> enabledCodeMods(List<Mod> mods) {
        List<Mod> out = new ArrayList<>();
        for (Mod m : mods) if (m.enabled && m.hasCode() && (m.hasExefs() || m.hasExefsPatches())) out.add(m);
        return out;
    }

    /** A short, stable id for a set of enabled code mods (their folder name, version and order, in the order they apply): the modded build's own
     *  variant tag, {@code <TITLEID>-mod-<this>} (dev.peanuts.build.ModBuild), so a different set of enabled code mods always gets its own build and
     *  the same set reuses one already made. Never a secret or a security boundary — just short and collision-resistant for a handful of mods */
    public static String codeModsHash(List<Mod> codeMods) {
        StringBuilder in = new StringBuilder();
        for (Mod m : codeMods) in.append(m.dir.getName()).append('\u0000').append(m.version == null ? "" : m.version).append('\u0000').append(m.order).append('\n');
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(in.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) hex.append(String.format(Locale.ROOT, "%02x", d[i] & 0xff));   // 12 hex chars: short, plenty for a handful of mods
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }   // SHA-256 is mandatory on every JVM and every Android API level this app targets
    }

    /** The modded build's variant id (a pack/library entry of its own, never overwriting titleId's normal build — DESIGN-MODS.md "a SEPARATE modded
     *  build"): {@code <TITLEID>-mod-<codeModsHash>}, or null when codeMods is empty (nothing to build: play the normal pack) */
    public static String variantId(String titleId, List<Mod> codeMods) {
        return codeMods.isEmpty() ? null : titleId.toUpperCase(Locale.ROOT) + "-mod-" + codeModsHash(codeMods);
    }

    /** A free, unused folder name under {@code gameDir} for a new mod called {@code wanted} ("Name", "Name (2)", "Name (3)", …) */
    public static String freeName(File modsRoot, String titleId, String wanted) {
        String base = sanitizeName(wanted);
        File dir = gameDir(modsRoot, titleId);
        String candidate = base;
        int n = 2;
        while (new File(dir, candidate).exists()) candidate = base + " (" + (n++) + ")";
        return candidate;
    }

    /** A folder-name-safe version of a mod's display name: no path separators, no leading/trailing dots or spaces, never empty */
    static String sanitizeName(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|]+", " ").trim();
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        return s.isEmpty() ? "Mod" : s;
    }

    // ---- mod.json: five flat fields, hand-rolled so this class stays javac-able with no classpath beyond the JDK ----

    private static void readJson(File f, Mod out) throws IOException {
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        out.name = jsonString(text, "name");
        out.version = jsonString(text, "version");
        String en = jsonRaw(text, "enabled");
        out.enabled = "true".equals(en);
        String ord = jsonRaw(text, "order");
        try { out.order = ord == null ? 0 : Integer.parseInt(ord.trim()); } catch (NumberFormatException e) { out.order = 0; }
        out.kind = jsonString(text, "kind");
    }

    private static void writeJson(File f, Mod m) throws IOException {
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"name\": ").append(jsonQuote(m.name)).append(",\n");
        b.append("  \"version\": ").append(jsonQuote(m.version == null ? "" : m.version)).append(",\n");
        b.append("  \"enabled\": ").append(m.enabled ? "true" : "false").append(",\n");
        b.append("  \"order\": ").append(m.order).append(",\n");
        b.append("  \"kind\": ").append(jsonQuote(m.kind == null ? KIND_FILES : m.kind)).append("\n");
        b.append("}\n");
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(f)) throw new IOException("could not write " + f);
    }

    /** A JSON string field's value, unescaped for the two sequences {@code mod.json} ever needs ({@code \"} and {@code \\}); null when the key is absent
     *  or its value is not a quoted string (so a malformed manifest degrades to nulls/defaults, never an exception that would hide the whole mod list) */
    private static String jsonString(String text, String key) {
        String raw = jsonRaw(text, key);
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '"') return null;
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < raw.length()) { i++; char n = raw.charAt(i); out.append(n == 'n' ? '\n' : n); }
            else out.append(c);
        }
        return out.toString();
    }

    /** The raw text after {@code "key":} up to (not including) the next top-level comma or closing brace; null when the key is not found at all */
    private static String jsonRaw(String text, String key) {
        String needle = "\"" + key + "\"";
        int k = text.indexOf(needle);
        if (k < 0) return null;
        int colon = text.indexOf(':', k + needle.length());
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        if (i < text.length() && text.charAt(i) == '"') {
            int j = i + 1;
            while (j < text.length() && text.charAt(j) != '"') { if (text.charAt(j) == '\\') j++; j++; }
            return text.substring(i, Math.min(j + 1, text.length()));
        }
        int j = i;
        while (j < text.length() && text.charAt(j) != ',' && text.charAt(j) != '}' && text.charAt(j) != '\n') j++;
        return text.substring(i, j).trim();
    }

    private static String jsonQuote(String s) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\');
            if (c == '\n') { b.append("\\n"); continue; }
            b.append(c);
        }
        return b.append('"').toString();
    }
}
