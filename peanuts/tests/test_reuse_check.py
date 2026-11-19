"""The full check of a build's kept shaders on the Android side (issue #120, peanuts/docs/INCREMENTAL-BUILD.md "The full check, later"):
android/app/src/ondevice/java/dev/nxnative/android/PackService.java checkKept and android/app/src/main/java/dev/peanuts/build/ReuseCheck.java. They need
Android to run, so these tests read how they are wired, each for a fault a review found. No game files."""
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PACK = REPO / 'android/app/src/ondevice/java/dev/nxnative/android/PackService.java'
CHECK = REPO / 'android/app/src/main/java/dev/peanuts/build/ReuseCheck.java'


def check_kept():
    """PackService.checkKept's body"""
    s = PACK.read_text(encoding='utf-8')
    i = s.index('    private void checkKept(Job j) {')
    return s[i:s.index('\n    }\n', i)]


class Stop(unittest.TestCase):
    def test_a_game_or_a_waiting_job_stops_the_check_whenever_it_comes(self):
        # dm_pack_interrupt does nothing before the tool's run has begun (no run_tid), so one interrupt sent then was lost and the check translated on
        # beside the game (or a build waited for the whole check): the watcher sends it again every 3 s while the tool runs, and a game that starts asks
        # requestStop once, whose 20 s end of the process stands behind it
        body = check_kept()
        watch = body[body.index('Thread watch = new Thread('):body.index('watch.setDaemon(true);')]
        self.assertNotIn('return;', watch)
        self.assertIn('requestStop("game")', watch)
        self.assertRegex(watch, r'afterCheck != null')
        self.assertRegex(watch, r'engine\.interrupt\(\)')


if __name__ == '__main__':
    unittest.main()
