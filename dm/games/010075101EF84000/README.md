# Stray

Base game (`v0`, 1.0.0), SDK 17.5.6. BlueTwelve Studio's **Unreal Engine 4** game on **NVN**, with Bink 2 (`subsdk0`, `NintendoSDK_movie`). Modules `main`, `subsdk0` and `sdk`. No game code is cut and no host rule is added; `ue_malloc.py` and `ue_region.py` handle Unreal Engine 4's allocator and virtual address region automatically.

## Android bring-up (`2026-11-22`)

Brought up on a POCO F7 (`Snapdragon 8s Gen 4`, Adreno 825, Android 16):

- **Driver:** Mesa Turnip (`26.3.0-T30-1.4.359`, `PurpleVK 26.3.0-devel`, `git-62ac221a33`) and Qualcomm system driver (`Vulkan 1.3.295`), both reaching in-game play.
- **Shaders:** 10,748 shaders extracted from the Oodle-compressed base `.pak` (`shader_split.py` via `DM_OODLE_OOZ`: `676` compute, `3` geometry, `7,752` pixel, `2,317` vertex; `0` missing in runtime logs across 14,700+ frames).
- **Result:** Boots cleanly through logos and title screen into gameplay at 30 fps (`~33.4 ms` frame time, `~15.5-16.5 ms` GPU, `~2.2-2.6 GB` resident) with audio and controls working.
- **Open items:** In the opening thunderstorm alcove before the storm clears, dark unlit surfaces appear mostly black until illuminated by lightning flashes; full daylight gameplay immediately following renders normally. Not tested on iOS or macOS; later updates beyond `v0` are not registered.
