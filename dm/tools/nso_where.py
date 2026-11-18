#!/usr/bin/env python3
# Names the code a log's "<module>+0x<offset>" addresses fall in: the nearest defined dynamic symbol at or below each offset (the SDK modules export most of
# theirs; a game's main often none). With --imports, also the module's imports whose names contain the given text. Prints names and offsets only, no game data.
#   python3 dm/tools/nso_where.py <exefs folder> sdk+0x3f4cfc sdk+0x3f3608 main+0x152ab7c [--imports main:nsd]
import argparse, bisect, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nso_syms   # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('exefs', help='the folder with the code modules (main, sdk, subsdk*)')
    ap.add_argument('addrs', nargs='*', help='module+0xoffset, as the log writes them')
    ap.add_argument('--imports', action='append', default=[], help='module:text — that module\'s imports whose names contain text')
    a = ap.parse_args()
    tables = {}

    def table(mod):
        if mod not in tables:
            syms = sorted((off, size, name) for name, (off, size) in nso_syms.defined(nso_syms.load(os.path.join(a.exefs, mod))).items())
            tables[mod] = (syms, [s[0] for s in syms])
        return tables[mod]

    for addr in a.addrs:
        mod, _, off = addr.partition('+')
        off = int(off, 16)
        syms, starts = table(mod)
        i = bisect.bisect_right(starts, off) - 1
        if i < 0: print(f'{addr}: before every symbol'); continue
        start, size, name = syms[i]
        inside = 'in' if size and off < start + size else 'after (outside its size)' if size else 'after'
        print(f'{addr}: {inside} {name} +{off - start:#x}')
    for q in a.imports:
        mod, _, text = q.partition(':')
        for s in sorted(nso_syms.imports(a.exefs, mod)):
            if text.lower() in s.lower(): print(f'{mod} imports {s}')


if __name__ == '__main__':
    main()
