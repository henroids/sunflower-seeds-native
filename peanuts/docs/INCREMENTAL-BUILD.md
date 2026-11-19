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

## The split: game files that did not change

The split scans every file of the profile's list (targeted) or of the RomFS (full) on every build; its `ScanCache` keeps results only to resume a stopped split,
keyed by the job's place and the container's time, and is deleted once the split is done. [`dm/peanuts/reuse/split_reuse.py`](../../dm/peanuts/reuse/split_reuse.py)
keeps, across builds, the result of every plain file job (`<PN_REUSE_DIR>/split/`): a file inside a `.nup`, whose table of contents has its SHA-256. A job's key:

- that SHA-256 and size;
- the names and SHA-256s of the files beside it whose name starts with its stem: what a plain file's reader opens besides it (`stem + '.dict'`,
  `stem + '.patch'`, each name a Next Level Games index lists);
- the job's direct-scan entry (its recorded containers, `DM_SPLIT_DIRECT`);
- a common part: the tools' and the profile's sources (`digest_sources`), Unity's common strings from main, the zstd dictionaries, `DM_SPLIT_SELECTED`, the split's
  environment and Python's version. Another common part (other tools, another main whose strings differ) starts the kept results over.

Before the scan the kept results go into the `ScanCache` and each kept file is a batch of its own, so only the others are scanned, in runs as before; after it the
new results are kept. A file whose result says it could not be read is never kept. The check: one kept file in 50 (at least one, once three could be kept),
picked by a hash of its key, is scanned anyway and its result must equal the kept one as values (sets and dicts in any order); one that differs scans every file
again without the kept results and deletes them. The split's own check after it stays: its tables against the profile's.

Archive ranges (several files, ranges planned over the whole archive), files without a hash (a plaintext NSP, a folder dump) and the Unity second pass are scanned
as before. Tests: `peanuts/tests/test_split_reuse.py` (`shader_split.py` run on a `.nup` made by `dm/tools/nup_pack.py`: a second split keeps every file but the
ones it checks and makes the same outputs byte for byte, a changed and a new file are scanned, kept results that differ are caught, `PN_REUSE=0`, the stem rule).

On ACNH the targeted, direct split reads only 93 files at their recorded containers: kept, they take 1 s in place of 9 s, and the split 50 s in place of 58 s.
The rest of it is listing the `.nup`'s 103,400 files (13 s) and reading and scanning main and planning the jobs (36 s), which run on every build.

## Not done yet

- The split's work before the scan (listing the files, main) on every build.
- Archive jobs in the split's cache: their key must cover every file they read.
- The import: Import again writes the whole `.nup` again (on an AYN Thor 11.9 GB in about 20 s: not worth it there).

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

| Run (`peanuts/scripts/incremental-check.py`) | Import | Rewrite | Split | Shaders | Pack | Whole build | Byte for byte as the full build |
|---|---|---|---|---|---|---|---|
| Full build | 25 s | 3 min 26 s | 56 s | 2 min 33 s (9,766 translated) | 6 s | 7 min 31 s | |
| Import again | 29 s | not run | 58 s | 19 s (9,564 kept, 202 translated to check them, 202 of 202 equal) | 6 s | 1 min 59 s | library and modules: yes |
| A build from the base and the update alone | 27 s | 3 min 28 s | 56 s | 2 min 31 s | 8 s | 7 min 35 s | |
| Its DLC added, Import again | 25 s | not run | 56 s | 21 s (9,564 kept, 202 checked) | 4 s | 1 min 52 s | library and modules: yes |

ACNH's DLC brings no shaders of its own that the phone's split finds (the split reads the update's RomFS, not an add-on's), so the build without it makes the
same 9,766. What is left of an Import again or an added DLC is the import and the split, which runs whole again: about three quarters of the 2 minutes.

## Checking it on a phone

`peanuts/scripts/incremental-check.py` runs the scenarios above on a phone through adb and the control interface (`peanuts/docs/CONTROL.md`) and compares the
outputs byte for byte: `full` (the reference), `again` (Import again), `dlc-later` (copies of the base and the update in the check's folder, a build from
them, then the DLC added and Import again). It drives only a development build installed beside the player's app (`-PnxAppId`, debuggable, the real packer:
`-PnxPackStage`), builds in `/sdcard/PeanutsCheck/<title ID>/`, keeps the app's settings on the computer while it runs and puts them back at the end (also after
an error), and deletes its folder unless `--keep`. It picks the phone once at the start (`--serial` when there are several). Exit status 0 when every
comparison held.

```sh
python3 peanuts/scripts/incremental-check.py --package dev.nxnative.android.<suffix> --title 01006F8002326000 --out report.json
```

## Tests

`peanuts/tests/test_shader_reuse.py`: a first build, a rebuild with the same inputs (only the sample translated), a profile that gains shaders, a changed fact
and a new pair (exactly those and the vertex shaders they pad are translated), pairs that change in the split's `pso_pairs.tsv` (translated, not kept by the same
inputs), an add-on's pixel shaders built after a build without them (they and the vertex shaders they pad are translated), the same inputs without a record (nothing kept unless the profile has its own `pso_pairs.tsv`), a change the rule does not see (caught by the check,
then a full translation), no record, `PN_REUSE=0`, a stop before the check, too few to check, a library of another version, the sample's classes; each build's
library equals a full build's. `peanuts/tests/test_rewrite_reuse.py`: a new version row keeps an unchanged `sdk`, a new main build rewrites `main`, patches for
the build (in the profile or in another profile's `patches.py`) and `MATCH_CUTS` of the module rewrite it, its bytes, its `patched.bin` and the `fps_decouple`
rows count, no record, another key, `PN_REUSE=0`.
