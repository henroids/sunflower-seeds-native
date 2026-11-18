# Tomodachi Life: Living the Dream (`010051F0207B2000`)

Update 1.0.4 (v262144), SDK 20, NVN. `main` and `sdk` are the only modules. The PC shader build produced 14,469 Vulkan shaders.

## What it needs

| Setting | Where | Why |
|---|---|---|
| `mem_used 0x6200000` (98 MiB) | [`game.tsv`](game.tsv) | The rewritten modules occupy 98 MiB. At the default reported usage of 64 MiB, the SDK requests a `0xc9400000` heap that the remaining pool cannot fit. Boot fails after the rejected `SetHeapSize`. |

## Android status

The AYN Thor with Turnip Gen8 V39 reached first-run setup at 30 fps without a heap override. Experimental display-paced caps offer 20, 30, 40, 45, 60, 90 and 120 fps, with Game owned as the default. At the highest cap, first-run setup reached about 119 fps and the interactive island tutorial ran at roughly 67–72 fps. Gameplay speed at the experimental caps is unverified.

Android bundles 361 Vulkan pipeline keys recorded on Thor. With Turnip Gen8 V39 and an empty cache, “Preparing shaders” built 289 pipelines in 4.9 seconds. The next launch reused the cache and skipped the progress screen. [Cold startup](../../../docs/reference/rendering.md#a-draw-skipped-for-its-pipeline-left-a-result-the-game-kept-android)
