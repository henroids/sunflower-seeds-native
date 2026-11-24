package dev.peanuts.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import java.util.Random;

/**
 * Home's background on the scoreboard look (Scoreboard.ON; BananaBackgroundView hands its drawing, ticks and taps here): the whole screen is a
 * board of bulbs, and seeds (the logo, as lit bulbs) march across it one bulb column at a time, as on a stadium scoreboard: each step the whole seed moves
 * over at once, keeping its shape. They are scattered at random, each with its own tilt (one of six patterns), most at the baseline pace and some a little
 * slower or faster, scattered at first at least MIN_APART bulbs apart where there is room. Each runs on a loop of its own: one that leaves on the right comes
 * back from the left at its own pace, in a new row and tilt drawn from the seed and the lap, so it all follows from the time (Seed). A few bulbs of the board are dead, as on a real one: they stay dark under a seed. A tap puts a seed out for RESPAWN_MS.
 * Cheap: the unlit board is one shader-filled rect, a seed one prebaked bitmap of its pattern, and every time is a multiple of TICK_MS, so the view
 * redraws at most about 16 times a second, and only when something changed
 */
public final class BulbBoard {
    /** The seed as bulbs, in six tilts: # lit, o a stripe (out), . not the seed's. Made from the logo's shape (sb_seed.png), each stripe one bulb a row */
    private static final String[][] PATTERNS = {
        { // turned 24 degrees to the left
            ".....####......",
            "...#########...",
            "..#######o##...",
            ".####o###o####.",
            ".#o##o#####o##.",
            "##o###o####o##.",
            "##o###o#####o#.",
            "##o####o###o##.",
            "##o####o###o###",
            "##o#####o###o##",
            "###o####o###o##",
            ".###o###o###o##",
            "..###o###o##o#.",
            "...###o##o##o##",
            "....###o##o###.",
            ".....###o#o####",
            "......#####o##.",
            "........######.",
            ".........####..",
            "...........##..",
        },
        { // turned 14 degrees to the left
            ".....#####.....",
            "....########...",
            "..########o##..",
            "..#o##o###o###.",
            ".##o###o####o##",
            ".##o###o####o##",
            ".#o####o####o##",
            "##o####o####o##",
            "##o#####o###o##",
            ".##o####o###o##",
            ".###o###o###o##",
            "..##o###o###o#.",
            "..##o###o###o#.",
            "...###o##o#o##.",
            "....##o##o#o##.",
            ".....##o#o###..",
            ".....####o####.",
            ".......######..",
            ".......#####...",
            ".........###...",
        },
        { // turned 4 degrees to the left
            "....######.....",
            "...#########...",
            "..########o##..",
            ".##o###o###o##.",
            ".##o###o###o##.",
            "##o####o####o##",
            "##o####o####o##",
            "##o####o####o##",
            "##o####o####o#.",
            "###o###o###o##.",
            ".##o###o###o##.",
            ".##o###o###o##.",
            "..##o##o##o##..",
            "..##o##o##o##..",
            "...##o#o#o##...",
            "...##o#o####...",
            "....###o###....",
            "....#######....",
            ".....#####.....",
            ".......###.....",
            "........#......",
        },
        { // turned 8 degrees to the right
            "......#####.....",
            "....########....",
            "..###o#######...",
            "..##o###o##o##..",
            ".##o####o###o##.",
            ".##o####o###o##.",
            "###o####o####o##",
            "##o####o####o##.",
            ".##o###o####o##.",
            ".##o###o####o##.",
            ".##o###o###o##..",
            "..#o###o###o##..",
            "..##o##o##o###..",
            "..##o##o##o##...",
            "..##o#o##o##....",
            "...###o#o##.....",
            "...###o####.....",
            "...#######......",
            "....#####.......",
            ".....###........",
            "......#.........",
        },
        { // turned 15 degrees to the right
            ".....#####.....",
            "...#########...",
            "..##o########..",
            ".###o###o####..",
            "##o#####o###o#.",
            "##o####o###o###",
            "##o####o####o#.",
            "##o####o####o##",
            "##o####o####o##",
            "##o###o####o###",
            "##o###o###o###.",
            ".#o###o###o##..",
            ".#o##o####o##..",
            ".##o#o##o###...",
            ".##o#o##o##....",
            "..###o#o##.....",
            ".###o#####.....",
            "..######.......",
            "...####........",
            "...###.........",
        },
        { // turned 24 degrees to the right
            "......####.....",
            "...#########...",
            "...##o#######..",
            ".####o###o####.",
            ".##o#####o##o#.",
            ".##o####o###o##",
            ".#o#####o###o##",
            ".##o###o####o##",
            "###o###o####o##",
            "##o###o#####o##",
            "##o###o####o###",
            "##o###o###o###.",
            ".#o##o###o###..",
            "##o##o##o###...",
            ".###o##o###....",
            "####o#o###.....",
            ".##o#####......",
            ".######........",
            "..####.........",
            "..##...........",
        },
    };
    private static final float PITCH_DP = 5f;
    private static final long TICK_MS = 60, RESPAWN_MS = 5000;
    private static final long BASE_MS = 540;                                // the baseline step; about a third of the seeds step a little slower or faster
    private static final long[] PERIODS = {BASE_MS, BASE_MS, BASE_MS, BASE_MS, BASE_MS, BASE_MS, 480, 600, 600};   // a seed's step, ms (multiples of TICK_MS)
    private static final int LIT = 0xFFE9A93F, LIT_ALPHA = 92, UNLIT = 0xFF141912, DEAD = 0xFF0A0C09;
    private static final int AREA_PER_SEED = 2000;                          // bulbs of board a seed: how many seeds a screen gets
    private static final int MIN_APART = 30;                                // bulbs between two seeds' centres at least (the first board's lattice spacing), where there is room
    private static final float DEAD_SHARE = 0.005f;                         // the board's dead bulbs
    private static final int LEAD = 20;                                     // columns a seed starts off the left edge (wider than any pattern)

    /** One seed on its loop: it starts at column x0, steps a column every period, and on leaving on the right comes back from the left span columns later.
     *  Everything follows from the time, so a jump in time (the screen off and on again) lands each seed where it would be, never all at the left edge */
    private final class Seed {
        int id, pat0, y0, span; long x0, t0, period, out = Long.MIN_VALUE;
        private long lapAt = Long.MIN_VALUE; private int pat, y;
        private long pos(long ms) { return x0 + LEAD + Math.max(0, ms - t0) / period; }
        long x(long ms) { return Math.floorMod(pos(ms), (long) span) - LEAD; }
        /** This lap's tilt and row: the placed ones on the first lap, then one drawn from the seed and the lap (the same for the same lap, however it is reached) */
        void lap(long ms) {
            long lap = Math.floorDiv(pos(ms), (long) span);
            if (lap == lapAt) return;
            lapAt = lap;
            if (lap == 0) { pat = pat0; y = y0; return; }
            Random r = new Random(id * 0x9E3779B97F4A7C15L + lap);
            pat = r.nextInt(PATTERNS.length); y = r.nextInt(Math.max(1, rows - h(pat) / 2)) - h(pat) / 4;
        }
        int state(long ms) { return (int) (Math.max(0, ms - t0) / period) ^ (out != Long.MIN_VALUE && ms - out < RESPAWN_MS ? 0x40000000 : 0); }
    }

    private final float pitch;
    private final Bitmap[] lit = new Bitmap[PATTERNS.length];   // each pattern's bulbs, lit
    private final Paint board = new Paint(), seedPaint = new Paint(Paint.FILTER_BITMAP_FLAG), dead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private Seed[] seeds = new Seed[0];
    private int[] shown = new int[0], deadCells = new int[0];   // dead cells as row * cols + col
    private int cols, rows;

    public BulbBoard(Context c) {
        pitch = Math.max(4f, PITCH_DP * c.getResources().getDisplayMetrics().density);
        int ps = Math.round(pitch);
        Bitmap tile = Bitmap.createBitmap(ps, ps, Bitmap.Config.ARGB_8888);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(UNLIT);
        new Canvas(tile).drawCircle(ps / 2f, ps / 2f, ps * 0.36f, b);
        board.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        for (int p = 0; p < PATTERNS.length; p++) lit[p] = seedBitmap(p, ps);
        seedPaint.setAlpha(LIT_ALPHA);
        dead.setColor(DEAD);
    }

    private static Bitmap seedBitmap(int p, int ps) {
        String[] s = PATTERNS[p];
        Bitmap bm = Bitmap.createBitmap(s[0].length() * ps, s.length * ps, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm); Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(LIT);
        for (int j = 0; j < s.length; j++)
            for (int i = 0; i < s[j].length(); i++)
                if (on(s, i, j)) cv.drawCircle((i + .5f) * ps, (j + .5f) * ps, ps * 0.36f, b);
        return bm;
    }

    private static boolean on(String[] s, int i, int j) { return j >= 0 && j < s.length && i >= 0 && i < s[j].length() && s[j].charAt(i) == '#'; }
    private static int w(int p) { return PATTERNS[p][0].length(); }
    private static int h(int p) { return PATTERNS[p].length; }
    private static long tick(long ms) { return ms - ms % TICK_MS; }

    /** The board for a view of w x h px: its dead bulbs and its seeds, scattered over the whole width (again only when the size changes) */
    private void layout(int wpx, int hpx, long ms) {
        int c = (int) Math.ceil(wpx / pitch), r = (int) Math.ceil(hpx / pitch);
        if (c == cols && r == rows) return;
        cols = c; rows = r;
        deadCells = new int[Math.max(1, Math.round(cols * rows * DEAD_SHARE))];
        for (int k = 0; k < deadCells.length; k++) deadCells[k] = rnd.nextInt(cols * rows);
        int n = Math.max(5, Math.min(30, cols * rows / AREA_PER_SEED));
        seeds = new Seed[n]; shown = new int[n];
        for (int k = 0; k < n; k++) { seeds[k] = new Seed(); place(seeds[k], k, ms); }
    }

    /** Seed k's first tilt, pace and spot, scattered over the board in the spot farthest from the seeds placed so far (or the first at least MIN_APART
     *  from all of them), and its loop: the board's width, the widest pattern and a gap of its own, so the seeds come round at different times */
    private void place(Seed s, int k, long ms) {
        s.id = k; s.pat0 = rnd.nextInt(PATTERNS.length); s.period = PERIODS[rnd.nextInt(PERIODS.length)]; s.t0 = tick(ms) + TICK_MS * rnd.nextInt(8);
        s.span = cols + LEAD + rnd.nextInt(MIN_APART);
        double best = -1; long bx = 0; int by = 0;
        for (int t = 0; t < 40; t++) {
            long cx = rnd.nextInt(Math.max(1, cols)) - w(s.pat0) / 2;
            int cy = rnd.nextInt(Math.max(1, rows - h(s.pat0) / 2)) - h(s.pat0) / 4;
            double near = Double.MAX_VALUE;
            for (int o = 0; o < k; o++) {
                Seed q = seeds[o];
                near = Math.min(near, Math.hypot((cx + w(s.pat0) / 2.0) - (q.x0 + w(q.pat0) / 2.0), (cy + h(s.pat0) / 2.0) - (q.y0 + h(q.pat0) / 2.0)));
            }
            if (near > best) { best = near; bx = cx; by = cy; if (near >= MIN_APART) break; }
        }
        s.x0 = bx; s.y0 = by;
    }


    /** True when a redraw would show something new: a seed stepped, came back from a tap, or left and was placed again */
    public boolean changed(long ms) {
        if (cols == 0) return true;
        long t = tick(ms); boolean any = false;
        for (int k = 0; k < seeds.length; k++) {
            int st = seeds[k].state(t);
            if (st != shown[k]) { shown[k] = st; any = true; }
        }
        return any;
    }

    public void draw(Canvas canvas, int wpx, int hpx, long ms) {
        long t = tick(ms);
        layout(wpx, hpx, t);
        canvas.drawRect(0, 0, wpx, hpx, board);
        for (Seed s : seeds) {
            if (s.out != Long.MIN_VALUE && t - s.out < RESPAWN_MS) continue;
            s.lap(t);
            canvas.drawBitmap(lit[s.pat], s.x(t) * pitch, s.y * pitch, seedPaint);
        }
        float r = pitch * 0.36f;
        for (int d : deadCells) canvas.drawCircle((d % cols + .5f) * pitch, (d / cols + .5f) * pitch, r, dead);   // dead bulbs: dark, lit or not
    }

    /** A tap at (x, y): puts out the seed it lands on, if any (the top one where two overlap); true when one went out */
    public boolean popAt(float x, float y, long ms) {
        long t = tick(ms); int bx = (int) Math.floor(x / pitch), by = (int) Math.floor(y / pitch);
        for (int k = seeds.length - 1; k >= 0; k--) {
            Seed s = seeds[k];
            if (s.out != Long.MIN_VALUE && t - s.out < RESPAWN_MS) continue;
            s.lap(t);
            int i = (int) (bx - s.x(t)), j = by - s.y;
            String[] p = PATTERNS[s.pat];
            if (j < 0 || j >= p.length || i < 0 || i >= p[j].length() || p[j].charAt(i) == '.') continue;
            s.out = t;
            return true;
        }
        return false;
    }

    public static long respawnMs() { return RESPAWN_MS; }

    /** How long the view may wait before asking changed() again: nothing moves between ticks */
    public static long tickMs() { return TICK_MS; }
}
