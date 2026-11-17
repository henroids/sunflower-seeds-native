"""A companion release's notes in the versions list (dev.peanuts.ui.NotesMarkup): GitHub Markdown and its HTML badge as simple HTML and one plain line.
javac compiles the class (no Android types) with android/app/src/jvmtest's NotesMarkupJvmTest. Skipped without a JDK."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / 'android/app/src/main/java/dev/peanuts/ui/NotesMarkup.java'
TEST = REPO / 'android/app/src/jvmtest/java/dev/peanuts/ui/NotesMarkupJvmTest.java'


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class Notes(unittest.TestCase):
    def test_jvm(self):
        with tempfile.TemporaryDirectory(prefix='notesmarkup_') as t:
            subprocess.run(['javac', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-d', t, str(SRC), str(TEST)], check=True)
            r = subprocess.run(['java', '-cp', t, 'dev.peanuts.ui.NotesMarkupJvmTest'], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertRegex(r.stdout, r'^ok \d+')


class Wiring(unittest.TestCase):
    def test_versions_list_uses_it(self):
        src = (REPO / 'android/app/src/main/java/dev/peanuts/ui/CompanionVersions.java').read_text(encoding='utf-8')
        self.assertIn('NotesMarkup.plain(item.notes)', src)
        self.assertIn('NotesMarkup.html(item.notes)', src)


if __name__ == '__main__':
    unittest.main()
