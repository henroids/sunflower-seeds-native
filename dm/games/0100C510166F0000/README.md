# Chained Echoes (`0100C510166F0000`)

Update v1310720 over the base game, with the Ashes of Elrant DLC (`0100C510166F1001`), SDK 14.3.0. **Unity 2020.3.36f1** (IL2CPP), rendering with **NVN**. Modules `main`, `subsdk0` (NintendoSDK_movie) and `sdk`.

This profile was written from a survey of the game files, with its memory keys taken from the other Unity 2020.3 profiles, not measured. With it the game was packed on Linux. It has not been booted anywhere.

## What was found

- **Modules.** `main` `17b6a110…`, `subsdk0` `c781dd63…`, `sdk` `2aef2cff…`. The rewriter takes all three.
- **NPDM.** 64-bit, 16 MB of system resource, so the game has virtual address memory. `main` also imports `nn::os::SetMemoryHeapSize`.
- **Graphics.** `main` imports `nvnBootstrapLoader` and nothing of EGL, OpenGL, Vulkan or GLSLC.
- **Shaders.** 3,870 after duplicates: 1,896 vertex, 1,900 pixel, 73 compute and 1 geometry shader. They are Unity Shader objects: 2,899 keys first appear in `Data/globalgamemanagers.assets`, 546 in `Data/Resources/` (the engine's built-ins), 130 in `Data/resources.assets`, about 280 in the Addressables bundles (`Data/StreamingAssets/aa/Switch/*.bundle`), and 11 GLSLC outputs are in `main` (Unity's player's own). `shader_pairs.tsv` has 1,899 vertex / pixel pairs, `shader_facts.tsv` the 73 compute shaders' thread groups.
- **Movies.** No movie file in the RomFS. `main` takes 25 functions from `subsdk0` (`movie::Extractor`, `movie::Decoder`: Unity's video player), none of which the host supplies itself, so the module is loaded.
- **Network.** `nn::socket` (24 imports), `nn::nifm`, `nn::ssl`, `nn::friends` (67) and `nn::ec` (63, the shop) are imported, as by every Unity player; the game has no online mode.
- **Sound.** Unity's own audio: two `.resource` files of FSB5 banks (46 MB) and the music in bundles.
- **RomFS.** 661 files, 0.88 GB: 637 UnityFS bundles in `Data/StreamingAssets/aa/Switch` (0.81 GB; the largest, 105 MB, is the DLC's music), the player's files in `Data/`, `Data/Managed/Metadata/global-metadata.dat`. The DLC's own RomFS has two small files; its content is in the update.

## What is guessed

| Setting | Why it is a guess |
|---|---|
| 5 GB block: `mem_reserve 0x140000000`, `mem_alias 0x118000000`, `mem_stack 0x10000000`, `sys_resource 0x1000000` | `profile_guess.py`'s rule for a game with a system resource. Not measured |
| `app_pool 0x40000000` | the 1 GB of Patrick's Parabox and AI: THE SOMNIUM FILES - nirvanA Initiative (Unity 2020.3.30f1, the closest profiles), in place of the rule's hardware pool, which needs a heap check that was not run. Advance Wars 1+2: Re-Boot Camp (Unity 2020.2) needed 2 GB |
| No `mem_used` | the heap request was not seen |
| `shader_groups /Resources/=engine /StreamingAssets/=assets /Data/=data` | Fire Emblem Engage's groups (Unity 2020.3 with Addressables). They only name the package files |
| `pad_layout full` | a 2D role-playing game; the 3D layout has the D-pad. No file decides it |
| `romfs_marker Data/globalgamemanagers`, `zip_store` | as the other Unity profiles; the rule for the file types |
| `build` row's display version `?` | the import keeps no control data, so the version's name was not read |

`bf_want.tsv` and `pso_manifest.tsv` are empty. No `patches.py`. No `depth_mode`: the NVN depth mode the game sets was not seen (Fire Emblem Engage sets `zero`).

## Open

- Not booted anywhere. The run-time keys (`render_cond`, `tex_flush_upload`, `tex_dirty_upload`, `core_preempt`, `present_interval`, …) keep their defaults.
- The memory layout: the alias region's use, the heap `main` asks for and whether 1 GB of pool is enough were not measured.
- The `sdk` build has no registered cut in `dm/tools/sdk_patches.py`; whether its function rules (the node area cut, the 2 MB alignment) matched this build was not read from the rewrite.
- The DLC: what `nn::aoc` must report for the game to open Ashes of Elrant was not checked.
- No readiness rows (`readiness.tsv`): the game is not marked as supported on Android.
