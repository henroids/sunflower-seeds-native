#!/usr/bin/env python3
# PEANUTS (issue #120, peanuts/UPSTREAM-EDITS.md Round 26): the phone's shaders step keeps every shader of the pack's older library whose translation would come
# out the same, and translates only the others. Run in place of dm/gfx/tools/vk_android_build.py with the same arguments and environment; it runs that tool in
# this process (as scripts/pack_android.py run_in_process runs the rewriter) and changes none of its files, so DMSH_VK_CHAIN does not move.
#
# Why: upstream's delta update (android/SHADERS.md#delta-updates) keeps an older library's entry only when the profile's manifest (vk_delta.tsv, from a full Mac
# build) has its hash. No Switch 1 profile has one, so every Build after an update, an added DLC or an Import again translated every shader again.
#
# Which entries are kept (each a library entry of the pack's shaders/, DMSH_VK_DELTA_FROM, of this DMSH_VK_CHAIN and this game):
#   same inputs  the older library was built from the same profile inputs (dmsh_chain.inputs) and the profile has its own pso_pairs.tsv: a shader's result is
#                then a function of the tools, the inputs and its key alone (vk_android_build.py result_file keys its result cache on exactly these), so the entry is
#                the one a translation makes now. Without the profile's own pso_pairs.tsv, load_pairs takes the split's copy, which the inputs digest does not
#                cover (an added DLC can add pairs, which pad a vertex shader's outputs): then only "same deps" keeps, whose digest has the pairs as taken.
#   same deps    the profile changed (an APK that adds a game version adds rows to the shader tables, which moves the inputs digest of every shader), but this
#                shader's own part of it did not: its stage, its shader_facts row, its pairs, its dual_want / bf_want membership, the geo_mesh and conflict rows
#                that name it, the facts of the mesh programs drawn with it, the profile's nvn_* / shader_* rows, and for a vertex shader the same of each of its
#                pixel shaders (dmsh_build.py shader_key and pair_keyed take the same parts for the Mac's per-shader cache). The digest of each shader is
#                recorded when a library is made (<PN_REUSE_DIR>/shaders/<library id>.tsv), so the next build compares the old digest with the new one.
# Either way only a shader this build makes (wanted_keys: vk_android_build.py's own selection, less the absences validated against the split), and a vertex
# shader only when its paired pixel shaders that this build makes are those the older library holds: its outputs are padded with theirs (PS_INPUTS), so an
# add-on installed since (its pixel shaders absent before) changes it while its part of the profile stays the same (same_pixel_shaders).
# The kept keys go into a manifest of this build's version, game and inputs whose hashes are the older library's own entries, in a copy of the profile folder
# (<PN_REUSE_DIR>/profile/<title ID>, DM_GAME pointed at it): vk_android_build.py's delta path then keeps them byte for byte and translates the rest.
#
# The check: a sample of the keys that could be kept (2 %, at least 16, at most 1,000, picked by a hash of the key, and at least 4 of each class: a stage, and a
# vertex shader with pixel shaders that pad it or one without) is left out of the manifest, so it is translated; when the library is written, each sample entry must be the older library's byte for byte (vk_android_delta.py entry_hash). One that differs, or a
# sample of which less than half could be compared, means the rule did not hold for this game: the kept results are deleted and the tool runs again with the
# profile as it is, which translates them (a full build; the shaders translated already are kept by its own result cache). A library that cannot be read, an
# entry that cannot be read, a record that is not this library's: that part is translated. Nothing here can make the step pass with a shader the tool did not
# make or keep by its own rules.
#
# Environment: PN_REUSE_DIR (the app's no_backup/reuse/<title ID>; unset: no record is kept and only "same inputs" applies), PN_REUSE=0 (env.txt: off, the tool
# runs as before). The profile's own manifest, when it is of this build, wins: the step is then upstream's delta update as it was.
import collections
import hashlib
import json
import os
import re
import runpy
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
TOOLS = REPO / 'dm/gfx/tools'
BUILD = TOOLS / 'vk_android_build.py'
FORMAT = 'pn-shader-deps 1'
SAMPLE_SHARE, SAMPLE_MIN, SAMPLE_MAX = 50, 16, 1000   # one key in SAMPLE_SHARE, within these bounds
SAMPLE_CLASS_MIN = 4   # and at least this many of each class of shader (sample_class), so a rule that misses one class is checked on it too
KEY = re.compile(r'[0-9a-f]{16}')


def say(text): print(text, flush=True)


def vk_version():
    """DMSH_VK_CHAIN of this tree (dm/gfx/nvn/dmsh_chain_vk.h, which vk_android_build.py refuses to run without)"""
    m = re.search(r'#define DMSH_VK_CHAIN "([0-9a-f]+)"', (REPO / 'dm/gfx/nvn/dmsh_chain_vk.h').read_text())
    return m.group(1) if m else ''


def load_modules():
    sys.path.insert(0, str(TOOLS)); sys.path.insert(0, str(REPO / 'dm/games'))
    import game, dmsh_chain, vk_android_delta   # noqa: E401
    return game, dmsh_chain, vk_android_delta


def zstd_module():
    try: from compression import zstd   # Python 3.14 (the phone's)
    except ImportError:
        try: from backports import zstd   # pyright: ignore[reportMissingImports]
        except ImportError: zstd = None
    return zstd


_BORROWED = {}


def borrowed(*names):
    """Functions of dmsh_build.py by name, taken the way vk_android_build.py borrows them, so the pairs are the ones the build reads"""
    if names in _BORROWED: return _BORROWED[names]
    import ast
    src = (TOOLS / 'dmsh_build.py').read_text()
    ns = {'os': os, 'collections': collections}
    exec(compile(ast.Module(body=[n for n in ast.parse(src).body if isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), 'dmsh_build.py', 'exec'), ns)
    _BORROWED[names] = out = [ns[n] for n in names]
    return out


def rows(path):
    """A table's rows: no blank line, no comment"""
    try: text = Path(path).read_text(encoding='utf-8')
    except FileNotFoundError: return []
    return [l for l in text.split('\n') if l.strip() and not l.lstrip().startswith('#')]


class Deps:
    """Each shader's part of the profile (see the head of this file) → its digest. g: the game profile, pkg: the split's package folder (the pairs tables'
    fallback, as load_pairs takes it), version: DMSH_VK_CHAIN"""

    def __init__(self, g, pkg, version, profile_rows):
        self.stage = {}
        for l in rows(g.table('shader_map.tsv')):
            c = l.split('\t')
            if len(c) > 4 and c[0].endswith('.bin'): self.stage.setdefault(c[3], c[0][:2])   # the package's name gives the stage, as in vk_android_build.py
        self.facts = {}
        for l in rows(g.table('shader_facts.tsv')):
            k, v = l.split('\t', 1)
            self.facts[k] = json.loads(v)
        load_pairs, = borrowed('load_pairs')
        self.pairs = {k: sorted(v) for k, v in load_pairs(g, pkg).items()}
        self.vs_of = collections.defaultdict(set)
        for v, ps in self.pairs.items():
            for p in ps: self.vs_of[p].add(v)
        self.mesh_of = collections.defaultdict(set)   # the mesh programs a pixel shader is drawn with (their facts' "pair"): MESH_PRIM in vk_android_build.py
        for k, f in self.facts.items():
            for p in f.get('pair', ()) if isinstance(f, dict) else ():
                self.mesh_of[p].add(k)
        self.dual = {l.split()[0] for l in rows(g.table('dual_want.tsv'))}
        self.bf = {l.strip() for l in rows(g.table('bf_want.tsv'))}
        self.named = collections.defaultdict(list)   # rows of the tables read by key whose layout this file does not model: every row that names the key
        glob = hashlib.sha256()
        for t in ('geo_mesh.tsv', 'shader_map.tsv.conflicts.tsv'):
            for l in rows(g.table(t)):
                ks = set(KEY.findall(l))
                for k in ks: self.named[k].append(f'{t}\0{l}')
                if not ks: glob.update(f'{t}\0{l}\n'.encode())   # a row that names no key counts for every shader
        self.common = hashlib.sha256(b'%s\0%s\0profile\0%s\0rows\0%s' % (FORMAT.encode(), version.encode(), profile_rows, glob.hexdigest().encode())).hexdigest()
        self._plain, self._dep = {}, {}

    def fact_row(self, k):
        f = self.facts.get(k)
        return 'missing' if f is None else json.dumps(f, sort_keys=True, separators=(',', ':'), ensure_ascii=False)

    def plain(self, k):
        d = self._plain.get(k)
        if d is None:
            h = hashlib.sha256()
            for part in (self.common, k, self.stage.get(k, 'missing'), self.fact_row(k), ','.join(self.pairs.get(k, ())), '1' if k in self.dual else '0',
                         '1' if k in self.bf else '0', '\n'.join(sorted(self.named.get(k, ()))), ','.join(sorted(self.vs_of.get(k, ()))),
                         '\n'.join(f'{m}\t{self.fact_row(m)}' for m in sorted(self.mesh_of.get(k, ())))):
                h.update(part.encode() + b'\0')
            d = self._plain[k] = h.hexdigest()[:32]
        return d

    def digest(self, k):
        """A shader's digest; a vertex shader's takes its pixel shaders' (their inputs pad its outputs), as dmsh_build.py pair_keyed"""
        d = self._dep.get(k)
        if d is None:
            d = self.plain(k)
            if self.pairs.get(k): d = hashlib.sha256((d + '\0' + ','.join(self.plain(p) for p in self.pairs[k])).encode()).hexdigest()[:32]
            self._dep[k] = d
        return d


def library_id(L):
    """A library's name for its record: the SHA-256 of everything before its blob (header, entries, bindings, module records) and its size. Another build of the
    library has other module offsets or sizes; a library that is the same up to its blob holds the same entries, or hash_of refuses them"""
    h = hashlib.sha256(bytes(L.m[:L.blob_off]))
    h.update(str(L.size).encode())
    return h.hexdigest()[:32]


def record_path(d, lib_id): return Path(d) / 'shaders' / f'{lib_id}.tsv'


def read_record(d, lib_id):
    """{'version', 'game', 'inputs', 'deps': {key: digest}} of a library's record, None when it has none or it does not read as one"""
    if not d: return None
    try: text = record_path(d, lib_id).read_text()
    except OSError: return None
    out = {'deps': {}}
    for l in text.split('\n'):
        c = l.split('\t')
        if len(c) != 2 or l.startswith('#'): continue
        if c[0] in ('format', 'version', 'game', 'inputs'): out[c[0]] = c[1]
        elif KEY.fullmatch(c[0]) and re.fullmatch(r'[0-9a-f]{32}', c[1]): out['deps'][c[0]] = c[1]
    return out if out.get('format') == FORMAT and all(x in out for x in ('version', 'game', 'inputs')) else None


def write_record(d, lib_id, version, game, inputs, deps):
    p = record_path(d, lib_id); p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix('.tmp')
    tmp.write_text(f'# what each shader of a library was built from (dm/peanuts/reuse/shader_reuse.py): the next build keeps the shaders whose part is the same\n'
                   f'format\t{FORMAT}\nversion\t{version}\ngame\t{game}\ninputs\t{inputs}\n' + ''.join(f'{k}\t{deps[k]}\n' for k in sorted(deps)))
    os.replace(tmp, p)


def sample_class(deps):
    """A shader's class for the sample: its stage, and for a vertex shader whether pixel shaders pad its outputs"""
    return lambda k: (deps.stage.get(k), bool(deps.pairs.get(k)))


def sample_of(keys, cls=None):
    """The keys the check translates: one in SAMPLE_SHARE by a hash of the key (the same keys on every run of the same build), within SAMPLE_MIN..SAMPLE_MAX;
    with cls (sample_class), topped up to SAMPLE_CLASS_MIN of each class (all of a smaller class) in the same order"""
    order = sorted(keys, key=lambda k: hashlib.sha256(b'pn-sample\0' + k.encode()).digest())
    n = min(len(order), max(SAMPLE_MIN, min(SAMPLE_MAX, -(-len(order) // SAMPLE_SHARE))))
    out = set(order[:n])
    if cls is not None:
        classes = collections.defaultdict(list)
        for k in order: classes[cls(k)].append(k)
        for ks in classes.values():
            short = min(len(ks), SAMPLE_CLASS_MIN) - sum(1 for k in ks if k in out)
            for k in ks:
                if short <= 0: break
                if k not in out: out.add(k); short -= 1
    return out


def same_pixel_shaders(L, k, deps, wanted):
    """A vertex shader's outputs are padded with the inputs of its paired pixel shaders that its build makes (vk_android_build.py PS_INPUTS): the older library
    holds those its build made. The same set, or no pairs: True. A pixel shader that was absent then (DMSH_VK_ABSENT_FILE: an add-on not installed) and is
    built now, or the other way round, changes the vertex shader's translation while its own part of the profile stays the same"""
    ps = deps.pairs.get(k)
    if not ps: return True
    return {p for p in ps if p in L.index} == {p for p in ps if wanted is None or p in wanted}


def plan(libs, version, game_id, inputs, deps, reuse_dir, zstd, wanted=None, pairs_in_inputs=True):
    """→ (kept {key: entry hash} for the manifest, sample {key: (older library's version, its entry hash)}, [lines for the log]). libs: delta.Library objects,
    newest first; deps: Deps of this build; wanted: the keys this build makes, None for all the profile maps; pairs_in_inputs: the profile has its own
    pso_pairs.tsv (else load_pairs takes the split's, which the inputs digest does not cover, and "same inputs" does not hold)"""
    kept, sample, lines, taken = {}, {}, [], set()
    for L in libs:
        if L.version != version or L.game != game_id:
            lines.append(f'reuse: {L.path} is of version {L.version}, game {L.game}: not this build\'s'); continue
        same = L.inputs == inputs and pairs_in_inputs
        rec = None if same else read_record(reuse_dir, library_id(L))
        if not same and (rec is None or (rec['version'], rec['game']) != (version, game_id)):
            lines.append(f'reuse: {L.path} was built from the same profile inputs, but the profile has no pso_pairs.tsv of its own (the split\'s pairs pad the vertex '
                         f'shaders, and the inputs digest does not cover them), and there is no record of its shaders\' parts: none kept from it' if L.inputs == inputs else
                         f'reuse: {L.path} was built from other profile inputs ({L.inputs}, this build {inputs}) and has no record of its shaders\' parts: none kept from it')
            continue
        cand = [k for k in L.index if k not in taken and k in deps.stage and (wanted is None or k in wanted) and (same or rec['deps'].get(k) == deps.digest(k))
                and same_pixel_shaders(L, k, deps, wanted)]
        hashes, bad = {}, 0
        for k in cand:
            try: _, h, _ = L.hash_of(L.index[k], zstd)
            except Exception: bad += 1; continue   # an entry that cannot be read (damaged, no zstd here) is translated
            hashes[k] = h
        if len(hashes) < SAMPLE_MIN * 2:
            lines.append(f'reuse: {L.path}: {len(hashes)} shaders could be kept, too few to check: none kept'); continue
        s = sample_of(hashes, sample_class(deps))
        for k, h in hashes.items():
            if k in s: sample[k] = (L.version, h)
            else: kept[k] = h
        taken |= set(hashes)
        lines.append(f'reuse: {L.path}: {len(hashes) - len(s)} shaders kept ({"the same profile inputs" if same else "their own part of the profile unchanged"}), '
                     f'{len(s)} translated to check them' + (f', {bad} entries unreadable' if bad else ''))
    return kept, sample, lines


def overlay(g, reuse_dir, manifest_text):
    """A copy of the profile folder's files with the manifest in place of its own; → its path"""
    root = Path(reuse_dir) / 'profile'
    dst, tmp = root / g.id, root / (g.id + '.tmp')
    shutil.rmtree(tmp, ignore_errors=True); tmp.mkdir(parents=True)
    for f in Path(g.dir).iterdir():
        if f.is_file() and f.name != 'vk_delta.tsv': shutil.copyfile(f, tmp / f.name)
    (tmp / 'vk_delta.tsv').write_text(manifest_text)
    shutil.rmtree(dst, ignore_errors=True); os.replace(tmp, dst)
    return dst


def run_build(argv, env):
    """vk_android_build.py with argv in this process, env added to the environment; everything put back after it (scripts/pack_android.py run_in_process). → exit code"""
    saved = (sys.argv, list(sys.path), dict(os.environ), set(sys.modules), os.getcwd())
    code = 0
    try:
        os.environ.update(env); sys.argv = [str(BUILD)] + list(argv); sys.path.insert(0, str(TOOLS))
        runpy.run_path(str(BUILD), run_name='__main__')
    except SystemExit as e:
        code = 0 if e.code is None else e.code if isinstance(e.code, int) else (print(e.code, flush=True), 1)[1]
    finally:
        argv0, path, environ, mods, cwd = saved
        sys.argv, sys.path[:] = argv0, path
        os.environ.clear(); os.environ.update(environ); os.chdir(cwd)
        std = os.path.realpath(sys.base_prefix) + os.sep
        for m in set(sys.modules) - mods:   # the tool's own modules read DM_GAME when imported: the next run imports them afresh
            if not os.path.realpath(getattr(sys.modules.get(m), '__file__', None) or '').startswith(std): sys.modules.pop(m, None)
    return code


def older_libraries(delta, out_lib_dir, game_id):
    """The libraries of DMSH_VK_DELTA_FROM (as vk_android_build.py older_libraries reads it), newest first, without this build's own output"""
    files = []
    for r in [p for p in os.environ.get('DMSH_VK_DELTA_FROM', '').split(os.pathsep) if p]:
        if os.path.isfile(r): files.append(r); continue
        if os.path.isfile(os.path.join(r, 'dmsh_vk.lib')): files.append(os.path.join(r, 'dmsh_vk.lib')); continue
        try: subs = os.listdir(r)
        except OSError: continue
        files += [os.path.join(r, n, 'dmsh_vk.lib') for n in subs if n.startswith('v-') and os.path.isfile(os.path.join(r, n, 'dmsh_vk.lib'))]
    libs = []
    for f in sorted(set(os.path.realpath(f) for f in files)):
        if os.path.dirname(f) == os.path.realpath(out_lib_dir): continue
        try: L = delta.Library(f)
        except Exception as e: say(f'reuse: {f} skipped: {e}'); continue
        if L.game != game_id: L.close(); continue
        libs.append(L)
    return sorted(libs, key=lambda L: -os.stat(L.path).st_mtime)


def wanted_keys(argv, deps, delta):
    """The keys this build makes, as vk_android_build.py takes them: the key list's (none: every key of the map) with the pixel shaders paired with them
    (dmsh_build.py build_keys), less the absences validated against the split (DMSH_VK_ABSENT_FILE, vk_android_delta.py package_keys). None when that cannot be
    told (an absence file that cannot be read, an empty selection: the tool says why), and then nothing is kept"""
    absent = set()
    if f := os.environ.get('DMSH_VK_ABSENT_FILE'):
        try: absent = set(Path(f).read_text(encoding='utf-8').splitlines())
        except OSError: return None
    build_keys, = borrowed('build_keys')
    loc = dict.fromkeys(deps.stage)
    try:
        if hasattr(delta, 'package_keys'): return set(delta.package_keys(set(argv[4:]), loc, deps.pairs, absent, build_keys))
        return set(build_keys(set(argv[4:]), loc, deps.pairs))
    except ValueError: return None


def check(new_lib, sample, zstd, delta):
    """→ (compared, [keys whose translated entry differs]) for the sample against the library just written"""
    L = delta.Library(new_lib)
    try:
        compared, differ = 0, []
        for k, (_, h) in sorted(sample.items()):
            i = L.index.get(k)
            if i is None: continue
            try: _, nh, _ = L.hash_of(i, zstd)
            except Exception: continue
            compared += 1
            if nh != h: differ.append(k)
        return compared, differ
    finally: L.close()


MARK = '.pn-reuse-unchecked'   # in the results folder: the keys a run kept before its check ran; a stop before the check leaves it


def results_dir(out_dir): return os.environ.get('DMSH_VK_RESULTS') or os.path.join(out_dir, 'results')


def drop_unchecked(res):
    """The kept results of a run that stopped before its check (MARK) are deleted: they are written again (seconds) if this run keeps them, and never used unchecked"""
    p = os.path.join(res, MARK)
    try: keys = json.loads(Path(p).read_text())
    except (OSError, ValueError): keys = None
    if keys is None and not os.path.exists(p): return
    for k in keys or []:
        if isinstance(k, str) and KEY.fullmatch(k):
            try: os.unlink(os.path.join(res, k + '.r'))
            except OSError: pass
    try: os.unlink(p)
    except OSError: pass
    say(f'reuse: {len(keys or [])} shaders kept by a run that stopped before its check: deleted (kept again or translated now)')


def main(argv):
    if os.environ.get('PN_REUSE') == '0' or len(argv) < 4 or argv[:2] != ['--target', 'vulkan']:
        if os.environ.get('PN_REUSE') == '0':
            say('reuse: off (PN_REUSE=0)')
            if len(argv) >= 4: drop_unchecked(results_dir(os.path.join(argv[3], 'v-' + vk_version())))
        return run_build(argv, {})
    game, dmsh_chain, delta = load_modules()
    g = game.load()
    version, inputs, reuse_dir, zstd = vk_version(), dmsh_chain.inputs(g), os.environ.get('PN_REUSE_DIR', ''), zstd_module()
    out_dir = os.path.join(argv[3], 'v-' + version)
    res = results_dir(out_dir)
    drop_unchecked(res)
    deps = Deps(g, argv[2], version, dmsh_chain.profile_rows(g))
    own = g.table(delta.MANIFEST)
    try: m = delta.parse_manifest(own.read_text())
    except (OSError, ValueError): m = None
    kept, sample = {}, {}
    if m and (m['version'], m['game'], m['inputs']) == (version, g.id, inputs):
        say('reuse: the profile\'s own manifest is of this build: upstream\'s delta update')
        code = run_build(argv, {})
    else:
        libs = older_libraries(delta, out_dir, g.id)
        wanted = wanted_keys(argv, deps, delta)
        try:
            if wanted is None: kept, sample, lines = {}, {}, ['reuse: this build\'s keys cannot be told (its absences, its selection): none kept']
            else: kept, sample, lines = plan(libs, version, g.id, inputs, deps, reuse_dir, zstd, wanted, g.table('pso_pairs.tsv').is_file())
        finally:
            for L in libs: L.close()
        for l in lines: say(l)
        prof = None
        if kept and reuse_dir:
            try:
                prof = overlay(g, reuse_dir, delta.format_manifest(version, g.id, inputs, kept))
                if dmsh_chain.inputs(game.Game(prof)) != inputs: say('[RED] reuse: the profile copy is not the profile: none kept'); prof = None
            except (OSError, ValueError) as e: say(f'[RED] reuse: cannot write the profile copy ({e}): none kept'); prof = None
        elif kept: say('reuse: no PN_REUSE_DIR for the profile copy: none kept')
        if prof is None: kept, sample = {}, {}
        if kept:
            os.makedirs(res, exist_ok=True)
            tmp = os.path.join(res, MARK + '.tmp'); Path(tmp).write_text(json.dumps(sorted(kept))); os.replace(tmp, os.path.join(res, MARK))
        code = run_build(argv, {'DM_GAME': str(prof)} if prof else {})
        if code != 0 and kept: drop_unchecked(res)   # a failed step: its kept results are not left for a Retry that may not check them
        if code == 0 and kept:
            compared, differ = check(os.path.join(out_dir, 'dmsh_vk.lib'), sample, zstd, delta)
            if differ or compared * 2 < len(sample):
                why = (f'{len(differ)} of {compared} shaders translated to check differ from the older library ({", ".join(differ[:3])})' if differ
                       else f'only {compared} of the {len(sample)} shaders translated to check are in the library')
                say(f'[RED] reuse check: {why}: the {len(kept)} kept shaders are translated again')
                drop_unchecked(res)
                if reuse_dir: shutil.rmtree(Path(reuse_dir) / 'shaders', ignore_errors=True)   # a record that led here is not trusted again
                kept = {}
                code = run_build(argv, {})
            else:
                try: os.unlink(os.path.join(res, MARK))
                except OSError: pass
                say(f'reuse check: {compared} of {compared} shaders translated to check are the older library\'s byte for byte; {len(kept)} shaders kept')
        if reuse_dir: shutil.rmtree(Path(reuse_dir) / 'profile', ignore_errors=True)
    if code == 0 and reuse_dir:
        try:
            L = delta.Library(os.path.join(out_dir, 'dmsh_vk.lib'))
            try: write_record(reuse_dir, library_id(L), version, g.id, inputs, {k: deps.digest(k) for k in L.index if k in deps.stage})
            finally: L.close()
        except Exception as e: say(f'reuse: no record of this library\'s shaders ({e}): the next build keeps only what the same inputs allow')
    return code


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
