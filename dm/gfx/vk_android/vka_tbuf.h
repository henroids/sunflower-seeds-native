// PEANUTS: how a buffer texture bound to a texel buffer slot is read (vka_draw.c tbuf_view), as pure logic, free of Android and Vulkan so a unit test on Linux can check
// it (dm/host/tests/t_android_tbuf.c).
// A read-only buffer texture of up to 1 MiB is read through a copy of the CPU's bytes taken when its draw or dispatch is recorded (DM_VK_TBUF_COPY: the game may refill
// the buffer for its next frame before the GPU has drawn this one). That copy is wrong for a buffer the GPU itself writes earlier in the same work: the CPU sees those
// bytes only once the submission is done, so the copy holds the frame before's, or what the memory held last. Final Fantasy Resonance (AYN Thor, Adreno 740, Turnip):
// the particle system's instance counts are cleared and counted by compute dispatches through storage texel buffers, and the dispatch that turns them into indirect
// draw arguments (544e80ec57b3a10b) read them through such a copy: in a scene's first frame it held leftover memory, the draws got a huge instance count and the GPU
// timed out in 40-50 % of the loads (0 in 12 with DM_VK_TBUF_COPY=0).
// So a store through a storage texel buffer is kept as a GPU write of that range (vka_tex.c vka_tex_gw_tbuf: an opaque write, flagged), and while such a write is not
// done (its submission has not completed: vka_tbuf_gw_pending) a read-only texel buffer over the range is not read through the CPU's copy (DM_VK_TBUF_GW, default 1):
//   - viewed where it is, when the device can view it there (placed memory, the offset a multiple of minTexelBufferOffsetAlignment: 64 on Adreno);
//   - else, outside a draw pass (a dispatch), through a copy the GPU makes before the dispatch (vkCmdCopyBuffer into scratch memory, a full barrier before the first
//     copy and one after the last), which sees what the work recorded before it wrote;
//   - else (a draw: no copy can be recorded inside its pass) through the CPU's copy as before, noted.
// Only stores through storage texel buffers count, not every opaque write: a storage buffer bound without a size is marked over the device's whole storage range, which
// would take the CPU's copy away from buffers the CPU does write (the case the copy was made for).
#ifndef VKA_TBUF_H
#define VKA_TBUF_H
#include <stdint.h>

enum { VKTB_NONE, VKTB_PLACE, VKTB_CPU, VKTB_GPU };   // an empty texel buffer; a view of guest memory; a view of the CPU's copy; a view of a copy the GPU makes
#define VKTB_COPY_MAX (1u << 20)                      // the largest buffer read through a copy

// One GPU write as the tracker keeps it (vka_tex.c gw_t): [lo, hi), its submission's fence (0: the submission being recorded), tb: a storage texel buffer's stores.
// 1 = it is such a write, overlaps [a, b) and is not done (done: the newest completed fence)
static inline int vka_tbuf_gw_pending(uint64_t lo, uint64_t hi, uint64_t fence, int tb, uint64_t a, uint64_t b, uint64_t done) {
    return tb && (!fence || fence > done) && lo < b && hi > a;
}
// storage: a storage texel buffer binding; placed: all of it is in placed guest memory; aligned: its offset there is one the device can view; copy_sw: DM_VK_TBUF_COPY;
// gw: DM_VK_TBUF_GW is on and a storage texel buffer's write over it is pending; in_pass: a draw pass is open (or the writes are only being built for one)
static inline int vka_tbuf_path(int storage, uint64_t bytes, int placed, int aligned, int copy_sw, int gw, int in_pass) {
    int viewable = placed && aligned, copyable = !storage && bytes && bytes <= VKTB_COPY_MAX;
    if (copyable && gw) return viewable ? VKTB_PLACE : placed && !in_pass ? VKTB_GPU : VKTB_CPU;
    if (copyable && (!viewable || copy_sw)) return VKTB_CPU;
    return viewable ? VKTB_PLACE : VKTB_NONE;
}
#endif
