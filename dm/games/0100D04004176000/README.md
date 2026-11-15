# Farming Simulator: Nintendo Switch Edition (`0100D04004176000`)

Update v65536 over the base game, SDK 1.6.0, the oldest SDK of any profile. **GIANTS Engine 7.0.0** (GIANTS Software's own; its source paths name `Q:/GIANTS/giants/src`), with LuaJIT 2.1.0-beta2 for the game scripts, rendering with **NVN** directly. Modules `main`, `subsdk0`, `subsdk1`, `subsdk2` and `sdk`.

This profile was written from a survey of the game files, with its memory keys guessed by rule, not measured. With it the game was packed on Linux, but **the pack has no shaders**: the shader split does not know the file the game keeps them in. It has not been booted anywhere, and with an empty shader library it cannot draw.

## What was found

- **Modules.** `main` `7d065117…`, `subsdk0` `0966d164…` (nn::swkbd), `subsdk1` `dfd767c4…` (NintendoSDK_NVN), `subsdk2` `77594029…` (nn::sf, nn::vi), `sdk` `96f688a9…`. On SDK 1.6.0 the SDK is split over these four modules; `main` takes 6 functions from `subsdk0`, 3 from `subsdk1` (`nvnBootstrapLoader`, `nv::InitializeGraphics`, `nv::SetGraphicsAllocator`) and 6 from `subsdk2` (nn::vi). The rewriter takes all five.
- **NPDM.** 64-bit, no system resource, so no virtual address memory. `main` imports `nn::os::SetMemoryHeapSize`.
- **Graphics.** `main` imports `nvnBootstrapLoader` and nothing of EGL, OpenGL, Vulkan or GLSLC. No nn::gfx.
- **Shaders.** None that the split reads: no BNSH file and no GLSLC output in `main` or in any RomFS file (every file was scanned). The game names one shader file, `shared/shader_switch.sharc` (19.4 MB), GIANTS' shader cache: magic `RAHS`, version 7, a 32-byte header (`7, 32, 85, 8115, 4078, 0x60000`), an index of 24-byte rows from `0x28` (a 16-byte hash and two 32-bit numbers), and from about `0x50000` 18.9 MB of data with the entropy of compressed or encrypted bytes (no zlib stream; the layout was not decoded). The 69 files in `data/shaders/*.xml` are the engine's shader descriptions (source, not what NVN is given). `main` calls `nvnProgramSetShaders` and holds a shader source generator (GLSL and Cg function texts), but no compiler is in the game.
- **Movies.** Three Ogg Theora files (`dataS2/videos/*.ogv`, 32 MB), decoded by the game's own Theora decoder. No movie library.
- **Network.** None: no socket, nifm or ssl import. `nn::account`, `nn::aoc` (2 functions), `nn::swkbd`, `nn::pctl`.
- **RomFS.** 7,381 files, 2.70 GB: `data/vehicles` 1.6 GB, `data/maps` 0.64 GB, `data/music` 0.17 GB. By type: `.dds` 1.3 GB (4,475 files), `.i3d.shapes` 1.1 GB, `.ogg` and `.wav` 0.25 GB, the scripts as `.l64` (LuaJIT bytecode, 503 files). No DLC was given.

## What is guessed

| Setting | Why it is a guess |
|---|---|
| 4 GB layout: `mem_reserve 0x100000000`, `mem_alias 0x10000000`, `mem_stack 0x10000000`, `app_pool 0xCD500000`, `sys_resource 0x0` | `profile_guess.py`'s rule for a game without a system resource that sets a heap. The heap the game asks for was not measured |
| No `mem_used` | the modules are far under the default 64 MB; the heap request was not seen |
| `modules main subsdk0 subsdk1 subsdk2 sdk` | the rule (every module `main` imports from). No other profile has NVN or nn::vi in a subsdk: whether the host's own functions answer before these modules' was not checked |
| `pad_layout full` | a 3D game with two sticks; no file decides it |
| `zip_store`, `romfs_marker` | the rule (big or compressed file types; the largest file, `data/maps/map02.i3d.shapes`) |
| `build` row's display version `?` | the import keeps no control data, so the version's name was not read |

`shader_map.tsv`, `shader_facts.tsv` and `shader_pairs.tsv` have no rows. `bf_want.tsv` and `pso_manifest.tsv` are empty. No `patches.py`.

## Open

- **The shaders.** `shader_split.py` needs a reader for the `RAHS` shader cache (`shared/shader_switch.sharc`): its index, how an entry is packed, and what an entry holds (most likely the NVN control and code sections of one program, as GLSLC writes them). Until then the pack is of no use.
- SDK 1.6.0: no profile is older than SDK 3.4.0. Whether the host's functions cover this SDK's nn::os, nn::fs, nn::hid, nn::audio and NVN (the API version, object sizes) was not checked. `dm/tools/sdk_patches.py` has no rule for this `sdk` build; its function patches concern virtual address memory, which this game does not have.
- Not booted anywhere. The run-time keys (`render_cond`, `tex_flush_upload`, `tex_dirty_upload`, `core_preempt`, `present_interval`, …) keep their defaults.
- `rtld` has 34 imports where newer games' has 19; not looked at.
- No readiness rows (`readiness.tsv`): the game is not marked as supported on Android.
