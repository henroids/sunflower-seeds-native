<h1 align="center">Sunflower Seeds Native</h1>

<p align="center">
  MLB The Show 26 (Nintendo Switch) running natively on an Android handheld, from your own dump.
</p>

<p align="center">
  <img alt="license" src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%2013%2B-green">
  <img alt="devices" src="https://img.shields.io/badge/tested%20on-AYN%20Thor%20%C2%B7%20Xperia%201%20V%20%C2%B7%20Retroid%20Pocket%20Duo-orange">
</p>

> [!NOTE]
> An unofficial, personal fork of [Peanuts Native](https://github.com/igawa6/peanuts-native), made to play one game:
> MLB The Show 26. Not affiliated with Nintendo, Sony Interactive Entertainment, MLB, Peanuts Native, Banana Native or
> nxnative. It contains no game code, firmware or keys. Almost all of the code changes were written by an AI coding
> assistant; see [AI use](#ai-use).

## What it is

Sunflower Seeds Native runs the Switch version of MLB The Show 26 on an Android handheld without an emulator. As in
Peanuts Native, the game's own ARM64 code is rewritten once to run directly on the phone's CPU, the system services it
calls are answered by the app, and its graphics (NVN) are replayed on Vulkan with its shaders translated ahead of time.

The fork adds the game's profile (`dm/games/0100AA7024094000`) and the host fixes the game needed. The main one, and the
reason for the project, was that players made in the game (create-a-player, Road to the Show) had black faces, black
skin and hair without colour. How that was tracked down and fixed is written up in detail in
**[docs/mlb-the-show-26-skin-and-hair.md](docs/mlb-the-show-26-skin-and-hair.md)**, for anyone porting a game that
draws into memory through one texture and reads it through another.

## Status

| | |
|---|---|
| Game | MLB The Show 26, title ID `0100AA7024094000`, **update 1.0.19 (v1245184) only** |
| Works | Boot, menus, exhibitions, Road to the Show (new and loaded careers), create-a-player with correct skin, faces, hair colour and beards (since 1.0.24) |
| Known issues | Some hair styles look wrong in the player editor's close-up; phones slow down as they heat up; the sound can stay off after a phone call comes in during a game; once, real players' skin was black in one session and right in the next (not reproduced). The full list is in the [game's note](dm/games/0100AA7024094000/README.md#open) |

### Tested devices

Frame rates on the field in an exhibition, with **Crowd control** on (below), from the logs of the app's builds of
2026-11-25 (official 1.0.34 and the matching test builds).

| Device | GPU driver | Docked (1080p) | Handheld (720p) | Notes |
|---|---|---|---|---|
| AYN Thor (Snapdragon 8 Gen 2) | Turnip Gen8 V39 | about 53 fps | about 59 fps (short run) | Steady over long sessions; does not throttle |
| Sony Xperia 1 V (Snapdragon 8 Gen 2) | Turnip Gen8 V39 | 60 at the start, about 43 after 15 minutes (51.6 on average over 19 minutes) | 54 at the start, about 44 after 30 minutes (48.3 on average over a 39-minute game) | Throttles as it heats up, in both modes |
| Retroid Pocket Duo (Adreno 722) | Turnip Gen8 V39 | not measured on V39 (about 34 fps on T30) | about 41 fps (first run, shaders compiling; 49 without the glitch fixes) | Clean with V39 and the settings the app sets for it (since 0.2.144). On Mr. Purple T30 (about 45 fps) streamed portraits showed blocks of noise. Its CPU is the limit, not the GPU |

Versions 0.2.N are [test builds](#test-builds); every fix named here is in the official app too.

With the crowd drawn, the field runs at about 34 fps on the Thor, 20 on the Xperia and 24 on the Retroid Pocket Duo
(builds before 1.0.34).

### Recommended setup

From the test runs of 2026-11-25 and 26 (every log is summed up in the [game's note](dm/games/0100AA7024094000/README.md#performance)):

| | AYN Thor | Sony Xperia 1 V | Retroid Pocket Duo |
|---|---|---|---|
| GPU driver | Turnip Gen8 V39 | Turnip Gen8 V39 | Turnip Gen8 V39 |
| Crowd control | On | On | On |
| Mode | Either; Docked holds about 53 fps | Either (both slow to about 44 fps as the phone heats up) | Handheld (Docked is about 34 fps) |
| Use fast cores | Either (no difference measured) | On (18 → 20.6 fps before Crowd control) | On |
| Everything else | Defaults | Defaults | Defaults |

The rest (Upscaler, Frame rate cap 60, Frame generation off) stays at the defaults on all three. **Use fast cores** is
in **Settings** › **Graphics** › **Speed**; Crowd control and Mode are on Home's scoreboard row.

On the Retroid Pocket Duo the app sets three driver options for this game by itself (nothing to turn on): no tiled
rendering (`TU_DEBUG=sysmem`, against the player skin flickering black; no fps cost) and, for the compiler, no preambles
and no uniform-buffer promotion (`IR3_SHADER_DEBUG=nopreamble,nouboopt`, against flickering menus and portraits, and
the spiking vertices that `nopreamble` alone brings). Other drivers ignore them. Drivers tried there: Turnip Gen8 V39 is
the only one clean with those options; Mr. Purple T30 (the recommendation before 2026-11-26, about 45 fps) draws blocks
of noise over the portraits the game makes as it plays; T23 and T26 do not start the game; Turnip Duo v5.1 flickered.

### What the tests found

- **What helped:** hiding the crowd (about a third of the field's draws; 34 → 52 fps on the Thor, 20 → 50 on the
  Xperia, 24 → 34 on the Duo docked); Handheld mode on the phones; the merge with upstream Peanuts Native (0.2.104).
- **What fixed glitches:** the larger image table (0.2.105) fixed the menu flicker and a texture error on the Duo; no
  tiled rendering on the Duo (0.2.121) fixed the skin flicker; Turnip Gen8 V39 with no preambles and no uniform-buffer
  promotion (0.2.144) fixed the Duo's noise blocks and its menu and portrait flicker.
- **What did not help** (tried and taken out): giving the fastest core to the game's main thread (the Thor got slower);
  full GPU barriers, no image compression (UBWC), full-precision shaders and no early depth (LRZ) on the Duo (none
  stopped the flicker); for the noise blocks on T30, holding video uploads, re-uploading unseen texture changes, copying
  GPU-made pictures through a buffer, one-lane subgroups, shuffles without workgroup memory and the shader cache off.
- **The limit now** is the game's own main thread on the CPU (about 16 ms a frame on the Thor, more on the Duo), not the
  GPU. Heat is the Xperia's limit.
- **Still open:** the sound can stay off after a phone call comes in during a game.

### Settings worth knowing

Home's scoreboard row is four buttons:

- **Crowd control**: on hides the crowd in the stands (the game still runs it and plays its sound). It is about a third
  of the field's draws, and the biggest single gain: on the Thor it took the field from about 34 to 52 fps. Also in the
  game's page › **Performance** › **Speed**.
- **Mode**: 720p (Handheld) or 1080p (Docked). Handheld draws a third fewer pixels: more frames and less heat on a phone.
- **Driver**: the GPU driver page (pick an installed driver, or install one).
- **App**: checks for an update.

Changes to Crowd control and Mode apply from the next start of the game.

## What you need

- An Android 13+ handheld with a Snapdragon (Adreno) GPU and Vulkan 1.3. Developed on the AYN Thor; also tested on a
  Sony Xperia 1 V and a Retroid Pocket Duo (8 GB of RAM). See [Tested devices](#tested-devices).
- **Your own** dump of MLB The Show 26 from your own Switch: the base game and update 1.0.19, as `.nsp` or `.xci` files.
- **Your own** `prod.keys` and firmware dump from the same console.
- A PC to prepare the game (Linux, or Windows with WSL; macOS also works), with a USB cable and `adb` to copy it to the
  handheld. Neither app prepares games on the phone: Rebuild, Import again and Update shaders are hidden, and a prepared
  game comes in with Import pack.

Nobody from this project will ask you for keys or game files, and none are linked from here. Do not share a `.nup`,
a pack or anything else made from your game.

## Install

### 1. Install the app

Download the newest `sunflower-seeds-<version>.apk` from the
[Releases](https://github.com/henroids/sunflower-seeds-native/releases) page (tags `sunflower-v1.0.N`) and open it on
the handheld. Android asks to allow installs from your browser or file manager: **Settings** › **Apps** › your browser
› **Install unknown apps** › on.

After that the app checks for new releases itself and shows a page with the notes and an **Update** button.

### 2. Set up the app once

Open **Sunflower Seeds**. The first screen, **Set up your games**, has four steps:

1. **Keys**: pick your `prod.keys`.
2. **Game folder**: pick the folder the prepared game will go into (the default the PC builder uses is
   `/sdcard/Sunflower Roms`). When **Allow file access** appears, tap **Open settings**, turn on **All files access**, and
   go back.
3. **Updates & DLC**: skip; the update is inside the `.nup` (below).
4. **Firmware**: pick your firmware folder or `.zip`. The app takes the system fonts from it.

Then **Settings** › **GPU driver**: install and pick the driver for your device from [Tested devices](#tested-devices)
(Turnip Gen8 V39 on all three). Run **Settings** › **Readiness check**
afterwards.

### 3. Make a `.nup` from your NSP or XCI files

The app reads a game as a **`.nup`**: one file that holds the game's files already decrypted, with the base game and
its update together. You make it on the PC, from your `.nsp` or `.xci` files, with the importer in this repository.
Nothing is uploaded anywhere.

Combining the base game and the update **is part of this step**: give the importer both files and the `.nup` holds
both, the update's files over the base's. You do not need a separate merge first. If you already merged the base and
the update into one `.nsp` or `.xci` with another tool, give that one file instead; the importer reads every title in
a file. Either way the merge happens before (or as part of) the conversion, never after.

On Linux or WSL, from a clone of this repository:

```sh
sudo apt install cmake gcc zlib1g-dev adb          # once (Debian/Ubuntu names)
cmake -S dm/peanuts/import -B peanuts/.build/nximport -DCMAKE_BUILD_TYPE=Release
cmake --build peanuts/.build/nximport --target nximport_cli

# check what is in your files: you should see "kind base" and "kind update  version 1245184"
peanuts/.build/nximport/nximport_cli --keys ~/prod.keys probe "MLB26.nsp" "MLB26-update.nsp"

# base + update -> one .nup (the order of the files does not matter)
peanuts/.build/nximport/nximport_cli --keys ~/prod.keys nup \
  "MLB The Show 26 [0100AA7024094000][v1245184].nup" 0100AA7024094000 \
  "MLB26.nsp" "MLB26-update.nsp"

# optional: check the .nup; it ends in "ok, <files>, <size> GiB"
peanuts/.build/nximport/nximport_cli verify "MLB The Show 26 [0100AA7024094000][v1245184].nup"
```

The `.nup` is about the size of the game plus its update. You only make it once; keep it for later builds.

### 4. Prepare the game and copy it to the handheld

The PC builder rewrites the game's code, translates its shaders, and with `--push` copies the `.nup`, the pack and the
icon to the handheld over USB. Step 3 is done for you if you give it the `.nsp` / `.xci` files instead of a `.nup`
(it makes the `.nup` the same way, beside the first file). Set up its Python environment once as described in
[peanuts/docs/BUILDER.md](peanuts/docs/BUILDER.md#linux) (Python 3.14, Capstone, lz4, `zbic`, SPIRV-Tools and .NET 10),
turn on **USB debugging** on the handheld (**Settings** › **About phone** › tap **Build number** seven times, then
**Settings** › **System** › **Developer options** › **USB debugging**), connect it, and run:

```sh
PEANUTS_PYTHON=~/peanuts-env/bin/python peanuts/scripts/peanuts-build.sh \
  "MLB The Show 26 [0100AA7024094000][v1245184].nup" --keys ~/prod.keys --push
```

It takes a while (most of it is the shader translation). The game lands in
`/sdcard/Sunflower Roms/MLB The Show 26/` on the handheld. To use another folder, add `--dest "/sdcard/<folder>"` and pick
that folder as the **Game folder** in the app.

### 5. Play

In the app, **Settings** › **Library** › **Rescan**. MLB The Show 26 appears on Home as a card: **Play** starts it,
**Settings** (or **⋯**) opens its own page (picture, speed, saves, storage).

After an app update that changes the shader chain, the release notes say so: run step 4 again with the same `.nup`
(no need to repeat step 3).

## Test builds

The branch `mlb-the-show-26` builds a separate test app (`v0.2.N`) on every push, from the
[Actions](https://github.com/henroids/sunflower-seeds-native/actions) tab. It installs beside the official app and
has the diagnostic switches used in the write-up. `main` builds the official app, from test builds that were played
first.

To send a game run's log without a cable, **Settings** › **Help** › **Send logs to GitHub** takes a GitHub key that
can write to a log repository of your own (keep that repository private: logs name the device). The in-game panel's
**Stop and send the log** then sends it at once, and Home sends any finished run it has not sent yet.

## Documentation

- [docs/mlb-the-show-26-skin-and-hair.md](docs/mlb-the-show-26-skin-and-hair.md): the black skin and hair, and how
  they were fixed.
- [dm/games/0100AA7024094000/README.md](dm/games/0100AA7024094000/README.md): the game's note (what was found, what is
  open).
- [docs/reference/nvn-objects.md](docs/reference/nvn-objects.md): the general mechanisms, with the other games they
  apply to.
- [peanuts/docs/BUILDER.md](peanuts/docs/BUILDER.md) and [peanuts/docs/FAQ.md](peanuts/docs/FAQ.md): the PC builder and
  the app, from Peanuts Native.
- [docs/peanuts-native-README.md](docs/peanuts-native-README.md): the upstream README this fork started from.

## AI use

This project was made with heavy use of AI, and it is written down here so nobody has to guess:

- **Who did what.** The project's owner, [@henroids](https://github.com/henroids), chose the goal,
  directed the work, tested the builds on his AYN Thor, Sony Xperia 1 V and Retroid Pocket Duo, and sent back logs and screenshots. The code changes, the
  analysis (logs, the game's disassembly and shaders) and the documentation, this README included, were written by
  **Claude**, an AI model by Anthropic, used through Claude Code (mainly Claude Opus and Claude Fable models). Commits
  it wrote carry a `Co-Authored-By: Claude …` line.
- **Review.** Changes were checked by building them, by the repository's tests, and by playing the game on the device.
  They were not reviewed line by line by an experienced human C or graphics programmer.
- **Mistakes.** The AI shipped several fixes based on wrong guesses before finding the real causes; they are listed
  honestly in the [write-up](docs/mlb-the-show-26-skin-and-hair.md#the-wrong-turns) and were removed again.
- **Upstream.** Peanuts Native, Banana Native and nxnative are separate projects with their own authors; their own
  READMEs say how they were made (Banana Native's, for example, also says most of its code was written with Claude).

If you use code from here, treat it as you would any untested contribution: read it, and test it.

## Credits and license

- [Peanuts Native](https://github.com/igawa6/peanuts-native) by igawa6 and its contributors: the app, the library, the
  importer and the PC builder this fork uses (builder by intermission88).
- [Banana Native](https://git.eden-emu.dev/moceure/BananaNative) by moceure: running Switch games natively on Android.
- [nxnative](https://github.com/seedds/nxnative) by seedds (MIT): the base of it all.
- [nx2android](https://github.com/shirai91/nx2android) (MIT): fixes adapted upstream.

Licensed GPL-3.0-or-later, like Peanuts Native ([LICENSE](LICENSE)). Some components carry their own licenses; see
`android/NOTICE.md` and `dm/third_party/README.md`. MLB The Show is a trademark of its owners; it is named here only to
say which game this is for.
