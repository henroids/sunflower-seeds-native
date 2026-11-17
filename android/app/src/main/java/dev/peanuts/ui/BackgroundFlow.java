package dev.peanuts.ui;

import android.animation.ValueAnimator;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.animation.AnimationUtils;

/**
 * One background across both screens while the bottom screen shows.
 * Both displays sit in one field (the second centred below, a hinge between) with one shared phase.
 * The drift itself is BananaBackgroundView's, unchanged.
 */
public final class BackgroundFlow {
    private static final float GAP_DP = 48;   // the hinge

    private static double phase;              // seconds, shared
    private static long lastMs = -1;
    private static final int[] AT = new int[2];
    private static int topId = -1, bottomId = -1;
    private static float topW, topH, bottomW;  // dp

    private BackgroundFlow() {}

    static void stale() { topId = -1; bottomId = -1; }

    /**
     * BananaBackgroundView.onDraw and popAt (PEANUTS hooks): the grid's drift in px (BananaGrid's wx, wy, not wrapped) that puts this view's grid into the
     * field, or null to draw as before. Arguments are upstream's own grid tilt and drift.
     */
    public static double[] drift(View v, float gridDeg, float speedX, float speedY) {
        Display top = BottomScreen.topDisplay(), bottom = BottomScreen.bottomDisplay(), d = v.getDisplay();
        if (top == null || bottom == null || d == null) return null;
        boolean onTop = d.getDisplayId() == top.getDisplayId();
        if (!onTop && d.getDisplayId() != bottom.getDisplayId()) return null;
        if (top.getDisplayId() != topId || bottom.getDisplayId() != bottomId) measure(top, bottom);
        advance();
        float density = v.getResources().getDisplayMetrics().density;
        v.getLocationOnScreen(AT);
        // this view's centre in the field (dp)
        float cx = (AT[0] + v.getWidth() / 2f) / density + (onTop ? 0 : (topW - bottomW) / 2f);
        float cy = (AT[1] + v.getHeight() / 2f) / density + (onTop ? 0 : topH + GAP_DP);
        // the grid turns about the top screen's centre: shift it by the distance, in the grid's axes
        double dx = topW / 2f - cx, dy = topH / 2f - cy, a = Math.toRadians(gridDeg), cos = Math.cos(a), sin = Math.sin(a);
        return new double[] {(dx * cos + dy * sin + phase * speedX) * density, (-dx * sin + dy * cos + phase * speedY) * density};
    }

    /** Frame time, at most 100 ms a step; still while animations are off */
    private static void advance() {
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (lastMs >= 0 && now > lastMs && ValueAnimator.areAnimatorsEnabled()) phase += Math.min(now - lastMs, 100) / 1000.0;
        lastMs = now;
    }

    @SuppressWarnings("deprecation")   // getRealMetrics
    private static void measure(Display top, Display bottom) {
        DisplayMetrics m = new DisplayMetrics();
        top.getRealMetrics(m);
        topW = m.widthPixels / m.density; topH = m.heightPixels / m.density;
        bottom.getRealMetrics(m);
        bottomW = m.widthPixels / m.density;
        topId = top.getDisplayId(); bottomId = bottom.getDisplayId();
    }
}
