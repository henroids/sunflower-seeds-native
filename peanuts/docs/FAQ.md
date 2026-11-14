# Peanuts Native FAQ

For version 0.1.1. Labels in **bold** are the words the app shows.

## Quick answers

- **Build is off, or a game stops at startup:** add your keys and firmware fonts in Settings › Library (**Keys**, **Firmware fonts**), then build or play again.
- **Black screen, wrong picture or a crash at start:** pick another GPU driver in Settings › **GPU driver**, then run Settings › **Readiness check**.
- **The companion is empty or keeps waiting after an app update:** hold the game's cover, open **Storage**, tap **Build again**, then **Rebuild**.

## Basics

### What is Peanuts Native, and is it an emulator?

It runs your own Nintendo Switch game dumps on an Android handheld. When you add a game, the app builds it once on the device: it rewrites the game's code for the device's CPU and translates its shaders. After that the game runs directly.

It is not an emulator in the usual sense: it does not emulate the console's hardware while you play. The price is the build step, which takes minutes per game, and the fact that each game needs a profile inside the app.

### Which devices does it run on?

It is developed and tested on the AYN Thor (dual screen, Snapdragon 8 Gen 2, 16 GB). The stated needs are Android 13 or later, a 64-bit ARM device and a Snapdragon (Adreno) GPU with a Vulkan 1.3 driver. Other devices are untested. On a single-screen device the game still runs, without the companion.

### How much RAM do I need?

The documented requirement is 12 GB or more. On 8 GB devices, building large games crashed before 0.1.1. Since 0.1.1 the build uses fewer workers on devices with less memory, so it is slower but should finish. Playing on 8 GB is untested and may not work for every game.

### How much storage do I need?

Plan for the size of the game plus a few GB. Before a build, the game's sheet shows **Needs about … free (a rough estimate)**, or a **Low disk space** warning. The estimate is rough, and a low-space warning does not block the build.

### What do I need from my own console?

- Your own game dumps as NSP, XCI, `.nup` files or dump folders, with their updates and DLC if you use them.
- Your own `prod.keys`.
- Your own firmware dump, as a folder or a `.zip`. The app extracts the fonts from it.

You dump your own games, keys and firmware from your own console. The app ships with none of them and does not link to any.

### When do I need the firmware fonts, and why does the app ask for them?

Before the first build. **Build** stays off and the sheet shows **Setup needed** until both keys and firmware fonts are added. Some games also need the fonts to start at all, for example Echoes of Wisdom. If such a game was prepared without them, Play shows **Firmware fonts needed** instead of crashing. Tap **Choose firmware…** there and pick the folder or `.zip` of the firmware you dumped from your own console. **Play anyway** starts the game without them.

### Where do my files go?

- Your dumps stay where you put them, in the game folder you picked.
- A build goes beside the game by default. Settings › Library › **Build folder** moves all builds to one folder, with one folder per game.
- Keys and extracted fonts are kept inside the app.
- Saves are in the app's own storage. The game's **Saves** tab shows the exact path.

The setup screen says it plainly: keys, firmware and folders stay on this device, and nothing is uploaded.

### Is it legal to use with my own dumps?

The app runs only dumps you made from your own console, and it contains no game code, firmware or keys. Whether dumping is allowed depends on where you live. This is not legal advice. Do not share built games or their shaders: both are made from your game's files.

### Can I play online?

Not in this version.

## Adding and building games

### How do I add and build my games?

1. Open the app. The first screen is **Set up your games**.
2. Add **Keys**, pick the **Game folder**, set **Updates & DLC** if those files are in other folders, and add **Firmware fonts**.
3. Tap **Show my games**.

Then tap a game's cover to open its sheet. Check the **Base**, **Update**, **DLC** and **Companion** rows and tap **Build**. The build imports the game, prepares it and installs the companion. Only one build runs at a time.

Later, change the folders in Settings › Library. **Rescan** reads them again.

### What do the labels on a cover mean?

| Label | Meaning |
|---|---|
| **Ready** | Built. Tap the cover to play. |
| **Build needed** | Found in your folder, not built yet. Tap to open its sheet. |
| **Building · N %** | A build is running. |
| **Build stopped** | The build failed or was stopped. Its card says why. |
| **Wrong version** | Your update is not one this app version supports. |
| **File gone** | The game file is no longer where the app found it. |
| **Not in this app version** | This app version has no profile for the game. |

The same names are the filters at the top of the library.

### Why does building take so long?

The app rewrites all of the game's code and translates every shader on the device. Most games take 5 to 40 minutes on the Thor. The size of the game, the number of shaders, the device's memory and its temperature all change the time. Echoes of Wisdom (9.6 GB, 9,858 shaders) took about 50 minutes on the Thor.

### Can I use the device while it builds?

Yes. The build goes on if you leave the app, and a notification shows its progress. You cannot build a second game at the same time. Some builds start the game once to capture shaders, so close any running game first.

### What happens if the build is interrupted or the device sleeps?

What is written so far stays. The game's card shows **Build stopped** with **Resume** or **Retry**, and the next build picks up what it can. If the screen going off stops the build on your device, turn on Settings › **Keep the screen on while preparing** and keep the device charging. A build can also pause by itself for heat or battery and then continue.

### It sits at the same percentage for a long time. Is it stuck?

Probably not. Large games can stay on one step for many minutes. Open the **Building** filter: the card shows the step and what it is working on. If the packer reports nothing for 10 minutes, the app stops the build itself and offers **Resume**.

### Can I build on a microSD card?

Yes. By default a build is written beside the game, so a game on the card is built on the card. To choose, pick a folder in Settings › Library › **Build folder**, on the device or on the microSD card. Hold that row to forget the folder.

### What is Build again, and when do I need it?

It remakes the game's `.nup` and pack from your files. Hold the game's cover, open the **Storage** tab, tap **Build again**, then **Rebuild** in the sheet. You need it:

- once after an app update, for a game whose companion uses map or helper features;
- when a message or a setting row asks for it;
- after you add a newer game update.

It is quick when the game's code is otherwise current, and long when it is not.

### Can I build on a PC or a Mac instead?

Yes. A script prepares the game on a computer and pushes it to the device over USB debugging. See [BUILDER.md](BUILDER.md). It is tested end to end on macOS. Linux is partly tested, and Windows is written but not yet tested. Games that make shaders while they run, for example Fire Emblem: Three Houses and Octopath Traveler 0, must be built on the device.

### How do updates and DLC work?

Put them in the game folder, or set Settings › Library › **Update folder** and **DLC folder**. They are matched to their games by title ID. The sheet shows the update it will use (**Choose another…** picks a different file) and the DLC it found (**Add…** picks more). The **Version** row shows **You have** and **This build supports**.

For an update that is not supported, the button reads **Build anyway** and asks first. The game may not start, may crash, or may draw parts wrongly, and the first build is slower. Your dump and saves are not changed.

### How do I free up storage?

- Settings › Library › **Free up space** deletes the original dump of a game that is already built. The game keeps playing from its built copy. Keep the original if you will add a later game update.
- Settings › **Storage** removes leftovers and old files. It never removes saves.

## Playing

### How do I start and stop a game, and which controls work?

Tap a **Ready** cover. To stop, open the in-game panel and choose **Stop the game**. The panel opens with a swipe in from the right edge, with Back, or on a controller with Home or by holding − and + for half a second.

Games take the handheld's own buttons and sticks, external controllers and on-screen touch controls. Settings has **On-screen controls** (Auto, On, Off), **Button mapping**, **Xbox button label swap** and **Rumble**. Each game can override them in its own settings.

### What frame rate do games run at?

Most supported games run at 60 fps on the Thor. Animal Crossing: New Horizons runs at 30, as on the Switch. A few games that are 30 on the Switch run at 60 here because the app changes the game's frame step, for example Breath of the Wild and Octopath Traveler 0. Some games lower their own rate in heavy scenes. The speed settings are **Frame rate cap**, **Hold 60 fps** and **Full game speed**.

### Is there frame generation or upscaling?

- **Frame generation** needs your own `Lossless.dll` from Lossless Scaling. It is never part of the app.
- **Upscaler** offers Nearest neighbor, Bilinear, FSR 1, Native and SGSR 1, with **FSR sharpness**.
- **Resolution** and **Dynamic resolution** appear for games that support them.

Some of these rows show only with Settings › **Tester settings** on.

### Where are a game's own settings, such as its language?

Hold its cover. The page has four tabs: **Performance**, **Game**, **Saves** and **Storage**. A game's settings override the defaults in Settings. **Reset to defaults** puts them back. The game's language is **Game** tab › **Game language**.

### Can I turn off the vibration and the animations in the app?

Yes. **Settings › Library** has two rows. **Haptics** is **System** or **Off**. **Motion** is **System** or **Reduced**. With **System**, the app also follows Android's own touch feedback and remove animations settings.

### Where are my saves, and can I import one?

Saves are in the app's own storage, under `saves/<title ID>`. The **Saves** tab shows the path and has **Back up saves**, **Restore saves** and **Import save** (a `.zip` or a save folder). Stop the game first. Before a restore or an import, the app backs up the current save.

### Which games are supported, and what does "Not in this app version" mean?

Every game needs a profile built into the app. Your app version has none for that game, so it cannot be built or played. Settings › **About** lists the games this version includes.

The project's README lists the tested games with the update each one needs. In 0.1.1 they are Link's Awakening, Echoes of Wisdom, Fire Emblem: Three Houses, Fire Emblem Engage, Metroid Dread, Super Mario Odyssey, Mario Kart 8 Deluxe, Animal Crossing: New Horizons, Persona 5 Royal, Castlevania: Belmont's Curse, Octopath Traveler 0 and Pokémon Brilliant Diamond. Settings › **About** may list more titles. Those have a profile but are not on the tested list.

## Graphics and performance

### Which GPU driver should I use, and how do I install one?

Graphics run on a Vulkan driver: **System (the phone's own driver)** or a custom one such as Turnip. If the system driver is older than Vulkan 1.3, as reported on Adreno 650 devices, you need a custom driver. If a game draws wrongly or crashes on one driver, try the other.

To install and pick one:

1. Settings › **GPU driver** › **Install…**
2. Choose **Download…** to list Turnip releases, or **Pick a zip…** for a driver zip you already have (a zip with a `meta.json`).
3. Pick the driver in the list. It takes effect at the next game start.

For one game only, use its **Performance** tab › **GPU driver**. **Remove picked…** deletes the app's copy of a driver.

### What do the Readiness check and the Graphics self-test tell me?

Settings › **Readiness check** checks Android, memory, file access, the GPU, the picked driver and each game's needs. Its verdict is **Ready**, **Ready, with limits** or **Not ready**, and a failed item has a button that opens the fix. Settings › **Graphics self-test** needs no game and ends in **PASS** or **FAIL (return code N)**. Since 0.1.1 it runs on the driver you picked. A FAIL on the system driver with a PASS on a custom one means: use the custom one.

### What does "% of draws skipped" mean?

The driver could not draw part of the picture. If the message says **Part of the picture is not drawn on this GPU driver**, try another driver. If it says **This GPU has no mesh shading**, another driver may not change it.

### The picture is wrong. What can I try?

- Another GPU driver.
- **Recommended** at the top of the game's **Performance** tab, if it shows.
- Another **Upscaler**. FSR 1 is too bright on Qualcomm's driver in some games.
- **Build again**, if the game's page or a message asks for it.

Flickering in Mario Kart 8 Deluxe is a known open report.

### Objects are black or missing for a few seconds on the first launch after an update. Is that a bug?

No. After an app update that changes how shaders are made, the GPU driver has to compile each game's drawing pipelines again. Until a pipeline is ready, the things that need it are not drawn, so they look black or missing and then appear. This happens once per game. The second launch is back to normal. If it still happens on every launch, report it with the log.

### The device gets hot and the frame rate drops. What now?

That is thermal throttling. Let the device cool down; a cooler helps. A lower **Frame rate cap** or **Resolution** reduces the load.

## Audio

### Is the audio crackle fixed, and what do I send if it is not?

0.1.1 changes the pacing of the audio renderer, which fixes crackle in games that use it. Octopath Traveler 0 also got a fix for its six-channel output. Whether every game is clean is not known yet.

If it still crackles, open an issue with the game and its version, where in the game it happens, your device, and the log of that run (**Share log**).

## Second screen companions

### What is a companion, and which games have one?

A page for one game on the second screen: a map, your party, items or quests, read live from the running game. It is installed per game from a catalog. A game without a companion leaves the second screen free for Android.

Settings › **Companions** shows the catalog: **Your games** first, then **Other companions · add the game first**. The catalog is read online, so the list can change without an app update. Every game on the tested list above has a companion.

### How do I install, update, remove or turn off a companion?

- At build time: the sheet's **Companion** row installs it after the build.
- Later: Settings › **Companions**, with **Install**, **Update** or **Remove** per game.
- **Versions…** on the game's sheet lists every release, to go back to an earlier one.

Build the game first, and close the game before you install. A new companion loads at the game's next start. Each download is checked against its published checksum.

To turn the second screen off for one game, hold its cover and open the **Game** tab. Its Companion section has **Second screen**: **Physical display** or **Off**.

### Why is the companion empty or waiting?

- After an app update, a companion that uses map or helper features needs **Build again** once.
- Your game update must be one the companion supports. Check the **Version** row in the game's sheet.
- A companion installed while the game runs loads at the next start.

### Does touch work on the second screen, and can a companion hide the game's own HUD?

Yes. Companion pages take taps and gestures, and some send button presses to the game. They give haptic feedback when you tap.

Some companions also hide parts of the game's HUD on the top screen and show them on the second screen instead. Octopath Traveler 0's does this for the party HUD. It is per companion and covers only the parts that companion handles. There is no app-wide switch for it yet.

## Mods

### What kinds of mods work?

Local mods only: `romfs` file replacements, cheats, `exefs` code and IPS patches (`exefs_patches`). The app downloads nothing and has no mod catalog.

### How do I add or remove a mod?

1. Open the game's sheet and tap **Mods**, then **Add mod…** For a built game the sheet is under hold the cover › **Storage** › **Build again**.
2. Pick the mod's **Folder** or **Zip file**. Atmosphère-layout folders and plain `romfs`/`exefs` folders are recognized.
3. Turn mods on or off and order them. The first enabled mod wins when two mods touch the same file.

To remove a mod, tap the bin beside it. Its files are deleted from the device. The game is not changed otherwise.

### What is a build with mods?

File mods and cheats apply without a build. A mod that carries code shows **Needs a build with mods**. Build the game normally first, then tap **Build with mods** in the Mods sheet. That makes a separate variant. The normal build is kept as it is. Start the variant with **Play with mods** on the game's sheet. If you change which code mods are on, tap **Rebuild with mods**.

## Troubleshooting

### The game crashes at start or shows a black screen. What should I check?

1. Settings › **Readiness check**.
2. The firmware fonts (Settings › Library › **Firmware fonts**).
3. Another GPU driver.
4. The game update: the sheet's **Version** row must show a supported one.
5. **Build again**, if the app was updated since the build.

After a crash the app shows the reason it knows, with a **Share log** button.

For a black screen, try another GPU driver first. On a device whose system driver is older than Vulkan 1.3, install a custom driver. Then run the **Graphics self-test**. If it passes and the game is still black, report it with the log.

### The build ran out of storage or memory. What now?

Out of storage: the card offers **Free up space** and **Retry**. Free space on the volume the build writes to, then retry. The build picks up what it can.

Out of memory, typically on an 8 GB device: update to 0.1.1, which builds with fewer workers on low-memory devices. A build that Android ended for memory resumes with fewer workers, so tap **Resume**. Close other apps before a large build. If it still fails, build the game on a PC or a Mac, or report it.

### How do I report a bug well?

Open an issue at https://github.com/igawa6/peanuts-native/issues with:

- what happened, and what you did just before;
- the device, its RAM and the Android version;
- the game, its title ID and its update version;
- the app version and the GPU driver;
- the Readiness check report (**Copy report**);
- the log: hold the game's cover › **Storage** › **Share log**, or Settings › **Share log** for the newest log of any game.

A screenshot or a short video helps for a wrong picture. Never attach or link game files, keys or firmware.

## Updating the app

### Do I need to rebuild my games after an update?

Usually not. Do **Build again** once for a game whose companion uses map or helper features, and whenever the game's page or a message asks for **Build again** or **Prepare again**. It is quick when the game's code is otherwise current.

### Will I lose my saves?

No. An update keeps the app's storage, and saves are in it. Uninstalling can delete them: Android asks whether to keep the app's data. Use **Back up saves** first.

### How do I tell which version I have?

The build name is under the app's title on the home screen and in Settings › **About**. A release build reads `peanuts-native-0.1.1`. Every log starts with it too.

## Credits

Peanuts Native is a fork of Banana Native by moceure, which is based on nxnative by seedds. Companions run on the Eden Duo runtime.
