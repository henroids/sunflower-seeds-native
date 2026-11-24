package dev.peanuts.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import java.util.HashMap;

/**
 * Home's background on the test app's scoreboard look (Scoreboard.ON; BananaBackgroundView hands its drawing, ticks and taps here): the whole screen is a
 * board of bulbs, and the seeds (the logo, turned 15 degrees, as lit bulbs) march across it one bulb column at a time, as on a stadium scoreboard. Each step
 * the trailing column goes out first and the leading column lights LIGHT_MS later. Seeds sit on a lattice (every PER_X bulbs across, every PER_Y down, odd
 * rows half a period over) that repeats every PER_X bulbs, so the march wraps without a seam. A tap on a seed puts it out for RESPAWN_MS.
 * Cheap: the unlit board is one shader-filled rect, a seed is one of two prebaked bitmaps (all lit, or with its trailing column out), and the view redraws
 * only when the state changes, about four times a second
 */
public final class BulbBoard {
    /** The seed as bulbs: # lit, o a stripe (out), . no bulb of the seed's; 15 x 20, turned 15 degrees to the left (as sb_seed.png) */
    private static final String[] SEED = {
        ".....#####.....",
        "...#########...",
        "..########o##..",
        "..####o###o###.",
        ".#o###o#####o##",
        "###o###o####o##",
        ".#o####o####o##",
        "##o####o####o##",
        "##o####o####o##",
        "###o####o###o##",
        ".###o###o###o##",
        "..##o###o###o#.",
        "..##o####o##o#.",
        "...###o##o#o##.",
        "....##o##o#o##.",
        ".....##o#o###..",
        ".....#####o###.",
        ".......######..",
        "........####...",
        ".........###...",
    };
    private static final int SW = 15, SH = SEED.length;
    private static final float PITCH_DP = 5f;
    private static final int PER_X = 32, PER_Y = 26;                       // the lattice, in bulbs
    private static final long STEP_MS = 520, LIGHT_MS = 170, RESPAWN_MS = 5000;
    private static final int LIT = 0xFFE9A93F, LIT_ALPHA = 92, UNLIT = 0xFF141912;

    private final float pitch;
    private final Bitmap lit, trail;           // the seed with all its bulbs lit; with its trailing (left) column of each row out
    private final Paint board = new Paint(), seed = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final HashMap<Long, Long> popped = new HashMap<>();
    private final long t0 = android.os.SystemClock.uptimeMillis();
    private long shown = Long.MIN_VALUE;

    public BulbBoard(Context c) {
        pitch = Math.max(4f, PITCH_DP * c.getResources().getDisplayMetrics().density);
        int ps = Math.round(pitch);
        Bitmap tile = Bitmap.createBitmap(ps, ps, Bitmap.Config.ARGB_8888);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(UNLIT);
        new Canvas(tile).drawCircle(ps / 2f, ps / 2f, ps * 0.36f, b);
        board.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        lit = seedBitmap(ps, false);
        trail = seedBitmap(ps, true);
        seed.setAlpha(LIT_ALPHA);
    }

    private static Bitmap seedBitmap(int ps, boolean trailingOut) {
        Bitmap bm = Bitmap.createBitmap(SW * ps, SH * ps, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm); Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(LIT);
        for (int j = 0; j < SH; j++)
            for (int i = 0; i < SW; i++)
                if (on(i, j) && !(trailingOut && !on(i - 1, j))) cv.drawCircle((i + .5f) * ps, (j + .5f) * ps, ps * 0.36f, b);   // trailing: lit with nothing lit to its left
        return bm;
    }

    private static boolean on(int i, int j) { return i >= 0 && i < SW && j >= 0 && j < SH && SEED[j].charAt(i) == '#'; }

    /** The march at uptime ms: the step (bulb columns moved) times two, plus 1 while the trailing columns are out before the leading ones light */
    private long state(long ms) { long e = Math.max(0, ms - t0), s = e / STEP_MS; return 2 * s + (e % STEP_MS < LIGHT_MS ? 1 : 0); }

    /** True when a redraw would show something new (a step, a seed back from a tap) */
    public boolean changed(long ms) {
        boolean back = !popped.isEmpty() && popped.values().removeIf(at -> ms - at >= RESPAWN_MS);   // a seed coming back is a change too
        long s = state(ms);
        if (s == shown && !back) return false;
        shown = s;
        return true;
    }

    public void draw(Canvas canvas, int w, int h, long ms) {
        canvas.drawRect(0, 0, w, h, board);
        long st = state(ms); boolean trailing = (st & 1) == 1; long pos = st / 2 - (trailing ? 1 : 0);   // while the trailing columns are out, the seeds are still at the last step
        Bitmap b = trailing ? trail : lit;
        int cols = (int) Math.ceil(w / pitch), rows = (int) Math.ceil(h / pitch);
        for (int r = -1; r * PER_Y < rows; r++) {
            int off = (r & 1) != 0 ? PER_X / 2 : 0, y = r * PER_Y + 3;
            for (long n = Math.floorDiv((long) (-SW - off) - pos, (long) PER_X); n * PER_X + off + pos < cols; n++) {
                Long at = popped.isEmpty() ? null : popped.get(key(r, n));
                if (at != null && ms - at < RESPAWN_MS) continue;
                canvas.drawBitmap(b, (n * PER_X + off + pos) * pitch, y * pitch, seed);
            }
        }
    }

    /** A tap at (x, y): puts out the seed it lands on (its bounding box), if any; true when one went out */
    public boolean popAt(float x, float y, long ms) {
        long st = state(ms), pos = st / 2 - ((st & 1) == 1 ? 1 : 0);
        int bx = (int) Math.floor(x / pitch), by = (int) Math.floor(y / pitch);
        int r = Math.floorDiv(by - 3, PER_Y), off = (r & 1) != 0 ? PER_X / 2 : 0;
        long n = Math.floorDiv((long) (bx - off) - pos, (long) PER_X);
        int i = (int) (bx - (n * PER_X + off + pos)), j = by - (r * PER_Y + 3);
        if (i < 0 || i >= SW || j < 0 || j >= SH || SEED[j].charAt(i) == '.') return false;
        long k = key(r, n);
        Long at = popped.get(k);
        if (at != null && ms - at < RESPAWN_MS) return false;
        popped.put(k, ms);
        shown = Long.MIN_VALUE;
        return true;
    }

    public static long respawnMs() { return RESPAWN_MS; }

    private static long key(int r, long n) { return ((long) r << 32) | (n & 0xFFFFFFFFL); }
}
