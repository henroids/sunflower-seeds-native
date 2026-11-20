// PEANUTS: exact-build aspect rows (game.tsv aspect_plan). A game that builds its screen shape from one 16:9 number (a ratio, or a
// width beside a fixed height) follows the picture's shape (DM_VK_PICTURE_ASPECT=<w>:<h>, vka_present.c) with a profile row and no
// code of its own: the loader checks the word the row expects and writes the value for the chosen shape, before code is sealed.
// Spec (DM_ASPECT_PLAN): "<main build, 32 hex>,<kind>,m0x<offset in main>,<expected word, 8 hex as in the file>" rows, ';' between.
// Kinds: ratio_f32 (a float 16/9 becomes the shape), width_movz (a MOVZ's imm16 width for 16:9 becomes the width for the shape),
// width_u32 (the same for a 32-bit width in data). All rows are checked before any is written.
#ifndef DM_ASPECT_PLAN_H
#define DM_ASPECT_PLAN_H
#include "fpslocker_plan.h"
#include <math.h>
#include <stdio.h>
#define ASP_ROWS 32
#define ASP_TEXT 4096
#define ASP_K169 (16.0 / 9.0)
struct asp_row {
  char kind;   // 'r' ratio_f32, 'w' width_movz, 'u' width_u32
  uint64_t off;
  uint32_t expected;
};
struct asp_plan {
  struct asp_row row[ASP_ROWS];
  unsigned n;
};
// DM_VK_PICTURE_ASPECT=<w>:<h> -> w / h, or 0 (unset, invalid, outside 0.4..4, or within 0.02 of 16:9): wide_shape.h's rule
static inline double asp_shape(const char *e) {
  int w = 0, h = 0;
  char tail;
  if (!e || sscanf(e, "%d:%d%c", &w, &h, &tail) != 2 || w <= 0 || h <= 0)
    return 0;
  double a = (double)w / h;
  return a > 0.4 && a < 4.0 && fabs(a - ASP_K169) >= 0.02 ? a : 0;
}
// A 16:9 width for the shape: even, as render sizes are
static inline uint32_t asp_width(uint32_t w169, double shape) {
  return (uint32_t)(lround(w169 * shape / ASP_K169 / 2.0) * 2);
}
// The word a row writes for the shape, from the word it expects; 0 when the expected word is not what the kind takes
static inline int asp_word(const struct asp_row *r, double shape, uint32_t *out) {
  if (r->kind == 'r') {
    float f;
    memcpy(&f, &r->expected, 4);
    if (!(fabs((double)f - ASP_K169) < 0.01))
      return 0;
    f = (float)shape;
    memcpy(out, &f, 4);
    return 1;
  }
  if (r->kind == 'w') {   // MOVZ, 32 or 64 bit, any shift: sf 10 100101 hw imm16 Rd
    if ((r->expected & 0x7f800000u) != 0x52800000u)
      return 0;
    uint32_t imm = (r->expected >> 5) & 0xffff, w = asp_width(imm, shape);
    if (imm < 64 || w < 64 || w > 0xffff)
      return 0;
    *out = (r->expected & ~(0xffffu << 5)) | (w << 5);
    return 1;
  }
  if (r->kind == 'u') {
    uint32_t w = asp_width(r->expected, shape);
    if (r->expected < 64 || r->expected > 16384 || w < 64 || w > 65535)
      return 0;
    *out = w;
    return 1;
  }
  return 0;
}
// The rows of `build` (their count), or -1 on a malformed spec. A row of another build is checked for form and left out.
static inline int asp_plan_parse(const char *spec, const char *build, struct asp_plan *out) {
  struct asp_plan plan = {0};
  char buf[ASP_TEXT];
  if (!spec || !*spec) {
    *out = plan;
    return 0;
  }
  if (strlen(spec) >= sizeof buf)
    return -1;
  strcpy(buf, spec);
  char *save = NULL;
  for (char *line = strtok_r(buf, ";", &save); line; line = strtok_r(NULL, ";", &save)) {
    char *f[4], *p = line;
    for (unsigned i = 0; i < 4; i++) {
      f[i] = p;
      char *comma = strchr(p, ',');
      if (i < 3) {
        if (!comma)
          return -1;
        *comma = 0;
        p = comma + 1;
      } else if (comma)
        return -1;
    }
    if (strlen(f[0]) != 32)
      return -1;
    for (unsigned i = 0; i < 32; i++)
      if (!strchr("0123456789abcdef", f[0][i]))
        return -1;
    struct asp_row r = {0};
    r.kind = !strcmp(f[1], "ratio_f32") ? 'r' : !strcmp(f[1], "width_movz") ? 'w' : !strcmp(f[1], "width_u32") ? 'u' : 0;
    if (!r.kind || f[2][0] != 'm' || !fpw_plan_hex(f[2] + 1, &r.off) || !fpw_plan_word(f[3], &r.expected))
      return -1;
    if (r.kind == 'w' && (r.off & 3))
      return -1;
    uint32_t probe;
    if (!asp_word(&r, 21.0 / 9.0, &probe))   // the expected word must be one the kind takes, whatever the build
      return -1;
    if (!strcmp(f[0], build)) {
      if (plan.n == ASP_ROWS)
        return -1;
      plan.row[plan.n++] = r;
    }
  }
  *out = plan;
  return (int)plan.n;
}
// Writes every row's word for the shape: the count, -1 (a site outside the image), -2 (a word that is not the expected one) or
// -3 (a width the shape cannot take). Nothing is written unless every row passes.
static inline int asp_plan_apply(const struct asp_plan *plan, uint8_t *image, uint64_t size, double shape) {
  uint32_t words[ASP_ROWS];
  for (unsigned i = 0; i < plan->n; i++) {
    const struct asp_row *r = &plan->row[i];
    if (r->off > size || size - r->off < 4)
      return -1;
    uint32_t old;
    memcpy(&old, image + r->off, 4);
    if (old != r->expected)
      return -2;
    if (!asp_word(r, shape, &words[i]))
      return -3;
  }
  for (unsigned i = 0; i < plan->n; i++)
    memcpy(image + plan->row[i].off, &words[i], 4);
  return (int)plan->n;
}
#endif
