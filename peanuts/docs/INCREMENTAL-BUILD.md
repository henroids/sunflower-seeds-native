# A Build after an update: only what changed

Issue #120. A Build on the phone that comes after another one of the same game (an update of the game, an added DLC, an Import again, a Retry, an app update
whose profile gained a game version) keeps every shader of the pack's older library whose translation would come out the same, and translates only the others.
The shaders step is most of a Build's time (Animal Crossing: New Horizons on an AYN Thor, 2026-11-16: rewrite 4 min, split 51 s, shaders 2 min 40 s for 9,766
shaders, pack 8 s; a profile of 60,000 to 80,000 shaders takes 15 to 25 minutes there).

Upstream already keeps an older library's entries, but only with a manifest of the build in the game's profile (`vk_delta.tsv`, from a full Mac build:
[android/SHADERS.md, Delta updates](../../android/SHADERS.md#delta-updates)). 7 of 160 profiles have one, all of them Switch 2 titles.

## What is kept

The shaders step runs [`dm/peanuts/reuse/shader_reuse.py`](../../dm/peanuts/reuse/shader_reuse.py) with `vk_android_build.py`'s arguments. It looks at each
library in the pack's `shaders/` of this app's `DMSH_VK_CHAIN` and this game:

| The older library | Kept |
|---|---|
| built from the same profile inputs (`dmsh_chain.inputs`: the shader tables and the `nvn_*` / `shader_*` rows), the profile having its own `pso_pairs.tsv` | every shader it has: a result is a function of the tools, those inputs and the shader's key (`vk_android_build.py` keys its own result cache on exactly that) |
| built from other inputs (the app's profile gained a game version), or from the same ones by a profile without its own `pso_pairs.tsv`, with a record of each shader's part of them | each shader whose part is the same: its stage, its `shader_facts.tsv` row, its pairs (with the split's `pso_pairs.tsv` where the profile has none), its `dual_want` / `bf_want` membership, the `geo_mesh` and conflict rows that name it, the facts of the mesh programs drawn with it, the profile's `nvn_*` / `shader_*` rows; a vertex shader also its pixel shaders' parts (the parts the Mac's per-shader cache key takes, `dmsh_build.py shader_key`) |
| of another chain version, another game, or other inputs with no record | nothing |

The record is written when a library is made: `no_backup/reuse/<title ID>/shaders/<library id>.tsv`, one digest a shader. A pack made before this app has none,
so its first update keeps only what the same inputs allow; the next one has it.

Why the profile's own `pso_pairs.tsv`: a profile without one (most of them) pads its vertex shaders with the split's copy (`dmsh_build.py load_pairs`), which
the inputs digest does not cover; an added DLC can add pairs there while the profile stays the same. Such a profile keeps by the record, whose digests take the
pairs as `load_pairs` takes them (a review found this before it was published; `test_split_pairs_are_not_taken_for_the_same_inputs`).

Either way only a shader this build makes is kept (`vk_android_build.py`'s own selection: the profile's keys less the absences validated against the split,
`DMSH_VK_ABSENT_FILE`), and a vertex shader only when the pixel shaders paired with it that this build makes are those the older library holds. Its outputs
are padded with the inputs of the pixel shaders the build makes (`PS_INPUTS`), so an add-on installed since the last build (its pixel shaders absent then)
changes the vertex shaders it pairs with although the profile stays the same (`test_a_pixel_shader_that_comes_with_an_addon`).

How it is kept: the kept shaders and their entries' hashes go into a manifest of this build's version, game and inputs, in a copy of the profile folder
(`no_backup/reuse/<title ID>/profile/`, `DM_GAME` pointed at it), and `vk_android_build.py`'s own delta path keeps them byte for byte. No file of the Vulkan
chain changes, so the library version does not move.

## The check, and what happens when it fails

A sample of the shaders that could be kept is translated anyway: one in 50, at least 16, at most 1,000, picked by a hash of the key, and at least 4 of each
class (each stage, and vertex shaders with pixel shaders that pad them apart from those without), so a rule that misses one class is checked on it too. After the library is
written, every sample entry must be the older library's byte for byte. If one differs (the rule missed something for this game), or less than half of the
sample is in the library, the kept shaders are deleted and the tool runs again with the profile as it is: a full translation, which keeps what it translated
already. The log says `[RED] reuse check: …`. The record that led to it is deleted.

Other cases that lead to a translation: an older entry that cannot be read, fewer than 32 shaders to keep, a run that stopped after keeping and before its check
(the kept results are deleted at the next start: `.pn-reuse-unchecked` in the results folder), a failed step. The checks that were there stay: the library's
own check, the split against the profile, every module through spirv-val.

## Switches and the log

- `PN_REUSE=0` in the job's `ondevice/env.txt`: off, the shaders step is `vk_android_build.py` as before and the rewrite keeps no module whose key moved.
- A profile whose own manifest is of the build: upstream's delta update runs as it was.
- `shaders.log` lines: `reuse: <library>: N shaders kept (...), M translated to check them`, then `reuse check: M of M shaders translated to check are the
  older library's byte for byte; N shaders kept`. The card shows "Translating M new shaders (the other N are reused)" from the tool's own count.

## The rewrite: a module whose own part did not change

A module's key (`pack_android.py rewrite_key`) covers the profile's whole `patches.py` and every `build` / `fps_decouple` row, so an app whose profile gains a game
version rewrote `sdk` and `subsdk*` again although their build IDs had not changed. The rewriter reads of the profile only what is keyed by the module's build ID:
its build's tables in the profile's `patches.py` (`CUTS`, `OBS`, `FRAME_STEP`, `CAPABILITIES`, `VARIANT_CUTS`) and, for a build no patch file registers, the
profile's `MATCH_CUTS` of the module's name and the `build` rows' IDs of that module; for `main` also the `fps_decouple` rows.
[`dm/peanuts/reuse/rewrite_reuse.py`](../../dm/peanuts/reuse/rewrite_reuse.py) records, after each rewrite, that part (with the rewriter's files and the module's
bytes), the key and the SHA-256 of `patched.bin` (`no_backup/reuse/<title ID>/rewrite/<module>.tsv`). When the key moved later, the module is kept if its part
and `patched.bin` are the recorded ones: the current key is written (`<module>: kept, its build and its part of the profile are unchanged ...` in `rewrite.log`),
as upstream does for an equivalent rewriter (`rewriter_equiv`). The launcher's own key check then finds the current key. Whenever the profile's `patches.py` has
anything for the module's build, the whole file counts, as in the key. No record, another part, another `patched.bin`, a mod's cross-module layout: rewritten.
Tests: `peanuts/tests/test_rewrite_reuse.py`. On an AYN Thor (ACNH 3.0.3, 2026-11-19): after a full build, an app whose profile gained a
`build` row (another main, the same sdk and subsdk0) kept `subsdk0` and `sdk` (10.5 s and 20.5 s of the full build) and rewrote `main`, whose
own builds' rows changed; Prepare took 3 min 37 s. The kept `sdk.patched.bin` is byte for byte (SHA-256) the one a build from nothing makes with that
profile.

## Not done yet

- The split's scan: a cache by each RomFS file's SHA-256 (the `.nup`'s table of contents has one) would help plain files only, where the targeted split is
  quick already; the slow scans (archives, Unreal paks, Unity files with borrowed type trees) depend on several files.
- The import: Import again writes the whole `.nup` again (on an AYN Thor 11.9 GB in about 20 s: not worth it there).
- The targeted split of a profile made of several game versions (`shader_versions`) falls back to the full scan (its own task).

## Measured (AYN Thor, ACNH 3.0.3 with its DLC, 2026-11-18 and 19)

| Run | Rewrite step | Shaders step | Whole build |
|---|---|---|---|
| Full build | 3 min 26 s (main, subsdk0, sdk) | 2 min 34 s (9,766 translated) | 7 min 29 s |
| Import again | not run (the keys unchanged, as before) | 20 s (9,570 kept, 196 translated to check) | 2 min 15 s |
| A profile change (5 new pairs in the APK's tables) | not run (the keys unchanged) | 20 s (9,555 kept, 211 translated) | the split fell back to its full scan: 7 min 10 s |
| The same with `PN_REUSE=0` | | full | the library is the run above's byte for byte (SHA-256) |
| A profile change (a new `build` row: another main, the same sdk and subsdk0), Prepare | main rewritten (2 min 53 s), sdk and subsdk0 kept | not run (inputs unchanged) | 3 min 37 s |
| The same profile, a build from nothing | all three rewritten | | `sdk.patched.bin` is the kept one byte for byte (SHA-256) |

These ran on 0.1.4, before a review's changes (the profile's own `pso_pairs.tsv` for "same inputs", the sample by class, the paired pixel shaders a build
makes). The code as it is, on 0.1.5 (`DMSH_VK_CHAIN` `dfebef5d68a9`, the 0.1.5 release's translator), 2026-11-19:

| Run | Shaders step | Whole build |
|---|---|---|
| Full build | full (9,766 translated) | 7 min 31 s |
| Import again | 9,564 kept by the record (ACNH's profile has no `pso_pairs.tsv`), 202 translated to check them: 202 of 202 the older library's byte for byte | 2 min 13 s |

The library after Import again is the full build's byte for byte (SHA-256).

## Tests

`peanuts/tests/test_shader_reuse.py`: a first build, a rebuild with the same inputs (only the sample translated), a profile that gains shaders, a changed fact
and a new pair (exactly those and the vertex shaders they pad are translated), pairs that change in the split's `pso_pairs.tsv` (translated, not kept by the same
inputs), an add-on's pixel shaders built after a build without them (they and the vertex shaders they pad are translated), the same inputs without a record (nothing kept unless the profile has its own `pso_pairs.tsv`), a change the rule does not see (caught by the check,
then a full translation), no record, `PN_REUSE=0`, a stop before the check, too few to check, a library of another version, the sample's classes; each build's
library equals a full build's. `peanuts/tests/test_rewrite_reuse.py`: a new version row keeps an unchanged `sdk`, a new main build rewrites `main`, patches for
the build (in the profile or in another profile's `patches.py`) and `MATCH_CUTS` of the module rewrite it, its bytes, its `patched.bin` and the `fps_decouple`
rows count, no record, another key, `PN_REUSE=0`.
