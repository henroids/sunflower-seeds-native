# PEANUTS hooks for the build pipeline, the Companion section and the Mods section

One-line additions to upstream files (PEANUTS.md rule 2).

## 1. GameSettings.java — the Companion section (one line)

File: `android/app/src/main/java/dev/nxnative/android/GameSettings.java`, in `static void dialog(MainActivity a, Game g)`, directly after the GPU timing row.

Old (the two lines the hook goes between; unchanged):

```java
        Ui.add(box, new Ui.Setting(a, "⏲️", "GPU timing", v -> gpuTiming(a, g, again)).value(gpuTsLabel(a, id)).chevron(a).view, 0);
        Ui.add(box, new Ui.Setting(a, "🌐", "Game language", v -> language(a, g, again)).value(GameLanguage.label(Prefs.gameLang(a, id), phoneTag())).chevron(a).view, 0);
```

New (one line inserted between them):

```java
        Ui.add(box, new Ui.Setting(a, "⏲️", "GPU timing", v -> gpuTiming(a, g, again)).value(gpuTsLabel(a, id)).chevron(a).view, 0);
        Ui.add(box, dev.peanuts.settings.CompanionSection.view(a, id, dev.peanuts.settings.CompanionSection.root(g.packDir)), 8);   // PEANUTS: the Companion section (install/update/remove, second screen)
        Ui.add(box, new Ui.Setting(a, "🌐", "Game language", v -> language(a, g, again)).value(GameLanguage.label(Prefs.gameLang(a, id), phoneTag())).chevron(a).view, 0);
```

`CompanionSection.root(g.packDir)` computes the companion root the way `dev.peanuts.Games.Entry.companionRoot` does: `<packDir>/../peanuts`
(`<game>/<title ID>-work/peanuts`), or null without a pack (then the section says "build the game first" and Install is off).

## 1b. GameSettings.java: the Mods section (one line)

Same file, in `static void build(MainActivity a, LinearLayout content, Game g, Runnable again, Runnable close)`, directly before the Companion section's line, so
Mods shows above Companion in the Game tab:

```java
        options.rows.addView(dev.peanuts.settings.ModsSection.view(a, id, g.name, g.gameDir, g.packDir, pack -> a.launch(new Game(id, g.name, g.gameDir, pack))));   // PEANUTS: the Mods section, above Companion (...)
```

`ModsSection` lists the game's mods (a switch and a bin each), installs one from a folder or a zip (`dev.peanuts.mods.ModImportUi`; the pickers' results come
back through `MainActivity.onActivityResult` → `dev.peanuts.ui.LibrarySettings.result`, requests 69 and 70 → `ModsSection.pickedFolder` / `pickedZip`), and for
code mods starts the build with mods (`dev.peanuts.build.ModBuildPipeline`) and shows its state. A second hook, at the top of
`MainActivity.launch`, asks `ModsSection.packToPlay` for the pack of every start: the variant's while code mods are on and built, else the normal one.
A game without a pack gets one row, "Build the game first". Both sections draw upstream's rows through `SectionUi` (reflection onto `Ui`).

## 2. AndroidManifest.xml — the build service (one element)

File: `android/app/src/ondevice/AndroidManifest.xml` (merged over main's in every APK; it already declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC and
POST_NOTIFICATIONS). Inside `<application>`, after the PackService element:

```xml
        <service android:name="dev.peanuts.build.BuildService" android:exported="false" android:foregroundServiceType="dataSync" />   <!-- PEANUTS: the library's build service -->
```

The service runs in the launcher's process (no `android:process`), so the game sheet's listener is called directly; the packer it starts (`PackService`) stays in `:pack`.

## 3. android/app/.gitignore — the `dev.peanuts.build` package is ignored (one line)

`android/app/.gitignore` line 2 is `build/`, which matches `src/main/java/dev/peanuts/build/` too (git check-ignore confirms), so the pipeline's sources
would never be committed. Either change that line to anchor it at the module root:

```
/build/
```

or add after it:

```
!src/main/java/dev/peanuts/build/
```

(or `git add -f` the folder once; the files then stay tracked). The session owner picks; nothing in the pipeline depends on which.
