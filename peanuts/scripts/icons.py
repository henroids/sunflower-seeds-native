#!/usr/bin/env python3
"""The Peanuts icons: the one source of the launcher's android/app/src/peanuts/res/drawable/pi_*.xml.

Every icon is a shell with at most one kernel, like the nut, on a 24 x 24 grid. An icon is a list of layers, each (kind, path data):

  "s"  shell   stroke 1.75 wide, round caps and joins, no fill, in the ink colour (?attr/pnIconInk)
  "k"  kernel  filled with the kernel colour (?attr/pnIconKernel, the accent): the seed inside the shell, kept clear of the stroke.
               One idea per icon; repeated seeds (the list's bullets) count as one. Arrows and marks have none.
  "f"  fill    filled with the ink: dots that stay neutral (a console's buttons, a launcher's row of apps)

Only path data is used (no circle or rect elements), so the strings are VectorDrawable pathData as they are. Ink and kernel come from the
theme, so one file serves the light and the dark theme; Drawable.setTint() on a filled button turns the whole icon one colour.

  python3 peanuts/scripts/icons.py           writes every pi_<name>.xml and removes a pi_*.xml no icon here names
  python3 peanuts/scripts/icons.py --check   writes nothing; exit 1 when a file differs from what this would write (peanuts/tests/test_icons.py)

A new icon: add its layers below, run the script, and use it by name (dev.peanuts.ui.Icons.get, SettingsSections).
"""

import pathlib
import sys

STROKE = 1.75
REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / 'android/app/src/peanuts/res/drawable'
HEAD = '<!-- Peanuts icon, written by peanuts/scripts/icons.py. Change it there and run the script, not here. -->'


def circle(cx, cy, r):
    return f"M{cx - r:g} {cy:g}a{r:g} {r:g} 0 1 0 {2 * r:g} 0a{r:g} {r:g} 0 1 0 {-2 * r:g} 0Z"


def rrect(x, y, w, h, r):
    return (f"M{x + r:g} {y:g}H{x + w - r:g}A{r:g} {r:g} 0 0 1 {x + w:g} {y + r:g}V{y + h - r:g}"
            f"A{r:g} {r:g} 0 0 1 {x + w - r:g} {y + h:g}H{x + r:g}A{r:g} {r:g} 0 0 1 {x:g} {y + h - r:g}"
            f"V{y + r:g}A{r:g} {r:g} 0 0 1 {x + r:g} {y:g}Z")


# The peanut: two lobes and a waist. The brand outline.
PEANUT = ("M12 2.5C15 2.5 17 4.6 17 7.2C17 9 16 10.2 15.4 11.2C15 11.9 15 12.4 15.5 13.2"
          "C16.4 14.4 17.6 15.6 17.6 17C17.6 20 15.2 21.5 12 21.5C8.8 21.5 6.4 20 6.4 17"
          "C6.4 15.6 7.6 14.4 8.5 13.2C9 12.4 9 11.9 8.6 11.2C8 10.2 7 9 7 7.2C7 4.6 9 2.5 12 2.5Z")
SEED_TOP = "M12 4.6c1.6 0 2.7 1.1 2.7 2.6S13.6 9.9 12 9.9 9.3 8.7 9.3 7.2 10.4 4.6 12 4.6Z"
SEED_BOTTOM = "M12 14.1c1.8 0 3.1 1.2 3.1 2.8s-1.3 2.8-3.1 2.8-3.1-1.2-3.1-2.8 1.3-2.8 3.1-2.8Z"
CONTROLLER = ("M7 7h10c2.8 0 4.5 2.5 4.5 6v2.5c0 2.3-1.5 3.5-3 3.5-1.5 0-2.3-1-3-2l-.7-1H9.2l-.7 1"
              "c-.7 1-1.5 2-3 2S2.5 17.8 2.5 15.5V13C2.5 9.5 4.2 7 7 7Z")
# A device with two screens, the top one wider (the companion, Screens)
TOP_SCREEN = rrect(2.5, 3, 19, 8.5, 2.5)
BOTTOM_SCREEN = rrect(4.5, 13.5, 15, 7.5, 2.5)

ICONS = {
    # Marks: shells only
    "check": [("s", "M5 12.5l5 5 9-10.5")],
    "close": [("s", "M6.5 6.5l11 11M17.5 6.5l-11 11")],
    "external": [("s", "M14 4.5h5.5V10M19.5 4.5 11 13M17 14v4a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 18V8.5A1.5 1.5 0 0 1 6 7h4")],
    "menu": [("s", "M4 6.5h16M4 12h16M4 17.5h16")],

    # Library
    "library": [("s", rrect(3.5, 3.5, 7.5, 7.5, 2)), ("k", rrect(5.75, 5.75, 3, 3, 0.8)), ("s", rrect(13, 3.5, 7.5, 7.5, 2)),
                ("s", rrect(3.5, 13, 7.5, 7.5, 2)), ("s", rrect(13, 13, 7.5, 7.5, 2))],
    "list": [("s", "M10 6.5H20M10 12H20M10 17.5H20"), ("k", circle(5.5, 6.5, 1.7)), ("k", circle(5.5, 12, 1.7)), ("k", circle(5.5, 17.5, 1.7))],
    "search": [("s", circle(10.5, 10.5, 6)), ("s", "M15 15l5 5"), ("k", circle(10.5, 10.5, 2.4))],
    "game": [("s", rrect(5.5, 3.5, 13, 17, 2.5)), ("k", rrect(8.5, 6.75, 7, 6.5, 1.2)), ("s", "M9.5 17h5")],
    "folder": [("s", "M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2Z"), ("k", rrect(6.5, 11, 11, 5, 1.2))],
    "storage": [("s", "M9 3.5h8a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-11Z"),
                ("k", rrect(9.25, 6.5, 1.5, 3.5, 0.75)), ("k", rrect(12.25, 6.5, 1.5, 3.5, 0.75)), ("k", rrect(15.25, 6.5, 1.5, 3.5, 0.75))],

    # Actions
    "play": [("s", "M8 5.3 18.5 12 8 18.7Z"), ("k", "M10.6 9.6 14.4 12l-3.8 2.4Z")],
    "build": [("k", "M13.1 6.6 17.4 10.9 16.3 12 12 7.7Z"), ("s", "M12.73 4.91 19.09 11.27 16.27 14.09 9.91 7.73Z"),
              ("s", "M12.9 11.1 4.8 19.2")],
    "update": [("s", "M19.5 12A7.5 7.5 0 1 1 15.6 5.4"), ("k", "M13.4 2.3 18.6 5.6 13.6 8.6Z")],
    "download": [("s", "M12 3.5V15M7.5 10.5 12 15l4.5-4.5M5 19.5h14")],
    "share": [("s", "M12 3.5V14M8 7.5l4-4 4 4M5 11v7.5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V11")],
    "trash": [("s", "M4.5 6.5h15M9.5 6.5v-2h5v2M6.5 6.5l1 12.5a1.5 1.5 0 0 0 1.5 1.5h6a1.5 1.5 0 0 0 1.5-1.5l1-12.5M10.5 10v6.5M13.5 10v6.5")],
    "settings": [("s", "M4 6h8.2M17.8 6H20M4 12h2.2M11.8 12H20M4 18h6.2M15.8 18H20"),
                 ("s", circle(15, 6, 2.6)), ("k", circle(15, 6, 1.1)), ("s", circle(9, 12, 2.6)), ("k", circle(9, 12, 1.1)),
                 ("s", circle(13, 18, 2.6)), ("k", circle(13, 18, 1.1))],

    # Game content
    "dlc": [("k", "M12 5.6 16.8 8 12 10.4 7.2 8Z"), ("s", "M12 3.5 20.5 8 12 12.5 3.5 8Z"), ("s", "M3.5 12 12 16.5 20.5 12M3.5 16 12 20.5 20.5 16")],
    "companion": [("s", TOP_SCREEN), ("s", BOTTOM_SCREEN), ("k", rrect(7.25, 15.75, 9.5, 3, 1))],
    "peanut": [("s", PEANUT), ("k", SEED_TOP), ("k", SEED_BOTTOM)],

    # System
    "controller": [("s", CONTROLLER), ("s", "M7.5 10.5v4M5.5 12.5h4"), ("k", circle(16, 11.3, 1.25)), ("k", circle(18.2, 13.5, 1.25))],
    "driver": [("s", rrect(6.5, 6.5, 11, 11, 2.5)), ("k", rrect(9.5, 9.5, 5, 5, 1.2)),
               ("s", "M9.5 3.5v3M14.5 3.5v3M9.5 17.5v3M14.5 17.5v3M3.5 9.5h3M3.5 14.5h3M17.5 9.5h3M17.5 14.5h3")],
    "key": [("s", circle(8, 15.5, 4.5)), ("k", circle(8, 15.5, 1.8)), ("s", "M11.2 12.3 19.5 4M16.3 7.2l2.2 2.2M14 9.5l1.6 1.6")],
    "gauge": [("s", "M4.5 16.5a8 8 0 1 1 15 0M12 14l4-4.5"), ("k", circle(12, 14, 2))],
    "sleep": [("s", "M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10Z"), ("k", "M8.9 9.4a4.6 4.6 0 0 0 5.9 7.1 5.6 5.6 0 0 1-5.9-7.1Z")],
    "awake": [("s", circle(12, 12, 4.5)), ("k", circle(12, 12, 2)),
              ("s", "M12 2.75v2M12 19.25v2M2.75 12h2M19.25 12h2M5.45 5.45l1.4 1.4M17.15 17.15l1.4 1.4M5.45 18.55l1.4-1.4M17.15 6.85l1.4-1.4")],
    "clock": [("s", circle(12, 12, 8.5)), ("s", "M12 7.5V12l3 2"), ("k", circle(12, 12, 1.5))],
    "user": [("s", circle(12, 8.5, 4)), ("s", "M4.5 20.5c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5"), ("k", circle(12, 8.5, 1.5))],

    # The Mii editor (Users with Miis): a Mii, flip, random (a die) and undo
    "mii": [("s", circle(12, 12, 8)), ("s", "M6.6 9.2c1.6-2.8 3.9-4 6.4-4 2.3 0 4.2 1 5 2.6"), ("k", circle(9.5, 12.5, 1.2) + circle(14.5, 12.5, 1.2)),
            ("s", "M10 16q2 1.4 4 0")],
    "flip": [("s", "M4 8h13.5M14 4.5 17.5 8 14 11.5M20 16H6.5M10 12.5 6.5 16l3.5 3.5"), ("k", circle(12, 8, 1.2))],
    "random": [("s", rrect(4, 4, 16, 16, 3.5)), ("k", circle(8.6, 8.6, 1.35)), ("k", circle(15.4, 8.6, 1.35)), ("k", circle(12, 12, 1.35)),
               ("k", circle(8.6, 15.4, 1.35)), ("k", circle(15.4, 15.4, 1.35))],
    "undo": [("s", "M9 4.5 4.5 9 9 13.5M4.5 9H14a5.5 5.5 0 0 1 0 11H10")],

    # Console mode: the console with its two controllers (handheld), the console in its dock at a TV (docked)
    "console": [("s", rrect(1.5, 6.75, 21, 10.5, 3.25)), ("s", "M6.5 6.75V17.25M17.5 6.75V17.25"), ("k", rrect(8.75, 9, 6.5, 6, 1)),
                ("f", circle(4, 10.3, 1.2)), ("f", circle(20, 13.7, 1.2))],
    "docked": [("s", rrect(2.5, 4, 19, 12.5, 2.5)), ("k", rrect(5.5, 7, 13, 6.5, 1.2)), ("s", "M8.5 20.5h7M12 16.5v4")],

    # Screens: what the second screen shows (Android's apps, the game's cover), where the game plays after a swap, how the screen turns
    "screens_android": [("s", TOP_SCREEN), ("s", BOTTOM_SCREEN), ("f", circle(8.25, 17.25, 0.95)), ("f", circle(10.75, 17.25, 0.95)),
                        ("f", circle(13.25, 17.25, 0.95)), ("f", circle(15.75, 17.25, 0.95))],
    "screens_cover": [("s", TOP_SCREEN), ("s", BOTTOM_SCREEN), ("k", rrect(10.25, 15.1, 3.5, 4.3, 0.7))],
    "swap_off": [("s", TOP_SCREEN), ("s", BOTTOM_SCREEN), ("k", "M10.25 4.9 14.5 7.25 10.25 9.6Z")],
    "swap_on": [("s", TOP_SCREEN), ("s", BOTTOM_SCREEN), ("k", "M10.5 15.1 14.25 17.25 10.5 19.4Z")],
    "screen_landscape": [("s", rrect(2.5, 6.5, 19, 11, 2.5)), ("k", rrect(5.5, 9.25, 11, 5.5, 1.2)), ("s", "M19 10.5v3")],
    "screen_rotate": [("s", rrect(8.5, 4, 7, 16, 2)), ("k", rrect(10.5, 7, 3, 7.5, 0.8)), ("s", "M11 17.25h2"),
                      ("s", "M3.77 16.75A9.5 9.5 0 0 1 5.28 5.28M2.68 5.28H5.28V7.88"),
                      ("s", "M20.23 7.25A9.5 9.5 0 0 1 18.72 18.72M21.32 18.72H18.72V16.12")],

    # Status: the kernel takes the status colour instead of the accent
    "info": [("s", circle(12, 12, 8.5)), ("s", "M12 11v5.5"), ("k", circle(12, 7.9, 1.3))],

    # Screens: a TV on its stand, the play mark as the kernel (Settings › Screens › TV and monitors, #407)
    "tv": [("s", "M5 4H19A2.5 2.5 0 0 1 21.5 6.5V14A2.5 2.5 0 0 1 19 16.5H5A2.5 2.5 0 0 1 2.5 14V6.5A2.5 2.5 0 0 1 5 4Z"),
           ("s", "M8.5 20.5h7M12 16.5v4"), ("k", "M10.4 8.1 14.4 10.25 10.4 12.4Z")],
}


def vector(layers):
    rows = []
    for kind, d in layers:
        if kind == "s":
            rows.append(f'    <path android:pathData="{d}" android:strokeColor="?attr/pnIconInk" android:strokeWidth="{STROKE}"\n'
                        f'        android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>')
        elif kind == "k":
            rows.append(f'    <path android:pathData="{d}" android:fillColor="?attr/pnIconKernel"/>')
        elif kind == "f":
            rows.append(f'    <path android:pathData="{d}" android:fillColor="?attr/pnIconInk"/>')
        else:
            raise ValueError(f"a layer is s, k or f, not {kind!r}")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            + HEAD + '\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            + "\n".join(rows) + "\n</vector>\n")


def files():
    """pi_<name>.xml -> its text, for every icon"""
    return {f"pi_{name}.xml": vector(layers) for name, layers in ICONS.items()}


def main(argv):
    check = argv[1:] == ["--check"]
    if argv[1:] and not check:
        raise SystemExit("usage: icons.py [--check]")
    want = files()
    have = {p.name for p in OUT.glob("pi_*.xml")}
    wrong = sorted(n for n, text in want.items() if not (OUT / n).is_file() or (OUT / n).read_bytes() != text.encode("utf-8"))
    extra = sorted(have - set(want))
    if check:
        for n in wrong:
            print(f"{n}: not what icons.py writes (run peanuts/scripts/icons.py)")
        for n in extra:
            print(f"{n}: no icon in icons.py names it")
        return 1 if wrong or extra else 0
    for n in wrong:
        (OUT / n).write_text(want[n], encoding="utf-8", newline="\n")
    for n in extra:
        (OUT / n).unlink()
    print(f"{len(want)} icons, {len(wrong)} written, {len(extra)} removed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
