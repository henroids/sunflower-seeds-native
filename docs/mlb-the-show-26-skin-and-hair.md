# MLB The Show 26: how the black skin and hair were fixed

This is the account of the main problem this fork was made for: in MLB The Show 26, players made in the game
(create-a-player, Road to the Show) had black faces, black skin and hair without colour. It covers what was seen, how
each cause was found, the fixes, and the wrong turns. The short, current facts are in the game's note,
[dm/games/0100AA7024094000/README.md](../dm/games/0100AA7024094000/README.md), and the general mechanisms in
[docs/reference/nvn-objects.md](reference/nvn-objects.md). Version numbers are this fork's releases: `0.2.x` is the test
app, `1.0.x` the official app.

The work was done by an AI coding assistant directed by the project's owner, who tested every build on an AYN Thor
and sent back logs and screenshots (see [AI use](../README.md#ai-use)).

## What was wrong, in one paragraph

On the Switch, a texture is a view of a piece of memory. A game can draw into memory through one texture object and
read the same memory through another, of another format or created later, and it sees what was drawn, because there
is only one memory. This port replays the game's graphics on Vulkan, where each texture object gets an image of its
own, and what the GPU draws stays in that image: the host does not copy it back into the game's memory. So every
"draw through one texture object, read through another" breaks unless the host carries the content across. All four
problems below were cases of that, each on a different path: a draw reading compressed blocks, the CPU reading a
texture back, a compute pass sampling a texture, and a texture object created after the first one was gone.

## How the game colours a generated player

Worked out along the way, from the logs and from the game's code and shaders:

1. At boot the game draws a **skin tone palette** on the GPU (`CAP_SkinTonePalette`, a 256x512 RGBA8 texture).
2. For a **face**, the CPU reads that palette back (`nvnTextureReadTexels`), picks the player's tone from it, and passes
   it to the shader that composes the face texture (`SPRITE` 5085e20953791a0e), which recolours the base skin to the
   target tone. The face is then compressed to BC1/BC7 on the GPU (`TEXTURECOMPRESSBCN` compute, writing through an
   RG32UI view of the same memory).
3. For the **body** (arms, hands, neck), a compute pass (`PLAYERCOMPUTESHADERS` 67d5da22e65373bf) averages the palette
   on the GPU and writes the result straight into each body material's constants: a mean and a target tone
   (`ICE_SKINSHADER` fp_c4[1] and fp_c4[2]). The body shader divides by the mean and multiplies by the target, and turns
   anything invalid into black.
4. **Hair** strands are coloured by another compute pass (`HAIRCOMPUTE` 8cbd9b4be32e966d) that samples the
   GPU-compressed hair colour texture.

Real players (scanned faces) use different inputs for most of this, which is why exhibitions with real teams mostly
looked right while generated players did not.

## The four causes and their fixes

| Seen | Cause | Fix | Version |
|---|---|---|---|
| Create-a-player model untextured | Its textures are compressed on the GPU through a view of another format over the same memory; the sampled BC texture got an empty image | The "storage take": before a draw, the newest GPU write through another format over a texture's memory is copied into its image, compressed targets included | 0.2.20 |
| Generated faces black | The CPU's read of the palette (`ReadTexels`) read the game's memory, which the GPU never wrote on the host | `mtl_read_texels` reads the GPU-written image over the memory when the texture has none of its own, unless the memory itself holds data (then the CPU wrote it later) | 0.2.29 |
| Hair tips and beards without colour | The storage take ran before draws only, not before compute dispatches | The same take before each dispatch | 0.2.30 |
| Body skin black (faces right in the editor, arms and hands black but shiny) on a loaded career | The palette's image was freed two frames after its texture object was finalized; the averaging pass read it through a texture object made later, which got a new, empty image | Small GPU-drawn images stay alive for as long as their memory pool does (within a 64 MiB budget); a later texture object over the same memory takes them over unless the CPU has since filled that memory | 1.0.24 |

The code is in `dm/gfx/vk_android/vka_tex.c` (`take_src`, `vka_tex_id_take`, `mtl_read_texels`, `linger_*`) and
`vka_draw.c` (`vka_dispatch`). Each fix is general, not a rule for this game: any game that draws through one texture
object and reads through another benefits.

## How each cause was found

### The method that worked

Every cause was found the same way, once the work got onto that track:

1. **Measure at the end of the chain.** Log what the broken draw actually receives: its textures (`DM_VK_TEX_STATS`
   scans every input texture of a named shader and says whether it is empty) and its constants (`DM_VK_UB_PEEK`). This
   turns "the face is black" into "the target tone constant is 0, 0, 0".
2. **Find who writes that value, then walk backwards one step at a time.** A CPU store is found with `DM_PROBE_WATCH`
   (a watchpoint with a backtrace), a GPU write with `DM_NVN_WRITE_LOG` (which compute passes bind that memory) or with
   `DM_VK_TEX_STATS`'s storage history (which images the GPU wrote over a texture's memory).
3. **Read the code that writes it.** The game's shaders were read back from the app's own shader library as SPIR-V and
   decompiled with `spirv-cross`; the game's CPU code was disassembled with Capstone from the user's own rewritten
   `main`. That is how the averaging pass and the palette read were understood, rather than guessed.
4. **Compare a run that looks right with one that does not, on the same build.** The final cause was found by putting
   a log of an exhibition (skin right) beside a loaded career (skin black): the same averaging pass read a full palette
   in one and an empty one in the other.

### The wrong turns

These cost the most time, and are worth knowing so they are not repeated:

- **Lighting (about 15 test builds, 0.2.21 to 0.2.28).** The faces looked unlit, and the menus' irradiance cube and
  light probe volumes were indeed empty. Following the game's lighting code showed that this is the game's own state in
  the menus (its light bank points at a placeholder set with no lights); stadiums have hundreds of lights. The real
  clue was elsewhere: the face's albedo texture itself was about 30 times too dark. Lesson: check the colour going in
  before the light falling on it.
- **"Background pipelines" (1.0.6).** After a fresh install the skin was black again, and the first explanation was
  that one-time drawing was skipped while shaders compiled in the background. The setting was turned off by default
  for this game. The next log showed skin black with it off too: it was not the cause.
- **Fixes shipped on a guess (1.0.7, 1.0.9, 1.0.10, 1.0.19).** Several builds changed behaviour on a plausible theory
  before a log showed the theory to be true. None of them applied in the player's runs. They were removed again in the
  clean-up, except the `ReadTexels` rule that memory holding data is newer than an aliasing GPU image, which matches the
  final fix's rule.
- **"New career versus loaded career".** For a while the difference looked like one of game data. It was one of timing:
  a new career happened to read the palette while its image was still alive; a loaded career read it later.
- **A tool's blind spot.** A watchpoint on the body material's constants (1.0.20) saw only the boot's initialisation
  and suggested nothing else wrote there. The memory is written by the GPU, which no CPU watchpoint sees. Lesson: when
  a value changes and no CPU store explains it, look for a GPU writer.
- **A probe that crashed the game (1.0.17).** A code probe was placed on a branch-and-link instruction, which a probe
  cannot run out of place, and the game crashed at boot. Probes go on instructions that do not depend on their address.

## Diagnostic switches used

These are set in the app's extra environment for a game, or by the launcher; all are documented in
[docs/env_switches.tsv](env_switches.tsv).

| Switch | What it answers |
|---|---|
| `DM_VK_TEX_STATS=<shader keys>` | For each input texture of those shaders: its format, whether its memory holds data, which images the GPU wrote over it, and a scan of its texels (how many are non-zero, the maximum per channel). Works for pixel and compute shaders |
| `DM_VK_UB_PEEK=<shader keys>` | The constant (uniform) buffers those shaders read, as the game wrote them |
| `DM_VK_RT_LOG` | The chain of render passes, with each target's content at the end of each pass |
| `DM_PROBE=main:<offset>[@reg+off]` | A log line, registers and a backtrace each time the game's code reaches that address |
| `DM_PROBE_WATCH=<address>+<length>` | Every CPU store into that memory, with the storing code and its backtrace (not GPU writes) |
| `DM_NVN_WRITE_LOG=<address>+<length>` | What writes that memory without a CPU store: inline uniform updates, GPU copies, and compute passes that bind it as a storage buffer |

## Still open

- Hair in the player editor's close-up does not look right for some styles (seen after 1.0.24, not investigated yet).
- In one session (1.0.22) every real player's skin was black in an exhibition; the next one was right. Not reproduced.
- Whether "Background pipelines" can be turned back on for this game is not tested.
