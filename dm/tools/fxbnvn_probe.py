#!/usr/bin/env python3
# MLB The Show 26 (0100AA7024094000) bring-up: what is inside the game's .fxbnvn shader files, without writing any game data out.
# The survey found no BNSH / BAHS at the top level; NSW/SHADERS holds 468 files, 456 of them .fxbnvn. This reads them straight from the .nup
# and prints only structure: sizes, the first 16 header bytes, and where known magics sit (NVN control sections, code area headers, BNSH,
# compression frames). Nothing it prints is shader code or game content, so the output is safe to share.
#   ~/peanuts-env/bin/python dm/tools/fxbnvn_probe.py "<Game>.nup" [--ext .fxbnvn] [--detail 3] > probe.txt
import argparse, collections, re, sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / 'dm' / 'tools'))
import nup   # noqa: E402

MAGICS = {
    'ctl (NVN control 0x98761234)': bytes.fromhex('34127698'),
    'gfx code (0x12345678)': bytes.fromhex('78563412'),
    'cs code (0x12345679)': bytes.fromhex('79563412'),
    'BNSH': b'BNSH',
    'GLSLC out (0x19866891)': bytes.fromhex('91688619'),
    'zstd frame': bytes.fromhex('28b52ffd'),
    'lz4 frame': bytes.fromhex('04224d18'),
    'zlib 78da': bytes.fromhex('78da'),
    'zlib 789c': bytes.fromhex('789c'),
}


def offsets(d, m, cap=8):
    out = []
    for x in re.finditer(re.escape(m), d):
        out.append(x.start())
        if len(out) >= cap: break
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('nup')
    ap.add_argument('--ext', default='.fxbnvn', help='file extension to look at (default .fxbnvn)')
    ap.add_argument('--detail', type=int, default=3, help='files to describe in detail (smallest, median, largest)')
    a = ap.parse_args()
    n = nup.Nup(a.nup)
    pkgs = sorted(n.packages, key=lambda p: (nup.KINDS.get(p.kind) != 'update', -p.version))   # the newest update first: its RomFS is the game as it runs
    p = pkgs[0]
    print(f'package {p!r}')
    root = n.find('romfs', ('pkg', p.index))
    files = []
    for d, _, fs in n.walk(root):
        for f in fs:
            if f.lower().endswith(a.ext): files.append(f'{d}/{f}' if d else f)
    print(f'{len(files)} {a.ext} files')
    if not files: return
    totals, heads, sizes, rows = collections.Counter(), collections.Counter(), [], []
    for path in sorted(files):
        node = n.find(path, root)
        with n.open(node) as r: d = r.read()
        sizes.append(len(d)); heads[d[:4].hex()] += 1
        c = {k: len(re.findall(re.escape(m), d)) for k, m in MAGICS.items()}
        for k, v in c.items(): totals[k] += v
        rows.append((len(d), path, d[:16].hex(' '), c, {k: offsets(d, m) for k, m in MAGICS.items() if c[k]}))
    print(f'sizes: min {min(sizes)}, max {max(sizes)}, total {sum(sizes) / 1048576:.1f} MB')
    print('first 4 bytes (count): ' + ', '.join(f'{h} x{k}' for h, k in heads.most_common(8)))
    print('magic totals over all files:')
    for k, v in totals.items(): print(f'  {k:32s} {v}')
    rows.sort()
    pick = sorted({0, len(rows) // 2, len(rows) - 1} | set(range(min(a.detail, len(rows)))))
    for i in pick:
        size, path, head, c, offs = rows[i]
        print(f'\n{path}  ({size} bytes)\n  head: {head}')
        for k, v in c.items():
            if v: print(f'  {k:32s} x{v}  first at ' + ', '.join(hex(o) for o in offs[k]))
    # the first control section of the median file, if any: the two lengths an NVN control section records (+0x20 its own, +0x704 the code area's)
    size, path, *_ = rows[len(rows) // 2]
    with n.open(n.find(path, root)) as r: d = r.read()
    ctl = offsets(d, MAGICS['ctl (NVN control 0x98761234)'], 4)
    for o in ctl:
        if o + 0x708 <= len(d):
            ln, code = int.from_bytes(d[o + 0x20:o + 0x24], 'little'), int.from_bytes(d[o + 0x704:o + 0x708], 'little')
            print(f'  ctl @{o:#x}: own length {ln:#x}, code area length {code:#x}, 4-byte aligned {not o & 3}')


if __name__ == '__main__':
    main()
