// PEANUTS: how a texel buffer is read (dm/gfx/vk_android/vka_tbuf.h; vka_draw.c tbuf_view): the choice between a view in place, the CPU's copy and a copy the GPU makes,
// for every combination of its inputs against the rules written out here; without a pending GPU write (or with DM_VK_TBUF_GW=0) it is the choice the replay made
// before; then which writes count as pending, over a small model of the tracker (vka_tex.c g_gw): the sequence of Final Fantasy Resonance's first frame of a scene
// (a clear and a count through storage texel buffers, then the read of the counts in the same submission), the frames after it, and the writes that must not count
#include <stdio.h>
#include <stdint.h>
#include "../../gfx/vk_android/vka_tbuf.h"

static int fails;
#define CHECK(c, ...) do { if (!(c)) { fails++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

// The replay's choice before DM_VK_TBUF_GW (vka_draw.c as it was): the CPU's copy for a read-only buffer of up to 1 MiB that cannot be viewed in place or when
// DM_VK_TBUF_COPY is on; else in place when it can be viewed there; else empty
static int before(int storage, uint64_t bytes, int placed, int aligned, int copy_sw) {
    if ((!placed || !aligned || copy_sw) && !storage && bytes && bytes <= (1u << 20)) return VKTB_CPU;
    return placed && aligned ? VKTB_PLACE : VKTB_NONE;
}

// The tracker's writes: range, fence (0 until the submission commits), tb
typedef struct { uint64_t lo, hi, fence; int tb; } wr;
static wr W[16]; static int nw; static uint64_t done_;
static void mark(uint64_t a, uint64_t n, int tb) { W[nw++] = (wr){a, a + n, 0, tb}; }
static void commit(uint64_t fence) { for (int i = 0; i < nw; i++) if (!W[i].fence) W[i].fence = fence; }
static int pending(uint64_t a, uint64_t n) { int r = 0; for (int i = 0; i < nw && !r; i++) r = vka_tbuf_gw_pending(W[i].lo, W[i].hi, W[i].fence, W[i].tb, a, a + n, done_); return r; }

int main(void) {
    static const uint64_t sizes[] = {0, 4, 8192, 1u << 20, (1u << 20) + 4, 64u << 20};
    int n = 0;
    for (int storage = 0; storage < 2; storage++) for (unsigned s = 0; s < sizeof sizes / sizeof *sizes; s++) for (int placed = 0; placed < 2; placed++)
    for (int aligned = 0; aligned < 2; aligned++) for (int copy_sw = 0; copy_sw < 2; copy_sw++) for (int gw = 0; gw < 2; gw++) for (int in_pass = 0; in_pass < 2; in_pass++) {
        uint64_t bytes = sizes[s];
        int p = vka_tbuf_path(storage, bytes, placed, aligned, copy_sw, gw, in_pass), was = before(storage, bytes, placed, aligned, copy_sw); n++;
        #define CASE "storage %d bytes %llu placed %d aligned %d copy %d gw %d pass %d: %d", storage, (unsigned long long)bytes, placed, aligned, copy_sw, gw, in_pass, p
        // no pending GPU write: exactly as before, whatever else
        if (!gw) CHECK(p == was, CASE);
        // a storage binding is never a copy (its stores must land in guest memory); a buffer over 1 MiB and an empty one neither
        if (storage || !bytes || bytes > (1u << 20)) CHECK(p == VKTB_PLACE || p == VKTB_NONE, CASE);
        if (storage || !bytes || bytes > (1u << 20)) CHECK(p == was, CASE);
        // a view in place only where the device can make one; a copy on the GPU only of placed memory (it copies from the guest memory buffer) and never inside a pass
        if (p == VKTB_PLACE) CHECK(placed && aligned, CASE);
        if (p == VKTB_GPU) CHECK(gw && placed && !aligned && !in_pass && !storage, CASE);
        // a pending GPU write over a read-only buffer of up to 1 MiB: never the CPU's copy where anything else is possible, and the copy switch does not matter
        if (gw && !storage && bytes && bytes <= (1u << 20)) {
            if (placed && aligned) CHECK(p == VKTB_PLACE, CASE);
            else if (placed && !in_pass) CHECK(p == VKTB_GPU, CASE);
            else CHECK(p == VKTB_CPU, CASE);
            CHECK(p == vka_tbuf_path(storage, bytes, placed, aligned, !copy_sw, gw, in_pass), CASE);
        }
        // read-only, up to 1 MiB: never empty
        if (!storage && bytes && bytes <= (1u << 20)) CHECK(p != VKTB_NONE, CASE);
        #undef CASE
    }
    // The cases by name
    CHECK(vka_tbuf_path(0, 8192, 1, 1, 1, 0, 0) == VKTB_CPU, "a CPU-written buffer keeps its copy");
    CHECK(vka_tbuf_path(0, 8192, 1, 1, 1, 1, 0) == VKTB_PLACE, "GPU-written, aligned, a dispatch: in place");
    CHECK(vka_tbuf_path(0, 8192, 1, 1, 1, 1, 1) == VKTB_PLACE, "GPU-written, aligned, a draw: in place");
    CHECK(vka_tbuf_path(0, 8192, 1, 0, 1, 1, 0) == VKTB_GPU, "GPU-written, misaligned, a dispatch: the GPU's copy");
    CHECK(vka_tbuf_path(0, 8192, 1, 0, 1, 1, 1) == VKTB_CPU, "GPU-written, misaligned, a draw: the CPU's copy");
    CHECK(vka_tbuf_path(0, 8192, 0, 1, 0, 1, 0) == VKTB_CPU, "GPU-written, not in placed memory: the CPU's copy");
    CHECK(vka_tbuf_path(1, 8192, 1, 0, 1, 1, 0) == VKTB_NONE, "a misaligned storage texel buffer stays empty");

    // The tracker. The buffer: 2,048 words at 0x10000; the newest completed fence is 6, the submission being recorded will be 8
    uint64_t X = 0x10000, XN = 8192; done_ = 6;
    CHECK(!pending(X, XN), "nothing recorded: not pending");
    mark(0x40000, 4096, 1);                      // another buffer's stores
    CHECK(!pending(X, XN), "another range");
    mark(X, XN, 0);                              // a storage buffer's (or a copy's) opaque write over it: not counted (vka_tbuf.h)
    CHECK(!pending(X, XN), "an opaque write that is not a storage texel buffer's");
    mark(X, XN, 1);                              // the clear dispatch, then the counting dispatches, in the submission being recorded
    CHECK(pending(X, XN), "stores recorded in this submission");
    CHECK(pending(X + 4096, 4) && pending(X - 4, 8) && pending(X + XN - 4, 64), "an overlap at either end, or inside");
    CHECK(!pending(X + XN, 64) && !pending(X - 64, 64), "the ranges beside it");
    commit(8);
    CHECK(pending(X, XN), "the submission is queued, not done (the next frame is being recorded)");
    done_ = 7; CHECK(pending(X, XN), "an earlier submission is done");
    done_ = 8; CHECK(!pending(X, XN), "its submission is done: the CPU has the bytes, the copy is right again");
    mark(X, XN, 1); done_ = 9;                   // the next frame's stores: fence 0 is the submission being recorded, whatever is done
    CHECK(pending(X, XN), "the next frame's stores");
    commit(10); done_ = 10;
    CHECK(!pending(X, XN), "and done");

    if (fails) { printf("t_android_tbuf: %d FAILED\n", fails); return 1; }
    printf("t_android_tbuf: ok (%d choices; the pending writes)\n", n);
    return 0;
}
