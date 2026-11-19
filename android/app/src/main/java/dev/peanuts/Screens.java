package dev.peanuts;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/**
 * Settings › Screens › Swap screens (GitHub issue #131). Some dual-screen setups attach the top screen over USB-C, so Android lists the built-in bottom
 * screen as its default display and the clip-on top one as an external display (the Retroid Pocket 5 with its Dual Screen Add-On). With the setting on,
 * a game starts on the external display and its companion takes the built-in one (Companion.secondDisplay). Off, nothing changes.
 */
public final class Screens {
    private Screens() {}

    /**
     * Start on the external display when requested and allowed. If it disappears or Android
     * rejects the launch, retry on the launcher's display with the original intent.
     */
    public static void startGame(Activity launcher, Intent intent) {
        if (dev.peanuts.users.WhoPlaysActivity.ask(launcher, intent)) return;   // Who's playing? first, with two or more users (Settings › Users); it starts the game
        Display d = PeanutsPrefs.swapScreens(launcher) ? external(launcher) : null;
        if (d != null && startOn(launcher, intent, d.getDisplayId())) {
            GameReturn.startedOn(launcher, d.getDisplayId());
            return;
        }
        GameReturn.startedOn(launcher, Display.INVALID_DISPLAY);
        launcher.startActivity(intent);
    }

    /**
     * Starts intent in a task of its own on display id, when that display is still there and Android allows the start on it. False: nothing was started,
     * and the caller starts it as before. GameReturn takes this way too, so the app's icon brings a game back to the display it was started on.
     */
    static boolean startOn(Activity launcher, Intent intent, int id) {
        Intent there = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            DisplayManager dm = launcher.getSystemService(DisplayManager.class);
            Display d = dm == null || id == Display.INVALID_DISPLAY ? null : dm.getDisplay(id);
            ActivityManager manager = launcher.getSystemService(ActivityManager.class);
            if (d != null && d.isValid() && manager != null && manager.isActivityStartAllowedOnDisplay(launcher, id, there)) {
                launcher.startActivity(there, ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle());
                return true;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(Companion.TAG, "Swap screens: display " + id + " refused the game", e);
        }
        return false;
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
