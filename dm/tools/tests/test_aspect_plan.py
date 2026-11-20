"""PEANUTS: aspect rows (dm/host/aspect_plan.h): the shape rule, each kind's word, and the all-or-nothing write."""
import pathlib
import re
import subprocess
import tempfile
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[3]
class AspectPlans(unittest.TestCase):
    def test_kinds_and_transaction(self):
        c=r'''
#include <assert.h>
#include "aspect_plan.h"
static unsigned char image[0x400];
static void put(unsigned o,uint32_t v) { memcpy(image+o,&v,4); }
static uint32_t get(unsigned o) { uint32_t v;memcpy(&v,image+o,4);return v; }
static float getf(unsigned o) { float f;memcpy(&f,image+o,4);return f; }
static void seed(void) { float r=16.0f/9.0f;memset(image,0,sizeof image);put(0x100,0xd280f008);put(0x104,0xf2c08708);memcpy(image+0x201,&r,4);put(0x300,1920); }
int main(void) {
 const char *bid="a6ce40dc3aedb1becacd2f1ff4515307";
 // Kirby and the Forgotten Land 1.1.0's row (mov x8,#1920), a float at an odd offset, a 32-bit width, and a row of another build
 const char *s="a6ce40dc3aedb1becacd2f1ff4515307,width_movz,m0x100,08f080d2;"
 "a6ce40dc3aedb1becacd2f1ff4515307,ratio_f32,m0x201,398ee33f;"
 "a6ce40dc3aedb1becacd2f1ff4515307,width_u32,m0x300,80070000;"
 "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb,width_u32,m0x300,80070000";
 struct asp_plan p;assert(asp_plan_parse(s,bid,&p)==3);
 // the shape: w:h, nothing for 16:9, junk or a shape outside 0.4..4
 assert(asp_shape("21:9")>2.33 && asp_shape("21:9")<2.34 && asp_shape("8:7")>1.14 && asp_shape("8:7")<1.15);
 assert(!asp_shape("16:9") && !asp_shape("1920:1080") && !asp_shape(NULL) && !asp_shape("") && !asp_shape("21:9x") && !asp_shape("0:9") && !asp_shape("9:1") && !asp_shape("1:9"));
 // 21:9: 1920 -> 2520, the MOVZ keeps its register and width; the half beside it is not touched
 seed();assert(asp_plan_apply(&p,image,sizeof image,asp_shape("21:9"))==3);
 assert(get(0x100)==(0xd2800008u|(2520u<<5)) && get(0x104)==0xf2c08708 && get(0x300)==2520);
 assert(getf(0x201)>2.3333f && getf(0x201)<2.3334f);
 // 8:7 (the second screen): 1920 -> 1234, even
 seed();assert(asp_plan_apply(&p,image,sizeof image,asp_shape("8:7"))==3);
 assert(get(0x100)==(0xd2800008u|(1234u<<5)) && get(0x300)==1234 && getf(0x201)>1.1428f && getf(0x201)<1.1429f);
 // one word that is not the expected one: nothing is written
 seed();put(0x300,1280);assert(asp_plan_apply(&p,image,sizeof image,asp_shape("21:9"))==-2);
 assert(get(0x100)==0xd280f008 && getf(0x201)>1.7777f && getf(0x201)<1.7778f);
 // a site outside the image
 seed();assert(asp_plan_apply(&p,image,0x300,asp_shape("21:9"))==-1 && get(0x100)==0xd280f008);
 // another build: no row; malformed specs
 assert(asp_plan_parse(s,"cccccccccccccccccccccccccccccccc",&p)==0 && p.n==0);
 assert(asp_plan_parse("",bid,&p)==0);
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,width_movz,m0x100",bid,&p)==-1);            // a field short
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,height_movz,m0x100,08f080d2",bid,&p)==-1);   // no such kind
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,width_movz,m0x102,08f080d2",bid,&p)==-1);    // code is word aligned
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,width_movz,m0x100,0887c0f2",bid,&p)==-1);    // a MOVK is not a MOVZ
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,ratio_f32,m0x200,0000803f",bid,&p)==-1);     // 1.0 is not 16/9
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,width_u32,m0x300,10000000",bid,&p)==-1);     // 16 is not a width
 assert(asp_plan_parse("A6CE40DC3AEDB1BECACD2F1FF4515307,width_u32,m0x300,80070000",bid,&p)==-1);     // build IDs are lower case
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,width_u32,c0x300,80070000",bid,&p)==-1);     // main only
 // Metroid Dread 2.1.0's pair (1600x900): the shape with about the same pixel count; Fire Emblem: Three Houses 1.2.0's two calls made NOPs
 const char *d="a6ce40dc3aedb1becacd2f1ff4515307,area_width_u32,m0x300,40060000;a6ce40dc3aedb1becacd2f1ff4515307,area_height_u32,m0x304,84030000;"
 "a6ce40dc3aedb1becacd2f1ff4515307,nop,m0x100,bb611394";
 assert(asp_plan_parse(d,bid,&p)==3);
 memset(image,0,sizeof image);put(0x300,1600);put(0x304,900);put(0x100,0x941361bb);
 assert(asp_plan_apply(&p,image,sizeof image,asp_shape("21:9"))==3 && get(0x300)==1834 && get(0x304)==786 && get(0x100)==0xd503201f);
 memset(image,0,sizeof image);put(0x300,1600);put(0x304,900);put(0x100,0x941361bb);
 assert(asp_plan_apply(&p,image,sizeof image,asp_shape("8:7"))==3 && get(0x300)==1282 && get(0x304)==1122);
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,nop,m0x100,08f080d2",bid,&p)==-1);           // only a BL becomes a NOP
 assert(asp_plan_parse("a6ce40dc3aedb1becacd2f1ff4515307,nop,m0x101,bb611394",bid,&p)==-1);           // code is word aligned
 return 0;
}'''
        with tempfile.TemporaryDirectory() as d:
            src=pathlib.Path(d)/'t.c';src.write_text(c)
            exe=pathlib.Path(d)/'t'
            subprocess.run(['cc','-std=c11','-D_GNU_SOURCE','-Wall','-Werror','-I',str(ROOT/'dm/host'),str(src),'-lm','-o',str(exe)],check=True)
            subprocess.run([str(exe)],check=True)
    def test_profile_rows_are_well_formed(self):
        """Every aspect_plan row in dm/games: five fields, a known kind, and a main build of the same profile (build.gradle aspectPlanOf's rule)."""
        seen=0
        for tsv in sorted((ROOT/'dm/games').glob('*/game.tsv')):
            rows=[l.split('\t') for l in tsv.read_text(encoding='utf-8').splitlines() if l.startswith('aspect_plan\t')]
            if not rows: continue
            seen+=len(rows)
            text=tsv.read_text(encoding='utf-8')
            for r in rows:
                self.assertEqual(len(r),5,f'{tsv}: {r}')
                self.assertRegex(r[1],r'^[0-9a-f]{32}$');self.assertIn(r[2],('ratio_f32','width_movz','width_u32','area_width_u32','area_height_u32','nop'))
                self.assertRegex(r[3],r'^m0x[0-9a-f]+$');self.assertRegex(r[4],r'^[0-9a-f]{8}$')
                self.assertIn(f'main={r[1]}',text,f'{tsv}: {r[1]} is not one of the profile\'s main builds')
        self.assertGreater(seen,0)
if __name__=='__main__':
    unittest.main()
