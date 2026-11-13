package dev.peanuts.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.peanuts.mods.ModStore;

/**
 * Plain-Java glue between {@link ModBuild}'s module-list plan and the on-device build (DESIGN-MODS.md "Code injection"/"ExeFS binary patch"): the
 * variant's own folder layout, the extra argv a modded "rewrite" step needs ({@code scripts/pack_android.py --modules/--mod-exefs/--mod-patches}),
 * and the exlaunch-style-hook warning (point 4 of the mods build task: a {@code subsdk9} added module's runtime hooks never take effect on this
 * host — main is read-only once rewritten and {@code svcSetProcessMemoryPermission} is not implemented). Kept free of {@code android.*} so it is
 * exercised on the JVM alone, the same pattern as {@link ModBuild} and {@code dev.peanuts.mods.ModStore} (javac/java tests, {@code
 * peanuts/tests/test_modded_build.py}). The actual job wiring (creating/resuming the variant's {@code Job}, starting {@code PackService}) is
 * {@code Prepare.runModded}, which this class's static methods feed.
 */
public final class ModdedBuild {
    private ModdedBuild() {}

    /** The variant's own work folder (never the normal build's &lt;dump&gt;/../&lt;id&gt;-work, so the two jobs never share one job.json): {@code
     *  <game home>/<variantId>-work}, gameHome being {@code dev.peanuts.mods.ModRuntime.home}'s return for this title. */
    public static File workDir(File gameHome, String variantId) { return new File(gameHome, variantId + "-work"); }

    /** The normal pack's shader library, reused as-is by the variant build (code mods never change shaders, DESIGN-MODS.md "Shaders are build-time
     *  only"): {@code <normalPack>/shaders}, the same relative path {@code pack_android.py}'s own {@code pack.tsv} "shaders" row records. */
    public static File normalShadersDir(File normalPack) { return new File(normalPack, "shaders"); }

    /**
     * The "modules" row of a pack's {@code pack.tsv} (its tab-separated {@code key\tvalue} rows, {@code pack_android.py}'s own writer): the title's
     * normal module list, in profile order, e.g. {@code [main, subsdk0, sdk]} for Pokemon Brilliant Diamond. Null when the pack has no modules row
     * (not a finished pack, or a pack.tsv this reader cannot find) - the caller then has nothing to plan a variant from and should refuse the build.
     */
    public static List<String> normalModules(File normalPack) {
        File f = new File(normalPack, "pack.tsv");
        if (!f.isFile()) return null;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                if (line.substring(0, tab).equals("modules")) {
                    String rest = line.substring(tab + 1).trim();
                    List<String> out = new ArrayList<>();
                    for (String m : rest.split(" ")) if (!m.isEmpty()) out.add(m);
                    return out;
                }
            }
        } catch (IOException e) { return null; }
        return null;
    }

    /**
     * The modded "rewrite" step's extra argv, one token per line (joined by the caller into {@code Job.modArgs}; {@code PackService}'s "rewrite"
     * case splits it back on "\n"). Empty when plan has nothing a mod supplies ({@code plan.source} empty): a plain replace-only mod still produces
     * {@code --modules}/{@code --mod-exefs} lines (its module stays in the same position, but its bytes come from the mod), while a mod that adds
     * no module and ships no exefs/exefs_patches at all (impossible per {@code ModStore.enabledCodeMods}'s own filter, but defensive here too)
     * yields nothing, i.e. a build that would do exactly what the normal pack already does.
     */
    public static List<String> rewriteArgv(ModBuild.Plan plan) {
        List<String> out = new ArrayList<>();
        if (plan == null || plan.refusal != null || plan.source == null || (plan.source.isEmpty() && plan.patchSets.isEmpty())) return out;
        out.add("--modules");
        out.add(String.join(",", plan.modules));
        for (String name : plan.modules) {
            ModStore.Mod mod = plan.source.get(name);
            File exefsFile = mod == null ? null : new File(mod.exefs(), name);
            if (exefsFile != null && exefsFile.isFile()) { out.add("--mod-exefs"); out.add(name + "=" + exefsFile.getPath()); }
            // every patch set is offered to every module: pack_android.py applies only the files named for that module's build ID (Plan.patchSets)
            for (File set : plan.patchSets) { out.add("--mod-patches"); out.add(name + "=" + set.getPath()); }
        }
        return out;
    }

    /** {@link #rewriteArgv}, joined one token per line, ready for {@code Job.modArgs}/{@code Upstream.jobModArgs} ("" when there is nothing to add) */
    public static String rewriteArgvJoined(ModBuild.Plan plan) { return String.join("\n", rewriteArgv(plan)); }

    /**
     * Point 4 of the mods build task: a warning when plan's winning module for {@code subsdk9} looks like an exlaunch-style hook module, whose
     * runtime hooks never take effect on this host (main is read-only once rewritten, {@code svcSetProcessMemoryPermission} is unimplemented) -
     * never a build refusal, just a clear sentence for the build log/UI. Detected by name {@code subsdk9} plus either: the profile did not already
     * have a {@code subsdk9} (an ADDED module, the shape exlaunch itself always takes - it is never a replacement), or the module's own bytes
     * contain an ASCII "exlaunch" marker (its own loader strings it almost always embeds). Null when {@code subsdk9} is not in the plan at all, or
     * neither signal fires (a plain replacement subsdk9 that is not exlaunch-shaped, e.g. a different SDK module entirely).
     */
    public static String exlaunchWarning(List<String> profileModules, ModBuild.Plan plan) {
        if (plan == null || plan.refusal != null || plan.source == null) return null;
        ModStore.Mod mod = plan.source.get("subsdk9");
        if (mod == null) return null;
        boolean added = profileModules == null || !profileModules.contains("subsdk9");
        boolean markerFound = containsExlaunchMarker(new File(mod.exefs(), "subsdk9"));
        if (!added && !markerFound) return null;
        return "subsdk9 (from " + mod.name + ") looks like an exlaunch-style hook module. Its runtime hooks will not take effect on this host: "
                + "the main module is read-only once rewritten, and svcSetProcessMemoryPermission (the SVC exlaunch's hooking depends on) is not "
                + "implemented. The module will load and run on its own, but whatever it meant to patch into main at run time will not happen.";
    }

    private static boolean containsExlaunchMarker(File f) {
        if (f == null || !f.isFile()) return false;
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            String s = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            return s.contains("exlaunch");
        } catch (IOException e) {
            return false;
        }
    }
}
