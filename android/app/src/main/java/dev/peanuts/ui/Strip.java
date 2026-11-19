package dev.peanuts.ui;

import dev.nxnative.android.R;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.ProgressBar;

/**
 * The app's one progress bar: the building cover's strip (Library), on the build card, the Prepare card (Ondevice), the second screen's build card
 * (BottomPanel), the build log's and the GPU driver page's bars too, in place of the platform's thin horizontal bar that Ui.progress / PUi.progress make
 * (PUi stays a verbatim copy of Ui; upstream's own screens, the app update and the game's shader prewarm, keep theirs). 6 dp high, rounded ends, banana deep on its track; the height is fixed
 * here (min and max height), so any layout gives the same bar. Indeterminate (upstream's Prepare, which reports no percent): the same 6 dp track with a
 * banana-deep piece a third of its width gliding across ({@link Sweep}), not the platform's thin animated line.
 */
public final class Strip {
    private Strip() {}

    /** On a card: the track is the card's stroke colour */
    public static ProgressBar card(Context c) { return of(c, PUi.col(c, R.color.bn_card_stroke), false); }

    public static ProgressBar of(Context c, int track, boolean indeterminate) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setIndeterminate(indeterminate);
        GradientDrawable fill = PUi.round(PUi.col(c, R.color.bn_banana_deep), PUi.dp(c, 3), 0, 0);
        LayerDrawable ld = new LayerDrawable(new Drawable[] {PUi.round(track, PUi.dp(c, 3), 0, 0), new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        p.setProgressDrawable(ld);
        p.setIndeterminateDrawable(new Sweep(track, PUi.col(c, R.color.bn_banana_deep), PUi.dp(c, 6)));
        p.setMinHeight(PUi.dp(c, 6));
        p.setMaxHeight(PUi.dp(c, 6));
        return p;
    }

    /** The indeterminate bar: the track, and a rounded piece a third of its width that glides from the left edge to the right one and starts over (1.6 s
     *  a pass). With Android's animations off it rests in the middle. ProgressBar starts and stops it, as it does any Animatable indeterminate drawable */
    static final class Sweep extends android.graphics.drawable.Drawable implements Animatable {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int height;
        private final RectF r = new RectF();
        private ValueAnimator anim;
        private float at = 0.5f;   // where the piece's centre is, 0 = left edge, 1 = right edge

        Sweep(int trackColor, int fillColor, int heightPx) {
            track.setColor(trackColor);
            fill.setColor(fillColor);
            height = heightPx;
        }

        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float h = Math.min(height, b.height()), top = b.centerY() - h / 2f, rad = h / 2f, w = b.width(), piece = w / 3f;
            r.set(b.left, top, b.right, top + h);
            c.drawRoundRect(r, rad, rad, track);
            float mid = b.left + at * (w + piece) - piece / 2f;   // enters at the left edge, leaves at the right one
            r.set(Math.max(b.left, mid - piece / 2f), top, Math.min(b.right, mid + piece / 2f), top + h);
            if (r.width() > 0) c.drawRoundRect(r, rad, rad, fill);
        }

        @Override public int getIntrinsicHeight() { return height; }
        @Override public void setAlpha(int alpha) { track.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { track.setColorFilter(cf); fill.setColorFilter(cf); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public void start() {
            if (isRunning()) return;
            if (!ValueAnimator.areAnimatorsEnabled()) { at = 0.5f; invalidateSelf(); return; }
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            anim.addUpdateListener(a -> { at = (float) a.getAnimatedValue(); invalidateSelf(); });
            anim.start();
        }

        @Override public void stop() { if (anim != null) { anim.cancel(); anim = null; } }
        @Override public boolean isRunning() { return anim != null && anim.isRunning(); }
    }
}
