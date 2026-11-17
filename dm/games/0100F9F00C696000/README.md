# Crash Team Racing Nitro-Fueled (`0100F9F00C696000`)

<!-- PEANUTS: a Peanuts Native profile and note (Banana Native has none for this title). -->

Update 1.0.15 (v983040) on the base game. SDK 8.2.0. Vicarious Visions' Alchemy engine (build name Octane) with NVN graphics, Havok, FMOD and Bink 2. Modules `main`, `subsdk0` (the game's own GLSLC) and `sdk`.

**Status (current): packs on the PC, not run on a device yet.** The split finds 1,945 shaders and all of them translate to Vulkan. The profile is a provisional one: its memory layout is the survey's guess, and the game has not booted in this host. The shaders it compiles at run time are still missing, below.

## Builds

| Module | Build ID |
|---|---|
| `main` | `1c689518406930512c13ddf4217e7676` |
| `subsdk0` | `c8a3cc0b1d1858330a881c0c2fc87940` |
| `sdk` | `3ae34e689d7a35f13cbf42bfd672af8b` |

The companion of eden-duo-companions (package `CrashTeamRacingNitroFueled`) is for this build (`1C68951840693051`).

## What it needs

| Setting | Where | Why |
|---|---|---|
| `mem_reserve 0x100000000`, `mem_alias 0x10000000`, `mem_stack 0x10000000`, `app_pool 0xCD500000`, `sys_resource 0x0` | `game.tsv` | the survey's guess (`dm/games/profile_guess.py`): `main` sets a heap of at most the pool − 64 MB, and the NPDM declares no system resource. Not measured |
| `mem_used 0x7000000` | `game.tsv` | the modules take 107 MB (`main` 84.4, `sdk` 13.0, `subsdk0` 9.2), more than the default 64 MB the heap request is checked against |
| `modules main subsdk0 sdk` | `game.tsv` | `subsdk0` is GLSLC, which `main` calls at run time |

No rewriter cut and no host rule of its own.

## Files and shaders

- RomFS: 608 files, 9.66 GB: 401 `.pak` archives under `archives/` (igArchive version 13, 74,311 files in all), 163 ghost files, 17 Bink 2 movies.
- `shader_split.py` found 1,945 shaders: 1,603 pixel, 340 vertex, 2 geometry. 1,933 are whole GLSLC outputs in the 86 shader `.igz` files of `archives/shaders_nx.pak` (2,582 outputs, one stage each; the other 400 archives hold none), read by the igArchive reader ([shaders.md](../../../docs/reference/shaders.md#glslc-outputs-in-alchemys-igarchive-files)); 12 are in `main`. Each output is one stage, so the 340 vertex shaders are paired with the pixel input cover.
- The Vulkan library: 1,945 of 1,945 translate, 2,061 modules, every one valid for `spirv-val`; 7.8 MB.

## Shaders compiled at run time (open)

`main` holds 5 GLSL sources and imports `glslcCompile`, so some shaders exist in no game file. They are not in the tables: the split prints `[RED] main holds GLSL sources and imports glslcCompile, but there are no captured GLSLC outputs`. A profile of this game in a fork of nxnative reports 2 of them compiled, a vertex and a pixel shader, and only after the first button press on the title screen (nothing in 200 s without input). To add them: run the game with `DM_GLSLC_CAPTURE=<folder>` and press A on the title screen, then split again with that folder (`/glslc_capture/=runtime` goes into `shader_groups`).

## Audio (not yet run)

The game opens two audio renderers, one at 32 kHz at boot and one at 48 kHz as its intro starts. A host that drives one stops presenting a few seconds later (`[RED] audren: a second renderer`). The host now drives up to 4 ([audio.md](../../../docs/reference/audio.md#a-game-with-more-than-one-renderer)); not yet heard on this game.

## Frame rate

The game asks for present interval 2 (30 fps), as on the Switch. It is reported to step its races by the time that passed, so a present interval of 1 would give 60 fps at the right speed. Not tried here.

## Rewrite census

From the survey, before any rewrite:

- `main`: 8,620,835 instructions, 386 undecodable, 10,268 `x18` uses, 185 `svc` (all `#0x26`).
- `sdk`: 1,376,579 instructions, 273 undecodable, 11,228 `x18` uses, 61 `svc`, 56 `mrs tpidrro_el0`, 32 `mrs cntpct_el0`.
- `subsdk0`: 1,427,545 instructions, 7 undecodable, 2,882 `x18` uses.

## Open

- A first boot on a device, on the system driver and on Turnip.
- The run-time shaders, above, and the two audio renderers heard on a device.
- The memory keys are not measured.
- The companion on the second screen.
