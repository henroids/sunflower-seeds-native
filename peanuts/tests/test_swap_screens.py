"""Swap screens (GitHub issue #131): the game's start goes through dev.peanuts.Screens, and the companion follows the game to the other display."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'android/app/src/main/java'


class SwapScreens(unittest.TestCase):
    def test_both_game_starts_take_the_screens_options(self):
        for f in ('dev/nxnative/android/MainActivity.java', 'dev/nxnative/android/FrontendLaunch.java'):
            src = (JAVA / f).read_text(encoding='utf-8')
            self.assertRegex(src, r'startActivity\((\w+), dev\.peanuts\.Screens\.gameStart\(this, \1\)\);\s+// PEANUTS:', f)

    def test_off_means_the_start_as_before(self):
        src = (JAVA / 'dev/peanuts/Screens.java').read_text(encoding='utf-8')
        body = src[src.index('public static Bundle gameStart'):]
        self.assertLess(body.index('if (!PeanutsPrefs.swapScreens(c)) return null;'), body.index('setLaunchDisplayId'))

    def test_companion_takes_the_display_the_game_is_not_on(self):
        src = (JAVA / 'dev/peanuts/Companion.java').read_text(encoding='utf-8')
        self.assertIn('if (own != Display.DEFAULT_DISPLAY) {', src)
        self.assertIn('new android.content.Intent(game, CompanionScreen.class)', src)
        screen = (JAVA / 'dev/peanuts/CompanionScreen.java').read_text(encoding='utf-8')
        self.assertIn('FLAG_NOT_FOCUSABLE', screen)
        self.assertIn('Companion::refocusGame', screen)

    def test_companion_screen_runs_in_the_game_process(self):
        manifest = (ROOT / 'android/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
        line = next(l for l in manifest.splitlines() if 'dev.peanuts.CompanionScreen' in l)
        self.assertIn('android:process=":game"', line)
        self.assertIn('<!-- PEANUTS:', line)

    def test_presentation_and_screen_share_one_page(self):
        pres = (JAVA / 'dev/peanuts/CompanionPresentation.java').read_text(encoding='utf-8')
        screen = (JAVA / 'dev/peanuts/CompanionScreen.java').read_text(encoding='utf-8')
        for src in (pres, screen):
            self.assertRegex(src, re.compile(r'new CompanionPage\('))


if __name__ == '__main__':
    unittest.main()
