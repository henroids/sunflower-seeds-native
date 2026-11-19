<!-- PEANUTS: Android bring-up profile. -->
# DRAGON BALL Z: KAKAROT + A NEW POWER AWAKENS SET

## Build and content

- Title `010051C0134F8000`, update 1.70 (`v1048576`), main
  `e99dffa39354b6068f8e7c984c77bc84`, SDK 9.3.1, Unreal Engine 4 on NVN.
- The verified input list includes add-ons `010051C0134F9001` through
  `010051C0134F9014`. Device mounts and in-game entitlement checks are pending.
- The full PC scan with the Oodle worker found 75,430 shaders and 9,650
  vertex/pixel variation pairs. Two unencrypted version 8 Unreal paks
  contain the cooked packages that hold the programs. The reader handles
  Zlib and the Oodle-compressed streams it encounters inside those packages.
  Scanning just those two paks produced the same shader tables and library
  as the full scan.

## Timing

The exact-build FPSLocker rows set `t.MaxFPS`, clear `r.VSync`, and
adjust the dynamic-resolution frame budget. The cutscene setting clears
Unreal's fixed-rate bit while retaining its other flags. Game owned skips
these writes. Actual caps, transition timing and gameplay speed require
measurement on Thor. [FPSLocker Warehouse 1.70 configuration](https://github.com/masagrator/FPSLocker-Warehouse/blob/v4/SaltySD/plugins/FPSLocker/patches/010051C0134F8000/E99DFFA39354B606.yaml)

## Open

Thor startup, gameplay, DLC entitlement, graphics, audio, and cold shader
preparation checks are pending.

## Found on this game

No device findings yet.
