"""The enabled mods' cheat files in upstream's cheat list (dev.nxnative.android.ModCheats over upstream's Cheats.java, both without Android classes): one merged
file and one list, so the folder the host is given (DM_CHEATS) holds what the in-game panel lists and toggles. javac/java only, the pattern of test_ctl.py."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PKG = REPO / 'android/app/src/main/java/dev/nxnative/android'
BUILD = '0123456789ABCDEF'

RUNNER = r'''package dev.nxnative.android;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
public final class ModCheatsTest {
    public static void main(String[] a) throws Exception {   // shipped-or-"-" folder build on(comma) modCheatsDir...
        List<File> dirs = new ArrayList<>();
        for (int i = 4; i < a.length; i++) dirs.add(new File(a[i]));
        List<Cheats.Entry> l = ModCheats.install(a[0].equals("-") ? null : new File(a[0]), new File(a[1]), a[2],
                new LinkedHashSet<>(a[3].isEmpty() ? new ArrayList<String>() : Arrays.asList(a[3].split(","))), dirs);
        if (l == null) { System.out.println("none"); return; }
        StringBuilder sb = new StringBuilder();
        for (Cheats.Entry e : l) if (e.toggles()) sb.append(e.key).append('|');
        System.out.println(sb);
    }
}
'''


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'needs a JDK')
class ModCheats(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix='modcheats-'))
        src = cls.tmp / 'src/dev/nxnative/android'; src.mkdir(parents=True)
        (src / 'ModCheatsTest.java').write_text(RUNNER, encoding='utf-8')
        cls.out = cls.tmp / 'out'
        subprocess.run(['javac', '-nowarn', '-d', str(cls.out), str(PKG / 'Cheats.java'), str(PKG / 'Json.java'), str(PKG / 'ModCheats.java'), str(src / 'ModCheatsTest.java')], check=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def run_install(self, shipped, folder, on, dirs):
        r = subprocess.run(['java', '-cp', str(self.out), 'dev.nxnative.android.ModCheatsTest', str(shipped) if shipped else '-', str(folder), BUILD, ','.join(on), *map(str, dirs)],
                           capture_output=True, text=True, check=True)
        return r.stdout.strip()

    def case(self, name):
        d = self.tmp / name; d.mkdir()
        return d

    def mod(self, d, mod, file, text):
        c = d / 'mods' / mod / 'cheats'; c.mkdir(parents=True, exist_ok=True)
        (c / file).write_text(text, encoding='utf-8')
        return c

    def test_mods_join_the_profile_and_the_imported_cheats(self):
        d = self.case('all')
        shipped = d / f'{BUILD}.txt'; shipped.write_text('[Moon Jump]\n04000000 00000010 00000001\n', encoding='utf-8')
        folder = d / 'cheats'; (folder / 'imported').mkdir(parents=True)
        (folder / 'imported' / 'mine.txt').write_text(f'build {BUILD}\n[Coins]\n04000000 00000020 00000063\n', encoding='utf-8')
        m1 = self.mod(d, 'First Mod', f'{BUILD}.txt', '[Moon Jump]\n04000000 00000010 00000002\n[Speed]\n04000000 00000030 00000002\n')
        self.mod(d, 'First Mod', 'FEDCBA9876543210.txt', '[Other Build]\n04000000 00000040 00000001\n')
        m2 = self.mod(d, 'second', f'{BUILD.lower()}.txt', '[Speed]\n04000000 00000030 00000004\n')
        keys = self.run_install(shipped, folder, ['Moon Jump', 'Speed'], [m1, m2]).strip('|').split('|')
        self.assertEqual(keys, ['Moon Jump', 'Coins', 'Moon Jump (imported)', 'Speed', 'Speed (imported)'], 'the profile\'s, the imported, then each mod\'s; a name taken gets its suffix')
        merged = (folder / f'{BUILD}.txt').read_text(encoding='utf-8')
        self.assertIn('[--SectionStart:Imported: mod First Mod: ' + BUILD + '--]', merged)
        self.assertIn('[--SectionStart:Imported: mod second: ' + BUILD.lower() + '--]', merged)
        self.assertNotIn('Other Build', merged, 'a mod\'s file of another build is left out')
        self.assertEqual((folder / f'{BUILD}.enabled').read_text(encoding='utf-8').split('\n')[:-1], ['Moon Jump', 'Speed'], 'the toggles the player set, by key: a mod\'s cheat is off until it is turned on')
        self.assertEqual(sorted(p.name for p in folder.iterdir()), [f'{BUILD}.enabled', f'{BUILD}.txt', 'imported'], 'one merged file and its toggles')

    def test_a_game_with_only_a_mods_cheats(self):
        d = self.case('modonly')
        m = self.mod(d, 'm', f'{BUILD}.txt', '[Only]\n04000000 00000010 00000001\n')
        self.assertEqual(self.run_install(None, d / 'cheats', ['Only'], [m]), 'Only|')
        self.assertEqual((d / 'cheats' / f'{BUILD}.enabled').read_text(encoding='utf-8'), 'Only\n')

    def test_without_mods_it_is_upstreams_install(self):
        d = self.case('plain')
        shipped = d / f'{BUILD}.txt'; shipped.write_text('[A]\n04000000 00000010 00000001\n', encoding='utf-8')
        self.assertEqual(self.run_install(shipped, d / 'cheats', [], []), 'A|')
        self.assertEqual(self.run_install(None, d / 'none', [], [d / 'no such folder']), 'none')


if __name__ == '__main__':
    unittest.main()
