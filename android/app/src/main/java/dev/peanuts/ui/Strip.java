package dev.peanuts.ui;

import dev.nxnative.android.R;

import android.content.Context;
import android.content.res.ColorStateList;
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
 * here (min and max height), so any layout gives the same bar. Indeterminate: the same height, banana deep.
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
        p.setIndeterminateTintList(ColorStateList.valueOf(PUi.col(c, R.color.bn_banana_deep)));
        p.setMinHeight(PUi.dp(c, 6));
        p.setMaxHeight(PUi.dp(c, 6));
        return p;
    }
}
