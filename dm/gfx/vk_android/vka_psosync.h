// PEANUTS: which draws are not skipped while their pipeline is made in the background (DM_VK_PSO_ASYNC; vka_draw.c draw_body, vka_pso.c vka_gfx_pipeline_now), as pure
// logic, free of Android and Vulkan so a unit test on Linux can check it (dm/host/tests/t_android_psosync.c).
// With the asynchronous threads on, a draw whose pipeline the cache lacks is skipped until a background thread has made it. That costs the draw in a few frames, which
// is the point, except where the game keeps what the draw made: then the picture stays wrong after every pipeline is there. Super Mario Odyssey on an AYN Thor (Adreno
// 740, Turnip), first run (no pipeline cache, no prewarm list): black with only the HUD for the whole session, `7 % of draws skipped ... pipelines not made 11521`,
// ~650 pipelines made in the background in the first minute and still black with none waiting; right with DM_VK_PSO_ASYNC=0, and right once the cache existed.
// Which draw's result was kept is not measured; the likely ones are a post-processing history (exposure or luminance adaptation: a tiny float target blended with its
// own last value each frame, which never recovers from an undefined or non-finite start) and tables drawn once (colour grading).
// So four kinds of draw get their pipeline on the frame path, as with DM_VK_PSO_ASYNC=0, each a bit of DM_VK_PSO_SYNC_PASS (default 15; 0 = every miss skipped):
//   1  a pass over the whole target: a direct draw (not indirect, not a multi-draw, not a mesh draw) of one instance and at most DM_VK_PSO_SYNC_VERTS vertices or
//      indices (default 6: a full-screen triangle is 3, a strip 4, a quad drawn as two indexed triangles 6). Post-processing chains draw this way, and each pass reads
//      the one before, so one skipped link blanks the frame. The count is what the draw record holds, indices for an indexed draw;
//   2  a draw into a small target, at most 64x64 texels in the game's own pixels, whatever its geometry: luminance, exposure and lookup tables;
//   4  a draw whose result is memory, not a picture: its shaders write storage buffers, images or raw memory, or the rasterizer is off (transform feedback, a
//      geometry shader's writes). Nothing redraws those next frame unless the game does.
//   8  a draw into a target the game does not draw into every frame, whatever its geometry and size: a cube map or an environment probe drawn once, a shadow atlas
//      updated now and then, a texture drawn once and reused. A skipped draw there stays wrong until the game draws the target again, which may be never. A draw
//      may be skipped only when every colour and depth target of its pass is drawn every frame (vkps_mark, below); the frame time this bit may take is bounded
//      (vkps_budget).
// Everything else is skipped as before: scene geometry into the targets drawn every frame (the scene, the G-buffer, shadow maps redrawn each frame), and that is
// where the long compiles are.
//
// Which targets are drawn every frame (bit 8). A target is one level and first layer of a texture, named by its storage address, size and format (vkps_key), so a
// texture object made again over the same storage is the same target and each face of a cube map is its own. A pass that begins on a target marks it for that
// frame, whether its draws are then drawn or skipped: a target whose first draws were all skipped still becomes one drawn every frame. The target is drawn every
// frame when, before this frame, passes began on it in two earlier frames, and each of the three frames is at most DM_VK_PSO_SYNC_GAP (default 4) frames after the
// one before: the gap lets through the two to four window textures a game draws in turn, a history pair drawn on alternate frames, and a target redrawn at half
// the display's rate. So the draws of the first two frames a target is drawn in wait for their pipelines, and so do those of the first two after it was left
// alone for more than the gap; from the third frame on they are skipped as before.
// The cost: at a scene's start every target is new (or was left alone over the loading screen), so for two frames every missing pipeline would be made on the
// frame path, which is DM_VK_PSO_ASYNC=0's stall at a scene start (hundreds of pipelines of 10-200 ms each on Turnip). Hence the budget: in one frame this bit
// starts no pipeline once those it made took DM_VK_PSO_SYNC_NEW_MS (default 100; 0 = no limit), and the draws past it are skipped as before and counted. A frame
// is then at most the budget and one pipeline longer, for the two frames a target is new. What this gives up: a target drawn once whose missing pipelines
// take more than the budget is still partly skipped (the count says so); targets kept by the game are mostly drawn before the scene in a frame, so they come first.
#ifndef VKA_PSOSYNC_H
#define VKA_PSOSYNC_H
#include <stddef.h>
#include <stdint.h>

enum { VKPS_PASS = 1, VKPS_SMALL = 2, VKPS_WRITES = 4, VKPS_NEW = 8, VKPS_ALL = 15,   // DM_VK_PSO_SYNC_PASS's bits, and the reason vkps_why returns
       VKPS_VERTS = 6,                                                // DM_VK_PSO_SYNC_VERTS's default
       VKPS_SMALL_PX = 64,                                            // a small target's side at most
       VKPS_GAP = 4,                                                  // DM_VK_PSO_SYNC_GAP's default: the most frames between two frames a target drawn every frame is drawn in
       VKPS_NEW_MS = 100,                                             // DM_VK_PSO_SYNC_NEW_MS's default: the frame time bit 8 may take in one frame before it starts no more
       VKPS_SEEN_N = 1024, VKPS_SEEN_PROBE = 16 };                    // the targets remembered (a power of two), and the slots one target may lie in

// The draw as draw_body knows it where the pipeline is looked up. count: vertices, or indices of an indexed draw (not known for an indirect one); instances: 0 counts
// as 1, as in the record; w, h: the pass's target in the game's own pixels (0: not known); writes: the shaders write memory, or the rasterizer is off; fresh: a
// target of the pass is not one drawn every frame (vkps_mark returned 0 for it when the pass began)
typedef struct { int mesh, indirect, instances, writes; int64_t count; uint32_t w, h; int fresh; } vkps_draw;

// The reason this draw's pipeline is made on the frame path (one VKPS_ bit, the lowest that applies), 0 = none: the draw is skipped until the pipeline is made
static inline int vkps_why(int bits, int max_verts, const vkps_draw *d) {
    if ((bits & VKPS_PASS) && !d->mesh && !d->indirect && d->instances <= 1 && d->count >= 1 && d->count <= max_verts) return VKPS_PASS;
    if ((bits & VKPS_SMALL) && d->w && d->h && d->w <= VKPS_SMALL_PX && d->h <= VKPS_SMALL_PX) return VKPS_SMALL;
    if ((bits & VKPS_WRITES) && d->writes) return VKPS_WRITES;
    if ((bits & VKPS_NEW) && d->fresh) return VKPS_NEW;
    return 0;
}
static inline const char *vkps_name(int why) {
    return why == VKPS_PASS ? "a pass of a few vertices" : why == VKPS_SMALL ? "a small target" : why == VKPS_WRITES ? "it writes memory" : why == VKPS_NEW ? "a target not drawn every frame" : "none";
}

// ---------- Which targets are drawn every frame (bit 8) ----------
// One target: its name and the last three frames a pass began on it, newest first, each as the frame's number + 1 (0: none)
typedef struct { uint64_t key, f[3]; } vkps_tgt;
typedef struct { vkps_tgt e[VKPS_SEEN_N]; } vkps_seen;   // zero-filled = empty. A target lies in one of VKPS_SEEN_PROBE slots from its key's; a new one takes the slot marked longest ago

// A target's name, never 0. storage: the texture's storage address (its serial when it has none); w, h, fmt: the texture's own size and format; level, layer: the
// level drawn into and the first layer or slice
static inline uint64_t vkps_key(uint64_t storage, uint32_t w, uint32_t h, uint32_t fmt, uint32_t level, uint32_t layer) {
    uint64_t k = 1469598103934665603ull, v[4] = {storage, (uint64_t)w << 32 | h, (uint64_t)fmt << 32 | level, layer};
    for (int i = 0; i < 4; i++) { k ^= v[i]; k *= 1099511628211ull; k ^= k >> 29; }
    return k ? k : 1;
}
// Drawn every frame, by its last three frames: two earlier frames, and each of the three at most gap frames after the one before (gap under 1 counts as 1)
static inline int vkps_steady(const vkps_tgt *e, int gap) {
    uint64_t g = gap < 1 ? 1 : (uint64_t)gap;
    return e->f[1] && e->f[2] && e->f[0] - e->f[1] <= g && e->f[1] - e->f[2] <= g;
}
// A pass begins on the target in this frame: marked, and 1 = it is drawn every frame (a draw of the pass may be skipped), 0 = it is new or was left alone (the
// pass's draws wait for their pipelines). The same answer for every pass on it within one frame. A frame number lower than the last one starts the target anew
static inline int vkps_mark(vkps_seen *t, uint64_t key, uint64_t frame, int gap) {
    uint64_t fr = frame + 1; vkps_tgt *e = NULL, *old = NULL;
    for (uint32_t j = 0; j < VKPS_SEEN_PROBE; j++) {
        vkps_tgt *s = &t->e[(key + j) & (VKPS_SEEN_N - 1)];
        if (s->key == key) { e = s; break; }
        if (!old || s->f[0] < old->f[0]) old = s;
    }
    if (!e) { e = old; e->key = key; e->f[0] = e->f[1] = e->f[2] = 0; }
    if (e->f[0] > fr) e->f[0] = e->f[1] = e->f[2] = 0;
    if (e->f[0] != fr) { e->f[2] = e->f[1]; e->f[1] = e->f[0]; e->f[0] = fr; }
    return vkps_steady(e, gap);
}

// ---------- Bit 8's frame time, bounded per frame ----------
typedef struct { uint64_t frame, ns; } vkps_budget;   // the frame, and the time bit 8's pipelines took in it
// 1 = bit 8 may start another pipeline in this frame: those it made in it took less than ms milliseconds (ms 0 or less: no limit). A new frame starts at nothing
static inline int vkps_budget_left(vkps_budget *b, uint64_t frame, int ms) {
    if (b->frame != frame) { b->frame = frame; b->ns = 0; }
    return ms <= 0 || b->ns < (uint64_t)ms * 1000000ull;
}
static inline void vkps_budget_spend(vkps_budget *b, uint64_t ns) { b->ns += ns; }
#endif
