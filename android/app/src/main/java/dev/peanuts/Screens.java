package dev.peanuts;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

/**
 * Settings › Library › Swap screens (GitHub issue #131). Some dual-screen setups attach the top screen over USB-C, so Android lists the built-in bottom
 * screen as its default display and the clip-on top one as an external display (the Retroid Pocket 5 with its Dual Screen Add-On). With the setting on,
 * a game starts on the external display and its companion takes the built-in one (Companion.secondDisplay). Off, nothing changes.
 */
public final class Screens {
    private Screens() {}

    /**
     * MainActivity and FrontendLaunch (PEANUTS hooks): the options a game's start goes with. With Swap screens on and an external display present, the
     * game's activity starts there, in a task of its own; otherwise null, the start as before.
     */
    public static Bundle gameStart(Context c, Intent intent) {
        if (!PeanutsPrefs.swapScreens(c)) return null;
        Display d = external(c);
        if (d == null) { Log.i(Companion.TAG, "Swap screens is on, but there is no other display: the game starts on this one"); return null; }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(Companion.TAG, "Swap screens: the game starts on display " + d.getDisplayId() + " \"" + d.getName() + "\"");
        return ActivityOptions.makeBasic().setLaunchDisplayId(d.getDisplayId()).toBundle();
    }

    /** The first valid display besides the default one, as the companion finds it (presentation displays first) */
    static Display external(Context c) {
        try {
            DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) return null;
            for (Display d : Companion.displays(dm)) if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return d;
        } catch (RuntimeException ignored) {}
        return null;
    }
}
