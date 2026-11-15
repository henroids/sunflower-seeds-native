// PEANUTS: which draws are not skipped while their pipeline is made in the background (DM_VK_PSO_ASYNC; vka_draw.c draw_body, vka_pso.c vka_gfx_pipeline_now), as pure
// logic, free of Android and Vulkan so a unit test on Linux can check it (dm/host/tests/t_android_psosync.c).
// With the asynchronous threads on, a draw whose pipeline the cache lacks is skipped until a background thread has made it. That costs the draw in a few frames, which
// is the point, except where the game keeps what the draw made: then the picture stays wrong after every pipeline is there. Super Mario Odyssey on an AYN Thor (Adreno
// 740, Turnip), first run (no pipeline cache, no prewarm list): black with only the HUD for the whole session, `7 % of draws skipped ... pipelines not made 11521`,
// ~650 pipelines made in the background in the first minute and still black with none waiting; right with DM_VK_PSO_ASYNC=0, and right once the cache existed.
// Which draw's result was kept is not measured; the likely ones are a post-processing history (exposure or luminance adaptation: a tiny float target blended with its
// own last value each frame, which never recovers from an undefined or non-finite start) and tables drawn once (colour grading).
// So three kinds of draw get their pipeline on the frame path, as with DM_VK_PSO_ASYNC=0, each a bit of DM_VK_PSO_SYNC_PASS (default 7; 0 = every miss skipped):
//   1  a pass over the whole target: a direct draw (not indirect, not a multi-draw, not a mesh draw) of one instance and at most DM_VK_PSO_SYNC_VERTS vertices or
//      indices (default 6: a full-screen triangle is 3, a strip 4, a quad drawn as two indexed triangles 6). Post-processing chains draw this way, and each pass reads
//      the one before, so one skipped link blanks the frame. The count is what the draw record holds, indices for an indexed draw;
//   2  a draw into a small target, at most 64x64 texels in the game's own pixels, whatever its geometry: luminance, exposure and lookup tables;
//   4  a draw whose result is memory, not a picture: its shaders write storage buffers, images or raw memory, or the rasterizer is off (transform feedback, a
//      geometry shader's writes). Nothing redraws those next frame unless the game does.
// Everything else is skipped as before: scene geometry is thousands of vertices on full-size targets, and that is where the long compiles are. Left unfixed by
// design: a kept result drawn with ordinary geometry into a larger target (a cube map or a shadow atlas drawn once).
#ifndef VKA_PSOSYNC_H
#define VKA_PSOSYNC_H
#include <stdint.h>

enum { VKPS_PASS = 1, VKPS_SMALL = 2, VKPS_WRITES = 4, VKPS_ALL = 7,   // DM_VK_PSO_SYNC_PASS's bits, and the reason vkps_why returns
       VKPS_VERTS = 6,                                                // DM_VK_PSO_SYNC_VERTS's default
       VKPS_SMALL_PX = 64 };                                          // a small target's side at most

// The draw as draw_body knows it where the pipeline is looked up. count: vertices, or indices of an indexed draw (not known for an indirect one); instances: 0 counts
// as 1, as in the record; w, h: the pass's target in the game's own pixels (0: not known); writes: the shaders write memory, or the rasterizer is off
typedef struct { int mesh, indirect, instances, writes; int64_t count; uint32_t w, h; } vkps_draw;

// The reason this draw's pipeline is made on the frame path (one VKPS_ bit, the lowest that applies), 0 = none: the draw is skipped until the pipeline is made
static inline int vkps_why(int bits, int max_verts, const vkps_draw *d) {
    if ((bits & VKPS_PASS) && !d->mesh && !d->indirect && d->instances <= 1 && d->count >= 1 && d->count <= max_verts) return VKPS_PASS;
    if ((bits & VKPS_SMALL) && d->w && d->h && d->w <= VKPS_SMALL_PX && d->h <= VKPS_SMALL_PX) return VKPS_SMALL;
    if ((bits & VKPS_WRITES) && d->writes) return VKPS_WRITES;
    return 0;
}
static inline const char *vkps_name(int why) { return why == VKPS_PASS ? "a pass of a few vertices" : why == VKPS_SMALL ? "a small target" : why == VKPS_WRITES ? "it writes memory" : "none"; }
#endif
