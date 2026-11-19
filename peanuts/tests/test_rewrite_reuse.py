"""dm/peanuts/reuse/rewrite_reuse.py (issue #120): the rewrite step keeps a module whose key moved only with other builds' rows of the profile, and rewrites it on any
change of what its own rewrite reads (its build's patches, its module's build rows, main's fps_decouple rows, its bytes, the rewriter) or of its patched.bin. The
profiles and modules here are small stand-ins (an NSO header with a build ID); no game files."""
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(REPO / 'dm/peanuts/reuse'), str(REPO / 'dm/games')]
import rewrite_reuse as rr   # noqa: E402
import game   # noqa: E402

REWRITER = ('patch_nso.py', 'nso.py')
SDK, MAIN_A, MAIN_B = 'aa' * 16, 'b1' * 16, 'b2' * 16


def module(bid, body=b'code'):
    h = bytearray(0x100); h[:4] = b'NSO0'; h[0x40:0x50] = bytes.fromhex(bid[:32]); return bytes(h) + body


class Rewrite(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp(prefix='pnrw_'))
        self.prof = self.d / 'games' / '0100000000BB0000'; self.prof.mkdir(parents=True)
        self.out = self.d / 'pack'; self.out.mkdir()
        self.saved = dict(os.environ); os.environ['PN_REUSE_DIR'] = str(self.d / 'reuse')
        rr._LOADED.clear()

    def tearDown(self):
        os.environ.clear(); os.environ.update(self.saved)
        shutil.rmtree(self.d, ignore_errors=True)

    def profile(self, rows, patches=None, fps=None):
        """game.tsv with build rows [(version, display, main ID, sdk ID)]; patches.py text (None: no file); fps_decouple value"""
        t = 'title_id\t0100000000BB0000\n' + ''.join(f'build\t{v}\t{d}\tmain={m}\tsdk={s}\n' for v, d, m, s in rows) + (f'fps_decouple\t{fps}\n' if fps else '')
        (self.prof / 'game.tsv').write_text(t)
        p = self.prof / 'patches.py'
        if patches is None: p.unlink(missing_ok=True)
        else: p.write_text(patches)
        rr._LOADED.pop(os.path.realpath(p), None)
        return game.Game(self.prof)

    def built(self, name, src, g, key='key-1', binary=b'patched'):
        """A module the rewrite made: its files and key in the pack, and its record"""
        (self.out / f'{name}.patched.bin').write_bytes(binary)
        (self.out / f'{name}.android.key').write_text(key + '\n')
        rr.record(name, src, self.out, g, REWRITER)

    def src(self, name, b):
        p = self.d / 'src' / name; p.parent.mkdir(exist_ok=True); p.write_bytes(b); return p

    def keeps(self, name, src, g):
        return rr.keep(name, src, self.out, g, 'key-2', REWRITER)

    def test_a_new_version_row_keeps_the_unchanged_sdk(self):
        sdk = self.src('sdk', module(SDK))
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)]))
        self.assertTrue(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK), ('v2', '1.0.1', MAIN_B, SDK)])))
        self.assertEqual((self.out / 'sdk.android.key').read_text(), 'key-2\n')

    def test_main_is_rewritten_when_another_main_build_is_added(self):
        main = self.src('main', module(MAIN_A))
        self.built('main', main, self.profile([('v1', '1.0.0', MAIN_A, SDK)]))
        self.assertFalse(self.keeps('main', main, self.profile([('v1', '1.0.0', MAIN_A, SDK), ('v2', '1.0.1', MAIN_B, SDK)])))
        self.assertEqual((self.out / 'main.android.key').read_text(), 'key-1\n')

    def test_patches_for_this_build_rewrite_it(self):
        sdk = self.src('sdk', module(SDK))
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches='CUTS = {}\n'))
        self.assertTrue(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches=f'CUTS = {{{MAIN_B!r}: []}}\n')))   # another build's cuts: kept
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches='CUTS = {}\n'))
        self.assertFalse(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches=f'CUTS = {{{SDK[:32]!r}: [(0, "00", "00", "x")]}}\n')))

    def test_another_profiles_patches_for_this_build_count(self):
        # patch_nso.py merges CUTS by build ID from every patch file: a module whose build another profile registers follows that file too
        os.environ['DM_GAMES_DIR'] = str(self.d / 'games')
        other = self.d / 'games' / '0100000000CC0000'; other.mkdir()
        (other / 'patches.py').write_text(f'CUTS = {{{SDK!r}: []}}\n')
        sdk = self.src('sdk', module(SDK))
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)]))
        self.assertTrue(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK), ('v2', '1.0.1', MAIN_B, SDK)])))
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)]))
        (other / 'patches.py').write_text(f'CUTS = {{{SDK!r}: [(0, "00", "00", "x")]}}\n'); rr._LOADED.clear()
        self.assertFalse(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK), ('v2', '1.0.1', MAIN_B, SDK)])))

    def test_match_cuts_of_the_module_rewrite_it(self):
        sdk = self.src('sdk', module(SDK))
        self.built('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches='MATCH_CUTS = {}\n'))
        self.assertFalse(self.keeps('sdk', sdk, self.profile([('v1', '1.0.0', MAIN_A, SDK)], patches="MATCH_CUTS = {'sdk': [{'rule': 1}]}\n")))

    def test_its_bytes_its_patched_bin_and_fps_rows(self):
        main = self.src('main', module(MAIN_A))
        g = self.profile([('v1', '1.0.0', MAIN_A, SDK)])
        self.built('main', main, g)
        self.assertFalse(self.keeps('main', self.src('main2', module(MAIN_A, b'other code')), g))
        (self.out / 'main.patched.bin').write_bytes(b'changed')
        self.assertFalse(self.keeps('main', main, g))
        self.built('main', main, g)
        self.assertFalse(self.keeps('main', main, self.profile([('v1', '1.0.0', MAIN_A, SDK)], fps='sead')))
        self.assertTrue(self.keeps('main', main, self.profile([('v1', '1.0.0', MAIN_A, SDK)])))

    def test_no_record_no_folder_other_key(self):
        sdk = self.src('sdk', module(SDK))
        g = self.profile([('v1', '1.0.0', MAIN_A, SDK)])
        (self.out / 'sdk.patched.bin').write_bytes(b'p'); (self.out / 'sdk.android.key').write_text('key-1\n')
        self.assertFalse(self.keeps('sdk', sdk, g))   # no record
        self.built('sdk', sdk, g)
        (self.out / 'sdk.android.key').write_text('key-0\n')   # the pack's key is not the recorded one
        self.assertFalse(self.keeps('sdk', sdk, g))
        self.built('sdk', sdk, g)
        os.environ['PN_REUSE'] = '0'   # env.txt: off
        self.assertFalse(self.keeps('sdk', sdk, g))
        del os.environ['PN_REUSE']
        self.assertTrue(self.keeps('sdk', sdk, g))
        self.built('sdk', sdk, g)
        del os.environ['PN_REUSE_DIR']
        self.assertFalse(self.keeps('sdk', sdk, g))


if __name__ == '__main__':
    unittest.main()
