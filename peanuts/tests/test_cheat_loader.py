"""The cheat loader of a game's page (SPEC-mod-menu.md, Package B: dev.peanuts.mods.CheatFile, CheatImport, CheatSources, the launcher's ModCheats): the JVM
part (android/app/src/jvmtest's CheatLoaderJvmTest; skipped without a JDK) reads a made-up cheat file of a cheat site's shape, the import's layouts and its
choice of build, the sources and the merged file. PEANUTS_CHEATS_REAL=<file> also reads a real cheat file as a smoke test (it is never part of the
repository). Then the sources: the gate that only a profile's games took cheats is gone, a start merges through CheatSources, and the text the player reads
(CheatFile.describe) keeps UI-RULES.md."""
import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP = REPO / 'android/app/src'
MODS = APP / 'main/java/dev/peanuts/mods'
LAUNCHER = APP / 'main/java/dev/nxnative/android'
TEST = APP / 'jvmtest/java/dev/peanuts/mods/CheatLoaderJvmTest.java'
CORE = [MODS / 'CheatFile.java', MODS / 'CheatImport.java', MODS / 'CheatSources.java']
UI_FILES = [MODS / 'CheatPick.java', LAUNCHER / 'CheatsBridge.java', MODS / 'CheatsPage.java', MODS / 'CheatImportUi.java']   # the words the player reads


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'needs a JDK')
class Jvm(unittest.TestCase):
    def test_cheat_loader(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t)
            subprocess.run(['javac', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-d', str(d / 'classes'), *map(str, CORE), str(TEST)],
                           check=True, capture_output=True, text=True)
            (d / 'work').mkdir()
            args = ['java', '-cp', str(d / 'classes'), 'dev.peanuts.mods.CheatLoaderJvmTest', str(d / 'work')]
            real = os.environ.get('PEANUTS_CHEATS_REAL')
            if real:
                args.append(real)
            r = subprocess.run(args, capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(r.stdout.strip(), 'ok')
            if real:
                print(r.stderr)


class Sources(unittest.TestCase):
    def test_every_game_takes_cheats(self):
        prefs = (LAUNCHER / 'Prefs.java').read_text(encoding='utf-8')
        self.assertRegex(prefs, r'static boolean cheatsGame\(String titleId\) \{ return true; \}\s+// PEANUTS')

    def test_the_cheat_rows_read_the_sources(self):
        ui = (LAUNCHER / 'CheatsUi.java').read_text(encoding='utf-8')
        self.assertRegex(ui, r'add\.accept\(CheatsBridge\.filesRow\(a, g, again\)\);\s+// PEANUTS')
        lib = (APP / 'main/java/dev/peanuts/ui/LibrarySettings.java').read_text(encoding='utf-8')
        self.assertIn('dev.nxnative.android.CheatsBridge.result(a, request, result, data)', lib, "the sheet's pickers come back through LibrarySettings.result")
        mc = (LAUNCHER / 'ModCheats.java').read_text(encoding='utf-8')
        self.assertIn('CheatSources.migrate(', mc, "upstream's imported/ files become sources")
        bridge = (LAUNCHER / 'CheatsBridge.java').read_text(encoding='utf-8')
        self.assertIn('Icons.tile(row.tile, "folder")', bridge, "the Cheat files row's tile is the Peanuts folder icon, no emoji")

    def test_the_page_replaces_the_rows(self):   # Part D
        prefs = (LAUNCHER / 'Prefs.java').read_text(encoding='utf-8')
        self.assertIn('CheatsBridge.wanted(c, titleId', prefs, 'a start passes cheats when a source is on')
        settings = (LAUNCHER / 'GameSettings.java').read_text(encoding='utf-8')
        self.assertNotRegex(settings, r'(?m)^\s*if \(Prefs\.cheatsGame\(id\)\) CheatsUi\.rows', 'the Game tab has the page row, not the cheat rows (only the comment says what it was)')
        self.assertIn('dev.peanuts.mods.CheatsPage.entry(', settings)
        self.assertIn('dev.peanuts.mods.CheatsPage.build(', settings)
        self.assertIn('dev.peanuts.mods.CheatsPage.stepTab(', settings)
        page = (MODS / 'CheatsPage.java').read_text(encoding='utf-8')
        self.assertIn('stick(content, top);', page, "the page's top stays while the rest scrolls under it (the canvas)")
        lib = (APP / 'main/java/dev/peanuts/ui/LibrarySettings.java').read_text(encoding='utf-8')
        self.assertLess(lib.index('CheatImportUi.result('), lib.index('CheatsBridge.result('), "the page's Add is asked before the rows' sheet (the same request codes)")

    def test_the_start_merges_the_sources(self):
        mc = (LAUNCHER / 'ModCheats.java').read_text(encoding='utf-8')
        self.assertIn('CheatSources.merge(', mc)

    def test_ui_text_rules(self):
        for f in UI_FILES + CORE:
            src = f.read_text(encoding='utf-8')
            strings = re.findall(r'"((?:[^"\\]|\\.)*)"', src)
            for s in strings:
                for bad, what in (('\u2014', 'an em dash'), ('\u2013', 'an en dash'), ('\u00b7', 'a middle dot'), (' \u2022 ', 'a bullet')):
                    self.assertNotIn(bad, s, f'{f.name}: {what} in "{s}"')
                self.assertFalse(re.search('[\U0001F300-\U0001FAFF\u2600-\u27bf]', s), f'{f.name}: an emoji in "{s}"')
                if f in UI_FILES and ' ' in s and not s.startswith('(?'):
                    self.assertNotIn('!', s, f'{f.name}: an exclamation mark in "{s}"')


if __name__ == '__main__':
    unittest.main()
