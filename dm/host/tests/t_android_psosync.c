// PEANUTS: which draws are not skipped while their pipeline is made in the background (dm/gfx/vk_android/vka_psosync.h vkps_why; vka_draw.c draw_body,
// DM_VK_PSO_SYNC_PASS / DM_VK_PSO_SYNC_VERTS): each kind alone and by its bit, the limits' edges, and that ordinary scene draws stay skipped
#include <stdio.h>
#include <string.h>
#include "../../gfx/vk_android/vka_psosync.h"

static int fails;
#define CHECK(c, ...) do { if (!(c)) { fails++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

static vkps_draw draw(int64_t count, int instances, uint32_t w, uint32_t h) { vkps_draw d = {0}; d.count = count; d.instances = instances; d.w = w; d.h = h; return d; }

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
    CHECK(VKPS_ALL == (VKPS_PASS | VKPS_SMALL | VKPS_WRITES), "the default is every kind");
    CHECK(strcmp(vkps_name(VKPS_PASS), vkps_name(VKPS_SMALL)) && strcmp(vkps_name(VKPS_SMALL), vkps_name(VKPS_WRITES)) && strcmp(vkps_name(0), vkps_name(VKPS_PASS)), "the reasons' names differ");
    printf(fails ? "t_android_psosync: %d FAILED\n" : "t_android_psosync: ok\n", fails);
    return fails != 0;
}
