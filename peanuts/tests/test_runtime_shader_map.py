"""Runtime shader detection reads the APK tools archive without loading PackNative/CPython."""
import shutil
import struct
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / 'android/app/src/main/java/dev/peanuts/build/RuntimeShaderMap.java'


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class RuntimeShaderMap(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix='runtime_shader_map_')
        cls.root = Path(cls.tmp.name)
        runner = cls.root / 'RuntimeShaderMapTest.java'
        runner.write_text('''package dev.peanuts.build;
import java.io.*;
public final class RuntimeShaderMapTest {
    public static void main(String[] args) throws Exception {
        try {
            if (args.length > 2) System.out.println(RuntimeShaderMap.missing(new FileInputStream(args[0]), args[1], new File(args[2])));
            else System.out.println(RuntimeShaderMap.compilesAtRunTime(new FileInputStream(args[0]), args[1]));
        } catch (IOException e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}
''')
        subprocess.run(['javac', '-d', str(cls.root), str(SRC), str(runner)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def detect(self, entries, title, library=None):
        archive = self.root / 'tools.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, text in entries.items():
                z.writestr(name, text)
        args = ['java', '-cp', str(self.root), 'dev.peanuts.build.RuntimeShaderMapTest', str(archive), title]
        if library is not None:
            args.append(str(library))
        r = subprocess.run(args,
                           check=True, capture_output=True, text=True)
        return r.stdout.strip()

    def test_shipped_profiles(self):
        titles = {'010055D009F78000': 'true', '01008CF01BAAC000': 'false', '0100152000022000': 'false'}
        entries = {f'dm/games/{title}/shader_map.tsv': (REPO / f'dm/games/{title}/shader_map.tsv').read_text()
                   for title in titles}
        for title, expected in titles.items():
            with self.subTest(title=title):
                self.assertEqual(self.detect(entries, title), expected)

    def test_only_target_profile_and_source_column_count(self):
        entries = {'dm/games/other/shader_map.tsv': 'vs_runtime.bin\t0\tvertex\t1234\t/glslc_capture/one.glslc\n',
                   'dm/games/target/shader_map.tsv': '# /glslc_capture/ignored.glslc\nvs_runtime.bin\t0\tvertex\t1234\tmain\n'}
        self.assertEqual(self.detect(entries, 'target'), 'false')

    def test_missing_profile_is_an_error(self):
        self.assertTrue(self.detect({'README': 'no profiles'}, 'target').startswith('error: '))

    def library(self, title, keys):
        header = bytearray(128)
        struct.pack_into('<II', header, 0, 0x4B56534D, 1)
        header[40:56] = title.encode('ascii')
        struct.pack_into('<II', header, 72, len(keys), 128)
        rows = bytearray()
        for key, modules in keys:
            entry = bytearray(64)
            struct.pack_into('<Q', entry, 0, int(key, 16))
            entry[17] = modules
            rows += entry
        p = self.root / 'dmsh_vk.lib'
        p.write_bytes(header + rows)
        return p

    def test_adopted_three_houses_library_missing_runtime_shaders(self):
        title = '010055D009F78000'
        profile = (REPO / f'dm/games/{title}/shader_map.tsv').read_text()
        rows = [line.split('\t') for line in profile.splitlines() if not line.startswith('#')]
        runtime = {r[3] for r in rows if len(r) >= 5 and r[4].startswith('/glslc_capture/')}
        self.assertEqual(len(runtime), 9)
        static = {r[3] for r in rows if len(r) >= 5 and r[0].endswith('.bin') and r[3] not in runtime}
        entries = {f'dm/games/{title}/shader_map.tsv': profile}
        # No job.json is needed: the incomplete library itself is sufficient evidence.
        lib = self.library(title, [(key, 1) for key in sorted(static)])
        self.assertEqual(self.detect(entries, title, lib), '9')
        lib = self.library(title, [(key, 1) for key in sorted(static | runtime)])
        self.assertEqual(self.detect(entries, title, lib), '0')

    def test_entry_without_modules_is_still_missing(self):
        title = '0100000000000001'
        entries = {f'dm/games/{title}/shader_map.tsv': 'vs_runtime.bin\t0\tvertex\tffffffffffffffff\t/glslc_capture/a.glslc\n'}
        lib = self.library(title, [('ffffffffffffffff', 0)])
        self.assertEqual(self.detect(entries, title, lib), '1')
        lib = self.library(title, [('ffffffffffffffff', 1)])
        self.assertEqual(self.detect(entries, title, lib), '0')

    def test_invalid_library_does_not_report_complete(self):
        title = '0100000000000001'
        entries = {f'dm/games/{title}/shader_map.tsv': 'vs_runtime.bin\t0\tvertex\t1234\t/glslc_capture/a.glslc\n'}
        lib = self.library('0100000000000002', [('1234', 1)])
        self.assertTrue(self.detect(entries, title, lib).startswith('error: '))
        lib = self.library(title, [('1234', 1)])
        lib.write_bytes(lib.read_bytes()[:-1])
        self.assertTrue(self.detect(entries, title, lib).startswith('error: '))

    def test_profile_without_captures_does_not_need_library(self):
        title = '0100000000000001'
        entries = {f'dm/games/{title}/shader_map.tsv': 'vs.bin\t0\tvertex\t1234\tmain\n'}
        self.assertEqual(self.detect(entries, title, self.root / 'nonexistent.lib'), '0')


if __name__ == '__main__':
    unittest.main()
