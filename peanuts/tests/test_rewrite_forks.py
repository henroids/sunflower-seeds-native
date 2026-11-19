"""dm/tools/patch_nso.py in_forks (issue #120): where multiprocessing cannot start (no _multiprocessing, as in the phone's embedded CPython) the rewriter's x18 and
spin phases fork their workers themselves. The function is taken out of patch_nso.py (the script rewrites a module when it runs, so it is not imported) and run
with stand-ins for what it uses. The results must be in_serial's, in order; a child that fails, or ends without its results, fails the call, and leaves no child
behind. Skipped where os.fork is missing (Windows). No game files."""
import ast
import os
import signal
import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = (REPO / 'dm/tools/patch_nso.py').read_text(encoding='utf-8')


def borrowed(names, workers):
    """in_serial and in_forks from patch_nso.py, with WORKERS and a progress that records its calls"""
    tree = ast.parse(SRC)
    fns = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in names]
    calls = []
    ns = {'os': os, 'sys': sys, 'signal': signal, 'WORKERS': workers, 'progress': lambda phase, done=0, total=1: calls.append((phase, done, total))}
    exec(compile(ast.Module(body=fns, type_ignores=[]), 'patch_nso.py', 'exec'), ns)
    return ns, calls


def square_plus(a, b): return [a * a + b] * 3


def fails_on_5(a):
    if a == 5: raise ValueError('piece 5')
    return a


def dies_on_3(a):
    if a == 3: os._exit(9)   # a child that ends without writing its results
    return a


@unittest.skipUnless(hasattr(os, 'fork'), 'no os.fork here')
class Forks(unittest.TestCase):
    def test_the_same_results_in_order(self):
        ns, calls = borrowed({'in_serial', 'in_forks'}, 4)
        jobs = [(i, i % 7) for i in range(37)]
        self.assertEqual(ns['in_forks'](square_plus, jobs, 'x18'), ns['in_serial'](square_plus, jobs))
        self.assertEqual([c[1] for c in calls if c[0] == 'x18'], [1, 2, 3, 4])   # a progress line as each child is done

    def test_fewer_jobs_than_workers(self):
        ns, _ = borrowed({'in_serial', 'in_forks'}, 8)
        self.assertEqual(ns['in_forks'](square_plus, [(2, 1), (3, 1)]), [[5] * 3, [10] * 3])

    def test_a_child_that_fails_fails_the_call(self):
        ns, _ = borrowed({'in_forks'}, 3)
        with self.assertRaisesRegex(RuntimeError, 'ValueError: piece 5'): ns['in_forks'](fails_on_5, [(i,) for i in range(9)])
        self.assertEqual(self.children(), [])

    def test_a_child_that_dies_fails_the_call(self):
        ns, _ = borrowed({'in_forks'}, 3)
        with self.assertRaisesRegex(RuntimeError, 'without its results'): ns['in_forks'](dies_on_3, [(i,) for i in range(9)])
        self.assertEqual(self.children(), [])

    def children(self):
        """This process's children left over (none: each was waited for)"""
        try: os.waitpid(-1, os.WNOHANG)
        except ChildProcessError: return []
        return ['a child is left']


class Wiring(unittest.TestCase):
    def test_in_parallel_takes_it_without_multiprocessing(self):
        self.assertIn('except ImportError: return in_forks(fn, jobs, phase)', SRC)

    def test_the_file_before_it_counts_as_the_same_rewriter(self):
        equiv = REPO / 'dm/tools/rewriter_equiv/patch_nso.py.6bc30f06074b'
        self.assertTrue(equiv.is_file())
        self.assertNotIn('def in_forks', equiv.read_text(encoding='utf-8'))
        self.assertIn('dm/tools/rewriter_equiv/patch_nso.py.6bc30f06074b\t', (REPO / 'peanuts/upstream-unmarked.txt').read_text(encoding='utf-8'))

    def test_the_pack_script_runs_it_in_the_worker(self):
        pack = (REPO / 'scripts/pack_android.py').read_text(encoding='utf-8')
        self.assertIn("rc = pn_rewrite_worker(REPO / 'dm/tools/patch_nso.py'", pack)
        self.assertIn("start_new_session=True", pack)
        svc = (REPO / 'android/app/src/ondevice/java/dev/nxnative/android/PackService.java').read_text(encoding='utf-8')
        self.assertIn('PackEngine.setenv("PN_REWRITE_J"', svc)


if __name__ == '__main__':
    unittest.main()
