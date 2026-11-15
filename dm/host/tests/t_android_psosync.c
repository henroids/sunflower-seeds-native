// PEANUTS: which draws are not skipped while their pipeline is made in the background (dm/gfx/vk_android/vka_psosync.h vkps_why; vka_draw.c draw_body,
// DM_VK_PSO_SYNC_PASS / DM_VK_PSO_SYNC_VERTS): each kind alone and by its bit, the limits' edges, and that ordinary scene draws stay skipped; which targets are
// drawn every frame (vkps_mark: frames in which a target is drawn into, its draws drawn or skipped, or is absent), and the frame budget of that kind (vkps_budget)
#include <stdio.h>
#include <string.h>
#include "../../gfx/vk_android/vka_psosync.h"

static int fails;
#define CHECK(c, ...) do { if (!(c)) { fails++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

static vkps_draw draw(int64_t count, int instances, uint32_t w, uint32_t h) { vkps_draw d = {0}; d.count = count; d.instances = instances; d.w = w; d.h = h; return d; }

// A target drawn into in the frames of fr (ascending), on a fresh table: what vkps_mark answered in each, as a string of 'w' (its draws wait) and 's' (they may be skipped)
static const char *run(const uint64_t *fr, int n, int gap) {
    static vkps_seen t; static char out[64]; memset(&t, 0, sizeof t);
    uint64_t key = vkps_key(0x7100000000ull, 512, 512, 37, 0, 0);
    for (int i = 0; i < n; i++) out[i] = vkps_mark(&t, key, fr[i], gap) ? 's' : 'w';
    out[n] = 0; return out;
}
#define RUN(gap, ...) run((const uint64_t[]){__VA_ARGS__}, (int)(sizeof((const uint64_t[]){__VA_ARGS__}) / sizeof(uint64_t)), gap)

int main(void) {
    vkps_draw d;
    // Passes of a few vertices on a full-size target: a triangle, a strip, an indexed quad; instances 0 counts as 1
    for (int n = 1; n <= VKPS_VERTS; n++) { d = draw(n, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_PASS, "%d vertices", n); }
    d = draw(3, 0, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_PASS, "instances 0");
    d = draw(VKPS_VERTS + 1, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "one vertex more than the limit");
    d = draw(0, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "an empty draw");
    d = draw(-1, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "a negative count");
    d = draw(4, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, 3, &d) == 0 && vkps_why(VKPS_ALL, 4, &d) == VKPS_PASS, "the switch's limit");
    d = draw(4, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, 0, &d) == 0, "limit 0: no pass counts");
    // Not passes: instanced, indirect (the count is the GPU's), mesh
    d = draw(4, 2, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "instanced");
    d = draw(4, 1, 1280, 720); d.indirect = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "indirect");
    d = draw(3, 1, 1280, 720); d.mesh = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "mesh");
    // Ordinary scene draws stay skipped, whatever the target's size above the small one
    d = draw(30000, 1, 1280, 720); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "scene geometry");
    d = draw(30000, 16, 2048, 2048); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "instanced geometry into a shadow map");
    d = draw(900, 1, 128, 128); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "geometry into a 128x128 target");
    // Small targets, whatever the geometry: both sides within the limit, and a size that is known
    d = draw(30000, 4, 64, 64); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_SMALL, "64x64");
    d = draw(30000, 1, 1, 1); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_SMALL, "1x1");
    d = draw(30000, 1, 256, 16); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "256x16: one side over");
    d = draw(30000, 1, 65, 64); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "65x64");
    d = draw(30000, 1, 0, 0); CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "size not known");
    d = draw(30000, 1, 32, 32); d.indirect = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_SMALL, "indirect into a small target");
    // Draws that write memory
    d = draw(30000, 1, 1280, 720); d.writes = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_WRITES, "writes memory");
    d = draw(30000, 8, 1280, 720); d.writes = 1; d.indirect = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_WRITES, "indirect, writes memory");
    // The bits: 0 is the behaviour before (every miss skipped); each kind only with its bit; the lowest bit that applies is the reason
    d = draw(3, 1, 16, 16); d.writes = 1;
    CHECK(vkps_why(0, VKPS_VERTS, &d) == 0, "bits 0");
    CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_PASS, "all three apply: the pass");
    CHECK(vkps_why(VKPS_SMALL | VKPS_WRITES, VKPS_VERTS, &d) == VKPS_SMALL, "without the pass bit");
    CHECK(vkps_why(VKPS_WRITES, VKPS_VERTS, &d) == VKPS_WRITES, "the writes bit alone");
    CHECK(vkps_why(VKPS_PASS, VKPS_VERTS, &d) == VKPS_PASS, "the pass bit alone");
    d = draw(30000, 1, 16, 16); CHECK(vkps_why(VKPS_PASS | VKPS_WRITES, VKPS_VERTS, &d) == 0, "a small target without its bit");
    d = draw(30000, 1, 1280, 720); d.writes = 1; CHECK(vkps_why(VKPS_PASS | VKPS_SMALL, VKPS_VERTS, &d) == 0, "a writing draw without its bit");
    CHECK(VKPS_ALL == (VKPS_PASS | VKPS_SMALL | VKPS_WRITES | VKPS_NEW) && VKPS_DEFAULT == (VKPS_SMALL | VKPS_WRITES), "every kind, and the default: small targets and draws that write memory");
    { int w[5] = {0, VKPS_PASS, VKPS_SMALL, VKPS_WRITES, VKPS_NEW};
      for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++) CHECK(strcmp(vkps_name(w[i]), vkps_name(w[j])), "the names of reasons %d and %d differ", w[i], w[j]); }

    // Bit 8: scene geometry into a target that is not drawn every frame waits, into one that is stays skipped; the lower bits name the reason first
    d = draw(30000, 16, 2048, 2048); d.fresh = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_NEW, "geometry into a new shadow atlas");
    d = draw(30000, 1, 512, 512); d.fresh = 1; d.indirect = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_NEW, "indirect geometry into a new cube face");
    d = draw(30000, 1, 512, 512); d.fresh = 1; CHECK(vkps_why(VKPS_NEW, VKPS_VERTS, &d) == VKPS_NEW, "the bit alone");
    d = draw(30000, 1, 512, 512); d.fresh = 1; CHECK(vkps_why(VKPS_PASS | VKPS_SMALL | VKPS_WRITES, VKPS_VERTS, &d) == 0, "a new target without its bit (the rule before)");
    d = draw(30000, 1, 1600, 900); d.fresh = 0; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == 0, "geometry into a target drawn every frame");
    d = draw(3, 1, 512, 512); d.fresh = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_PASS, "a pass into a new target: the pass");
    d = draw(30000, 1, 32, 32); d.fresh = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_SMALL, "a new small target: the small target");
    d = draw(30000, 1, 512, 512); d.fresh = 1; d.writes = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_WRITES, "a writing draw into a new target: the writes");
    d = draw(30000, 1, 512, 512); d.fresh = 1; d.mesh = 1; CHECK(vkps_why(VKPS_ALL, VKPS_VERTS, &d) == VKPS_NEW, "the reason does not look at the draw's kind");

    // Which targets are drawn every frame. The answer is the same whether the earlier frames' draws were drawn or skipped: the mark is the pass's, not the draw's
    CHECK(!strcmp(RUN(VKPS_GAP, 10), "w"), "drawn once (a cube map, a table): waits");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11), "ww"), "the first two frames wait");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 12, 13, 14), "wwsss"), "drawn every frame: skipped from the third frame on");
    CHECK(!strcmp(RUN(VKPS_GAP, 0, 1, 2), "wws"), "from frame 0");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 10, 10, 11, 11, 12, 12), "wwwwwss"), "several passes in a frame: one answer for the frame");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 12, 14, 16), "wwss"), "on alternate frames (a history pair, a half-rate target)");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 14, 18, 22), "wwss"), "every fourth frame (one of four window textures): the gap's edge");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 15, 20, 25, 30), "wwwww"), "every fifth frame (a shadow atlas updated now and then): always waits");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 12, 13, 18, 19, 20), "wwsswws"), "left alone for five frames (a loading screen): two frames wait again");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 12, 16, 17), "wwsss"), "left alone for three frames: within the gap");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 100, 101, 102), "wwwws"), "two frames, a long gap, then every frame");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 12, 300, 600), "wwsww"), "drawn every frame, then rarely");
    CHECK(!strcmp(RUN(2, 10, 12, 14, 17, 19, 21), "wwswws"), "gap 2");
    CHECK(!strcmp(RUN(1, 10, 11, 12, 14, 15, 16), "wwswws"), "gap 1: consecutive frames only");
    CHECK(!strcmp(RUN(0, 10, 11, 12, 14, 15, 16), "wwswws") && !strcmp(RUN(-3, 10, 11, 12), "wws"), "a gap under 1 counts as 1");
    CHECK(!strcmp(RUN(VKPS_GAP, 10, 11, 12, 5, 6, 7), "wwswws"), "the frame number went back: anew");
    {   // Targets are apart: storage, size, format, level and layer each make another one; the same six values are the same target (a texture object made again)
        static vkps_seen t; memset(&t, 0, sizeof t);
        uint64_t k0 = vkps_key(0x7100000000ull, 512, 512, 37, 0, 0);
        uint64_t k[6] = {vkps_key(0x7100100000ull, 512, 512, 37, 0, 0), vkps_key(0x7100000000ull, 256, 512, 37, 0, 0), vkps_key(0x7100000000ull, 512, 256, 37, 0, 0),
                         vkps_key(0x7100000000ull, 512, 512, 38, 0, 0), vkps_key(0x7100000000ull, 512, 512, 37, 1, 0), vkps_key(0x7100000000ull, 512, 512, 37, 0, 1)};
        for (int i = 0; i < 6; i++) { CHECK(k[i] != k0 && k[i], "key %d differs", i); for (int j = i + 1; j < 6; j++) CHECK(k[i] != k[j], "keys %d and %d differ", i, j); }
        CHECK(vkps_key(0x7100000000ull, 512, 512, 37, 0, 0) == k0 && vkps_key(0, 0, 0, 0, 0, 0), "the same values: the same key; never 0");
        for (uint64_t f = 1; f <= 3; f++) vkps_mark(&t, k0, f, VKPS_GAP);
        CHECK(vkps_mark(&t, k0, 4, VKPS_GAP) == 1, "the scene target is drawn every frame");
        // A cube map drawn a face a frame beside it: each face is new in its frame, so its pass waits, and the scene target stays as it is
        for (uint32_t face = 0; face < 6; face++) {
            CHECK(vkps_mark(&t, vkps_key(0x7200000000ull, 256, 256, 37, 0, face), 4 + face, VKPS_GAP) == 0, "cube face %u", face);
            CHECK(vkps_mark(&t, k0, 4 + face, VKPS_GAP) == 1, "the scene target beside face %u", face);
        }
        // Dynamic resolution: another size is another target (two frames wait); back at the first size within the gap it is still drawn every frame
        uint64_t ka = vkps_key(0x7300000000ull, 1600, 900, 37, 0, 0), kb = vkps_key(0x7300000000ull, 1280, 720, 37, 0, 0);
        for (uint64_t f = 20; f <= 22; f++) vkps_mark(&t, ka, f, VKPS_GAP);
        CHECK(vkps_mark(&t, kb, 23, VKPS_GAP) == 0 && vkps_mark(&t, kb, 24, VKPS_GAP) == 0 && vkps_mark(&t, kb, 25, VKPS_GAP) == 1, "the new size: two frames");
        CHECK(vkps_mark(&t, ka, 26, VKPS_GAP) == 1, "the first size again after three frames");
    }
    {   // More targets than the table holds, all drawn every frame: none is lost while VKPS_SEEN_N / 4 are live, and a table overrun only makes targets wait (never skip wrongly)
        static vkps_seen t; memset(&t, 0, sizeof t); int lost = 0, n = VKPS_SEEN_N / 4;
        for (uint64_t f = 1; f <= 4; f++) for (int i = 0; i < n; i++) { int s = vkps_mark(&t, vkps_key(0x7400000000ull + (uint64_t)i * 0x10000, 64, 64, 37, 0, 0), f, VKPS_GAP); if (f >= 3 && !s) lost++; }
        CHECK(lost == 0, "%d of %d targets drawn every frame were forgotten", lost, n);
        memset(&t, 0, sizeof t); int skip_early = 0;
        for (uint64_t f = 1; f <= 2; f++) for (int i = 0; i < VKPS_SEEN_N * 4; i++) skip_early += vkps_mark(&t, vkps_key(0x7400000000ull + (uint64_t)i * 0x10000, 64, 64, 37, 0, 0), f, VKPS_GAP);
        CHECK(skip_early == 0, "an overrun table: no target counts as drawn every frame in its first two frames");
    }
    {   // The frame budget of bit 8: pipelines start while those made in the frame took less than the limit; the next frame starts at nothing; 0 = no limit
        vkps_budget b = {0};
        CHECK(vkps_budget_left(&b, 0, VKPS_NEW_MS), "frame 0, nothing spent");
        CHECK(vkps_budget_left(&b, 7, VKPS_NEW_MS), "nothing spent");
        vkps_budget_spend(&b, 60 * 1000000ull); CHECK(vkps_budget_left(&b, 7, 100), "60 of 100 ms");
        vkps_budget_spend(&b, 39999999ull); CHECK(vkps_budget_left(&b, 7, 100), "just under");
        vkps_budget_spend(&b, 1); CHECK(!vkps_budget_left(&b, 7, 100), "100 of 100 ms: no more");
        vkps_budget_spend(&b, 900 * 1000000ull); CHECK(!vkps_budget_left(&b, 7, 100), "one long pipeline over it");
        CHECK(vkps_budget_left(&b, 7, 0) && vkps_budget_left(&b, 7, -1), "no limit");
        CHECK(vkps_budget_left(&b, 7, 2000), "a higher limit, live");
        CHECK(vkps_budget_left(&b, 8, 100) && b.ns == 0, "the next frame");
    }
    printf(fails ? "t_android_psosync: %d FAILED\n" : "t_android_psosync: ok\n", fails);
    return fails != 0;
}
