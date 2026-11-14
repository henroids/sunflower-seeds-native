# PEANUTS: new file (code mods: branches straight from one module into another, design/platform/DESIGN-MODS.md "Code injection")
"""The console's module layout, for a build with code mods whose modules branch straight into each other.

A mod made for the console may hold b / bl instructions that leave their module: Luminescent Platinum for Pokemon Brilliant Diamond 1.3.0 patches 106 of them into main
that go to the subsdk1 it adds, and that module calls main 1,228 times the same way. Their distances are the console's, where the loader places the modules of a process
one right after the other in a fixed order (rtld, main, subsdk0 to subsdk9, sdk), each taking its segments and bss rounded up to a 4 KiB page (Atmosphère's
ldr_process_creation.cpp: the next module starts at the end of the one before). For that game main takes 0x5063000 and subsdk0 0x6a5000, so subsdk1 stands at
main+0x5708000, the one distance at which all 106 branches land on symbols subsdk1 exports. This host's modules stand farther apart (each carries a key page and a patch
area), so the packer works out where each such branch goes on the console (this file), the rewriter checks them against what it rewrote and lists them in
<module>.xmod.tsv (patch_nso.py --xmod), and the loader aims each again at the bases it gave the modules (dm/host/loader.c xmod_apply)."""
import struct

PAGE = 0x1000
CONSOLE_ORDER = ('rtld', 'main') + tuple(f'subsdk{i}' for i in range(10)) + ('sdk',)   # the order Horizon's loader maps a process's modules in


def rank(name):
    """A module's place in the console's order; a name it does not know sorts after all of them"""
    return CONSOLE_ORDER.index(name) if name in CONSOLE_ORDER else len(CONSOLE_ORDER)


def insert(mods, name):
    """mods with name added where the console loads it: before the first module that comes later in the console's order (an added subsdk1 goes between subsdk0 and
    sdk, as on the console, so its symbols take the same precedence there and here), at the end when there is none. The other modules keep their order; a new list"""
    out = list(mods)
    if name in out: return out
    at = next((i for i, m in enumerate(out) if rank(m) > rank(name)), len(out))
    out.insert(at, name)
    return out


def header(head, name='module'):
    """(text length, size on the console) from an NSO's first 0x100 bytes: the size is where its last segment or its bss (which follows data) ends, on a page"""
    if len(head) < 0x100 or head[:4] != b'NSO0': raise ValueError(f'{name}: not an NSO')
    segs = [struct.unpack_from('<III', head, 0x10 + i * 0x10)[1:] for i in range(3)]   # (memory offset, size) of text, ro, data
    bss = struct.unpack_from('<I', head, 0x3c)[0]
    end = max(segs[0][0] + segs[0][1], segs[1][0] + segs[1][1], segs[2][0] + segs[2][1] + bss)
    return segs[0][1], (end + PAGE - 1) & ~(PAGE - 1)


def layout(heads):
    """{module: (offset from main on the console, text length, size)} for heads = {module: header()'s pair}, in the console's order. Without a main the first module
    is at 0 (the offsets are only ever compared with each other)"""
    names = sorted(heads, key=rank)   # stable: unknown names keep their order after the known ones
    at, out = 0, {}
    for m in names:
        out[m] = (at, heads[m][0], heads[m][1]); at += heads[m][1]
    zero = out['main'][0] if 'main' in out else 0
    return {m: (o - zero, t, s) for m, (o, t, s) in out.items()}


def far_branches(image, text_end, size, words=None):
    """[(offset, 'b' | 'bl', target)] of the b / bl in a module's text whose target lies outside its own size bytes; words: only these offsets (the ones a patch
    changed), else every word of the text"""
    out = []
    n = text_end & ~3
    if words is None:
        view = memoryview(bytes(image[:n])).cast('I')
        cand = (4 * i for i, w in enumerate(view) if w & 0x7C000000 == 0x14000000)
    else: cand = (o for o in sorted(words) if 0 <= o < n and not o & 3)
    for o in cand:
        w = struct.unpack_from('<I', image, o)[0]
        if w & 0x7C000000 != 0x14000000: continue
        d = w & 0x3FFFFFF; t = o + 4 * (d - ((d & 0x2000000) << 1))
        if not 0 <= t < size: out.append((o, 'bl' if w >> 31 else 'b', t))
    return out


def changed_words(before, after, text_end):
    """The word offsets of a module's text at which two images of it differ (a patch's edits), in order"""
    out, n = [], text_end & ~3
    for o in range(0, n, 0x1000):
        if before[o:o + 0x1000] != after[o:o + 0x1000]:
            out += [w for w in range(o, min(o + 0x1000, n), 4) if before[w:w + 4] != after[w:w + 4]]
    return out


def resolve(lay, name, target):
    """(module, offset in it) of an address target bytes from module name's start, when it lies in another module's text on the console; else None"""
    at = lay[name][0] + target
    for m, (o, text, _) in lay.items():
        if m != name and o <= at < o + text: return m, at - o
    return None


def plan(lay, far):
    """far = {module: (scan, far_branches())} for the modules that were searched (scan: 'all' or 'sites' with the list of words searched) → {module: the text of its
    layout file} for each module that has such branches or is the target of one. The rows are patch_nso.py's (its "Cross-module branches"): every module's place,
    where to search, and the offsets other modules branch to"""
    incoming = {m: [] for m in lay}
    for m, (_, _, branches) in far.items():
        for o, kind, t in branches:
            to = resolve(lay, m, t)
            if to: incoming[to[0]].append((to[1], m))
    out = {}
    for m in lay:
        scan, sites, branches = far.get(m, ('sites', [], []))
        if not branches and not incoming[m]: continue
        rows = [f'module\t{n}\t{o:#x}\t{t:#x}\t{s:#x}' for n, (o, t, s) in lay.items()]
        rows.append(f'scan\t{scan}')
        if scan == 'sites': rows += [f'site\t{o:#x}' for o in sorted(sites)]
        rows += [f'in\t{o:#x}\t{frm}' for o, frm in sorted(set(incoming[m]))]
        out[m] = ''.join(r + '\n' for r in rows)
    return out


def read_table(path):
    """[(offset, 'b' | 'bl', module, offset in it)] of a <module>.xmod.tsv"""
    out = []
    with open(path, encoding='utf-8') as f:
        for line in f.read().splitlines():
            if not line or line.startswith('#'): continue
            c = line.split('\t')
            out.append((int(c[0], 16), c[1], c[2], int(c[3], 16)))
    return out
