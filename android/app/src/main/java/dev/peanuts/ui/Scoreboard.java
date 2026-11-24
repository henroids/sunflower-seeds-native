package dev.peanuts.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Sunflower Seeds test app's look, "Scoreboard" (design option C): square panels on near-black, a purple header strip, labels in a pixel font (Silkscreen),
 * numbers in a dot-matrix font (VT323), text in Space Grotesk, mint for the selection and the main button. Only the test app (applicationId …sunflowertest)
 * has it: its colours and fonts come from android/app/src/sunflower-test (res and assets, build.gradle), and every hook asks ON first, so every other build
 * keeps the upstream look. The hooks: Ui / PUi round (no corners), face (the text font), section (pixel labels), header (the strip), badge (the bulb seed);
 * BananaBackgroundView (BulbBoard: the screen as a bulb board, the seeds marching across it); MainActivity (the status row on Home). The fonts are OFL-1.1 (assets/fonts/*.txt)
 */
public final class Scoreboard {
    private Scoreboard() {}

    public static final boolean ON = dev.nxnative.android.BuildConfig.APPLICATION_ID.endsWith(".sunflowertest");
    public static final int STRIP = 0xFF5A59A6, ON_STRIP = 0xFFF4F2FF, CELL = 0xFF0B0F09, LINE = 0xFF2E3524, LABEL = 0xFF8A937A, MINT = 0xFFBFF2CC, LAVENDER = 0xFFD9D4F5;

    private static AssetManager assets;
    private static Typeface pixel, led;
    private static final SparseArray<Typeface> sans = new SparseArray<>();

    /** Loads the fonts once (the first screen's onCreate); without them the hooks keep the system font */
    public static void init(Context c) {
        if (!ON || assets != null) return;
        assets = c.getApplicationContext().getAssets();
        try { pixel = Typeface.createFromAsset(assets, "fonts/Silkscreen-Regular.ttf"); led = Typeface.createFromAsset(assets, "fonts/VT323-Regular.ttf"); }
        catch (RuntimeException e) { pixel = led = null; }
    }

    /** The seed in amber bulbs, turned 15 degrees (res/drawable-nodpi/sb_seed.png, the test app's only), or null: the caller's own drawing */
    public static android.graphics.drawable.Drawable seed(Context c) {
        if (!ON) return null;
        int id = c.getResources().getIdentifier("sb_seed", "drawable", c.getPackageName());   // by name: the other builds have no such drawable
        return id == 0 ? null : c.getDrawable(id);
    }

    /** The seed for a logo spot (the setup and empty cards): the bulb seed on the scoreboard look, else the drawing res */
    public static void logo(android.widget.ImageView v, int res) { android.graphics.drawable.Drawable d = seed(v.getContext()); if (d != null) v.setImageDrawable(d); else v.setImageResource(res); }

    /** A row's icon in amber bulbs in place of its emoji, or null: the emoji stays. The art is res/drawable-nodpi/sb_ic_<the emoji's code points in hex,
     *  joined by _, without the variation selector FE0F> (🔑 is sb_ic_1f511), found by name */
    public static android.graphics.drawable.Drawable icon(Context c, String emoji) {
        if (!ON || emoji == null || emoji.isEmpty()) return null;
        StringBuilder n = new StringBuilder("sb_ic");
        emoji.codePoints().filter(cp -> cp != 0xFE0F).forEach(cp -> n.append('_').append(Integer.toHexString(cp)));
        android.graphics.drawable.Drawable d = drawable(c, n.toString());
        return d == null ? null : new android.graphics.drawable.InsetDrawable(d, dp(c, 4));
    }

    /** An icon button's art in amber bulbs (res/drawable-nodpi/sb_<its drawable's name>: the Settings gear, the FAQ book), or null: its own stays */
    public static android.graphics.drawable.Drawable button(Context c, int res) {
        if (!ON) return null;
        try { return drawable(c, "sb_" + c.getResources().getResourceEntryName(res)); } catch (android.content.res.Resources.NotFoundException e) { return null; }
    }

    private static android.graphics.drawable.Drawable drawable(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());   // by name: the other builds have none of these
        return id == 0 ? null : c.getDrawable(id);
    }

    /** Puts a row's bulb icon (icon) on its emoji tile, if it has one */
    public static void tile(TextView tile, String emoji) {
        android.graphics.drawable.Drawable d = icon(tile.getContext(), emoji);
        if (d == null) return;
        tile.setText("");
        tile.setForeground(d);
    }

    /** A shape's corner radius: none on the scoreboard */
    public static float radius(float px) { return ON ? 0 : px; }

    /** Space Grotesk at a weight (300 to 700), or null: the caller's own font */
    public static Typeface face(int weight) {
        if (!ON || assets == null) return null;
        int w = Math.max(300, Math.min(700, weight));
        synchronized (sans) {
            Typeface t = sans.get(w);
            if (t == null) {
                try { t = new Typeface.Builder(assets, "fonts/SpaceGrotesk.ttf").setFontVariationSettings("'wght' " + w).build(); }
                catch (RuntimeException e) { t = null; }
                if (t == null) return null;
                sans.put(w, t);
            }
            return t;
        }
    }

    /** A label in the pixel font, upper case (section titles, the strip, the cells' labels) */
    public static void pixel(TextView t, float sp) {
        if (pixel == null) return;
        t.setTypeface(pixel);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setTextSize(sp);
    }

    /** The page header as the purple strip: its row gets the strip, the title the pixel font */
    public static void strip(Context c, LinearLayout header, TextView title) {
        if (!ON) return;
        int p = dp(c, 16);
        header.setBackgroundColor(STRIP);
        header.setPadding(p, dp(c, 6), dp(c, 8), dp(c, 6));
        title.setTextColor(ON_STRIP);
        pixel(title, 20);
    }

    /** Home's status row: one cell a value, label above, the value in the dot-matrix font (label, value pairs) */
    public static View status(Context c, String... pairs) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(); g.setColor(CELL); g.setStroke(dp(c, 2), LINE);
            cell.setBackground(g);
            cell.setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 12));
            TextView l = new TextView(c); l.setText(pairs[i]); l.setTextColor(LABEL); l.setGravity(Gravity.CENTER); pixel(l, 10);
            TextView v = new TextView(c); v.setText(pairs[i + 1]); v.setTextColor(i / 2 % 2 == 0 ? MINT : LAVENDER); v.setGravity(Gravity.CENTER);
            v.setSingleLine(); v.setEllipsize(TextUtils.TruncateAt.END); v.setTextSize(34); if (led != null) v.setTypeface(led);
            cell.addView(l); cell.addView(v);
            cell.setContentDescription(pairs[i] + ": " + pairs[i + 1]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.setMarginStart(dp(c, 8));
            row.addView(cell, lp);
        }
        return row;
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
}
