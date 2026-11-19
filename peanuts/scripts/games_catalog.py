#!/usr/bin/env python3
"""The game catalog, peanuts/games.json: every game this app version ships, with what code needs to know about it, from the sources the
repository already keeps:

- peanuts/release-titles.txt: which games ship (the release's title IDs);
- dm/games/<title ID>/game.tsv: the profile's name, its builds (build rows: the update's version number and display version), the builds
  Android plays (android_builds; all build rows without it) and the package's version;
- README.md's Supported games table: the name players know, the display version where a profile has none ("?"), and whether a companion
  exists.

    python3 peanuts/scripts/games_catalog.py          # write peanuts/games.json
    python3 peanuts/scripts/games_catalog.py --check  # exit 1 if it differs from what this script writes

A game added to or removed from release-titles.txt (with its README row) is in the catalog after the next run; .github/workflows/catalog.yml
runs this and issue_forms.py on every push to main and commits what changed. Warnings (printed, never fatal): a shipped game without a
README row or a profile, a README row of a game that does not ship.
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'peanuts/games.json'


def readme_rows(root=ROOT):
    """{title ID: (name, version cell, companion True/False/None)} of the README's Supported games table"""
    text = (root / 'README.md').read_text(encoding='utf-8')
    part = text[text.index('## Supported games'):text.index('## What you need')]
    rows = {}
    for m in re.finditer(r'^\| (.+?) \| `([0-9A-F]{16})` \| (.+?) \| (.+?) \|\s*$', part, re.M):
        comp = re.sub(r'<!--.*?-->', '', m[4]).strip().casefold()
        rows[m[2]] = (m[1].strip(), m[3].strip(), True if comp == 'yes' else False if comp == 'no' else None)
    return rows


def profile(root, title_id):
    path = root / 'dm/games' / title_id / 'game.tsv'
    if not path.is_file():
        return None
    rows = [ln.rstrip('\n').split('\t') for ln in path.read_text(encoding='utf-8').splitlines() if ln and not ln.startswith('#')]

    def values(key):
        return [r for r in rows if r[0] == key]
    name = next((r[1] for r in values('name') if len(r) > 1), None)
    builds = [(r[1].strip(), r[2].strip() if len(r) > 2 else '?') for r in values('build') if len(r) > 1]
    android = [v for r in values('android_builds') for c in r[1:] for v in c.split()]
    package = next((r[1] for r in values('package') if len(r) > 1), None)
    return {'name': name, 'builds': builds, 'android': android, 'package': package}


def number(version):
    m = re.search(r'\d+', version)
    return int(m.group()) if m else -1


def display(shown, cell, count):
    """The version players read: the profile's, else the README's when it names one version only"""
    if shown and shown != '?':
        return shown
    if count == 1 and ',' not in cell:
        if cell.lower().startswith('base game'):
            return 'base game'
        return cell if not cell.isdigit() else f'v{cell}'
    return None


def build(root=ROOT, warn=print):
    titles = [t.strip() for t in (root / 'peanuts/release-titles.txt').read_text(encoding='utf-8').split() if t.strip()]
    readme = readme_rows(root)
    version = (root / 'peanuts/VERSION').read_text(encoding='utf-8').strip()
    games = []
    for t in titles:
        p = profile(root, t)
        if p is None:
            warn(f'catalog: {t} ships (release-titles.txt) but has no profile (dm/games/{t}/game.tsv); left out')
            continue
        row = readme.get(t)
        if row is None:
            warn(f'catalog: {t} ({p["name"]}) ships but has no row in the README\'s Supported games table')
        plays = [b for b in p['builds'] if not p['android'] or b[0] in p['android']]
        plays.sort(key=lambda b: number(b[0]), reverse=True)
        cell = row[1] if row else ''
        note = root / 'dm/games' / t / 'README.md'
        games.append({
            'title_id': t,
            'name': row[0] if row else p['name'],
            'profile_name': p['name'],
            'builds': [{'version': v, 'number': number(v), 'display': display(d, cell, len(plays))} for v, d in plays],
            'package': p['package'],
            'companion': row[2] if row else None,
            'profile': f'dm/games/{t}/game.tsv',
            'note': f'dm/games/{t}/README.md' if note.is_file() else None,
        })
    for t in sorted(set(readme) - set(titles)):
        warn(f'catalog: the README lists {t} ({readme[t][0]}), which release-titles.txt does not ship')
    games.sort(key=lambda g: g['name'].casefold())
    return {'about': 'Written by peanuts/scripts/games_catalog.py from peanuts/release-titles.txt, dm/games/<title ID>/game.tsv and README.md. '
                     'Do not edit by hand.',
            'app_version': version, 'games': games}


def render(catalog):
    return json.dumps(catalog, ensure_ascii=False, indent=2) + '\n'


def main(argv, root=ROOT):
    want = render(build(root))
    out = root / 'peanuts/games.json'
    if '--check' in argv:
        if not out.is_file() or out.read_text(encoding='utf-8') != want:
            print('peanuts/games.json is out of date (run python3 peanuts/scripts/games_catalog.py)')
            return 1
        print('game catalog: up to date')
        return 0
    out.write_text(want, encoding='utf-8', newline='\n')
    print(f'game catalog: written ({len(json.loads(want)["games"])} games)')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
