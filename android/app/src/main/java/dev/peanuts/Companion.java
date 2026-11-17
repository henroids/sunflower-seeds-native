package dev.peanuts;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.peanuts.settings.CompanionSection;
import dev.peanuts.ui.GameScreen;

/**
 * Peanuts Native: the companion screen of a running game, shown on a second display (the AYN Thor's "Screen-2") as a Presentation owned by the
 * game's activity, in the game's own process (":game"), so the host's memory and input are a function call away. GameActivity calls start() once its
 * window is up and stop() when it is destroyed (the two PEANUTS hooks); without a second display nothing is shown and the game is unchanged.
 *
 * Taking the display at all is gated on the host's companion runtime (dm/peanuts/peanuts.c, Bridge.rtState): with no companion package for this title
 * (or one that failed to load), the second display is left alone entirely, exactly as it is without Peanuts - Android's own launcher/home stays on it
 * - unless the developer readout is explicitly turned on (PeanutsPrefs.developerReadout). RT_PENDING (the host has not decided yet, usually because the
 * game's main module has not loaded) is polled until it resolves or the game ends.
 */
public final class Companion {
    static final String TAG = "peanuts";
    private static final int DECIDE_POLL_MS = 200;
    private static CompanionPresentation shown;
    private static DisplayManager.DisplayListener listener;
    private static DisplayManager listenerDm;   // where the listener is registered: DisplayManager's listeners are process-wide, stop() must unregister it
    private static android.content.Context held;   // the game hold's context while it runs (GameHold)
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static Activity decidingGame;
    private static String decidingGameName;
    private static boolean stopped;
    /** Set once decide() resolves: whether this run is allowed to take the second display at all. Until then, a hot-plugged display is ignored. */
    private static boolean allowed;
    /** Set while the game's activity is stopped (Home or another app is in front, or the screen is off): the companion's window is off the second display
     * then, which goes back to whatever Android shows there. A Presentation is not closed by its activity stopping, so without this the companion stayed
     * on the bottom screen over the home screen or the other app (seen on the AYN Thor, 2026-11-15). */
    private static boolean away;
    /** Peanuts' page on the second display meanwhile (GameScreen.LOADING / PLAYING), -1 none */
    private static int wanted = -1;
    private static final long LOADING_MAX_MS = 60000;

    private Companion() {}

    /** To the launcher's bottom screen (BottomScreen, the main process): the second display is the companion's, or will not be */
    public static final String STATE = "dev.peanuts.COMPANION_STATE";

    private static void tell(android.content.Context c, String state) {
        if (c == null) return;
        try { c.sendBroadcast(new android.content.Intent(STATE).setPackage(c.getPackageName()).putExtra("state", state)); } catch (RuntimeException ignored) {}
    }

    /** Until decide(): the start page if a companion is installed, the game's cover if Settings keep one, else nothing */
    private static int firstPage(Activity game) {
        if (!PeanutsPrefs.bottomScreen(game)) return -1;
        String id = titleId(game);
        boolean off = CompanionSection.secondScreen(game, id).equals(CompanionSection.OFF);
        if (!off && companionInstalled(game, id)) return GameScreen.LOADING;
        return PeanutsPrefs.bottomScreenInGames(game) ? GameScreen.PLAYING : -1;
    }

    /** As the runtime finds it: a package beside the pack whose manifest names this title */
    private static boolean companionInstalled(Activity game, String id) {
        String pack = game.getIntent().getStringExtra("pack");
        java.io.File parent = pack == null ? null : new java.io.File(pack).getAbsoluteFile().getParentFile();
        return parent != null && CompanionSection.packageFor(new java.io.File(parent, "peanuts"), id) != null;
    }

    /** No companion after all: the game's cover if Settings keep one, else the display goes back to Android */
    private static void settle(Activity game) {
        wanted = PeanutsPrefs.bottomScreen(game) && PeanutsPrefs.bottomScreenInGames(game) ? GameScreen.PLAYING : -1;
        if (wanted < 0) GameScreen.close(); else if (!away) openPage(game);
        tellPage(game, "none");
    }

    private static void openPage(Activity game) {
        if (wanted < 0 || listenerDm == null) return;
        GameScreen.open(game, secondDisplay(game, listenerDm), titleId(game), decidingGameName, wanted);
    }

    /** What is really on the second display now; nothing: {@code otherwise} */
    private static void tellPage(Activity game, String otherwise) {
        int m = GameScreen.mode();
        tell(game, m == GameScreen.LOADING ? "loading" : m == GameScreen.PLAYING ? "playing" : otherwise);
    }

    private static String titleId(Activity game) { return String.valueOf(game.getIntent().getStringExtra("title_id")); }

    /** The game left the foreground (GameActivity.onStop): the companion's window is taken off the second display until {@link #back}. */
    public static void away() {
        away = true;
        GameScreen.away();
        if (shown != null) {
            // Hidden, not dismissed: a window made again came back black until the companion drew something new (its page is only sent when it changes)
            try { shown.hide(); } catch (RuntimeException ignored) {}
            try { Bridge.present(false); } catch (UnsatisfiedLinkError ignored) {}   // hide() runs no onStop: the runtime is told here, and held fingers are released
            Log.i(TAG, "the game left the foreground: the companion is off the second display until it is back");
        }
    }

    /** The game is in front again (GameActivity.onStart): the companion is shown again when this run had it. */
    public static void back(Activity game) {
        if (!away) return;
        away = false;
        if (stopped) return;
        if (wanted >= 0) { if (GameScreen.mode() < 0) openPage(game); else GameScreen.back(); }
        if (shown != null) {
            try { shown.show(); try { Bridge.present(true); } catch (UnsatisfiedLinkError ignored) {} return; } catch (RuntimeException e) { Log.w(TAG, "companion not shown again: " + e); shown = null; }   // its display went away meanwhile
        }
        if (allowed && listenerDm != null) show(game, decidingGameName, listenerDm);
    }

    /** Starts the game hold and, once the host has decided whether a companion loaded, shows it (or leaves the display alone). Keeps trying as displays come and go. */
    public static void start(Activity game, String gameName) {
        if (listener != null) return;
        // A pack from before main.orig.bin gets the file now, on a thread of its own, before the host starts: the runtime waits for it (dev.peanuts.pack.PackUpgrade)
        try { dev.peanuts.pack.PackUpgrade.atGameStart(game); } catch (RuntimeException e) { Log.w(TAG, "pack upgrade: " + e); }
        stopped = false;
        allowed = false;
        away = false;
        DisplayManager dm = (DisplayManager) game.getSystemService(Activity.DISPLAY_SERVICE);
        listener = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) { if (allowed) show(game, gameName, dm); else if (!away) openPage(game); }
            @Override public void onDisplayRemoved(int id) { if (allowed && shown != null && shown.getDisplay().getDisplayId() == id) { shown = null; show(game, gameName, dm); } if (!allowed) { GameScreen.close(); if (!away) openPage(game); } }
            @Override public void onDisplayChanged(int id) {}
        };
        dm.registerDisplayListener(listener, null);
        listenerDm = dm;
        decidingGame = game; decidingGameName = gameName;
        wanted = firstPage(game);
        openPage(game);
        tellPage(game, "free");
        if (wanted == GameScreen.LOADING) ui.postDelayed(() -> { if (!allowed && !stopped && decidingGame != null) settle(decidingGame); }, LOADING_MAX_MS);
        decide(dm);
        GameHold.start(game, gameName);
        held = game.getApplicationContext();
    }

    public static void stop() {
        tell(held, "stopped");
        stopped = true;
        wanted = -1;
        GameScreen.close();
        decidingGame = null;
        ui.removeCallbacksAndMessages(null);
        if (held != null) { GameHold.stop(held); held = null; }
        if (shown != null) { try { shown.dismiss(); } catch (RuntimeException ignored) {} shown = null; }
        if (listenerDm != null && listener != null) { try { listenerDm.unregisterDisplayListener(listener); } catch (RuntimeException ignored) {} }
        listener = null; listenerDm = null;   // unregistered: the listener holds the game activity, and DisplayManager's listeners outlive it
    }

    /** Polls Bridge.rtState() until it leaves RT_PENDING, then decides once whether to take the display. A title update/reinstall never changes this mid-run,
     * so one decision for the game's whole life is enough - onDisplayAdded/Removed above keep tracking the physical display after that. */
    private static void decide(DisplayManager dm) {
        Activity game = decidingGame;
        if (game == null || stopped || game.isFinishing() || game.isDestroyed()) return;
        int state = Bridge.rtStateSafe();
        boolean developerReadout = PeanutsPrefs.developerReadout(game);
        if (state == Bridge.RT_PENDING && !developerReadout) {
            ui.postDelayed(() -> decide(dm), DECIDE_POLL_MS);
            return;
        }
        if (CompanionSection.secondScreen(game, String.valueOf(game.getIntent().getStringExtra("title_id"))).equals(CompanionSection.OFF)) {
            Log.i(TAG, "second screen is Off in this title's settings: companion not shown");
            settle(game);
            return;
        }
        if (state != Bridge.RT_LOADED && !developerReadout) {
            Log.i(TAG, "no companion loaded for this title (state=" + state + "): the second display is left to Android, not taken over");
            settle(game);
            return;
        }
        if (state == Bridge.RT_FAILED) Log.w(TAG, "a companion package was found but the runtime refused it (see earlier \"peanuts\" logs); " +
                (developerReadout ? "showing the developer readout instead" : "second display left to Android"));
        allowed = true;
        show(game, decidingGameName, dm);
    }

    private static void show(Activity game, String gameName, DisplayManager dm) {
        if (shown != null || away || game.isFinishing() || game.isDestroyed()) return;   // away: shown again by back()
        Display d = secondDisplay(game, dm);
        if (d == null) { Log.i(TAG, "no second display: companion not shown"); tell(game, "none"); return; }
        try {
            shown = new CompanionPresentation(game, d, gameName);
            shown.show();
            Log.i(TAG, "companion on display " + d.getDisplayId() + " \"" + d.getName() + "\"");
            wanted = -1;
            GameScreen.closeSoon();
            tell(game, "shown");
        } catch (RuntimeException e) {   // the display refuses app windows, or went away between the query and show()
            Log.w(TAG, "companion not shown on display " + d.getDisplayId() + ": " + e);
            shown = null;
            settle(game);
        }
    }

    /** The first display that is not the game's own: the Thor lists its Screen-2 only under DISPLAY_CATEGORY_PRESENTATION, an emulator's overlay only in the plain list, so both are read. */
    static Display secondDisplay(Activity game, DisplayManager dm) {
        int own = game.getDisplay() != null ? game.getDisplay().getDisplayId() : Display.DEFAULT_DISPLAY;
        for (Display d : displays(dm)) if (d.getDisplayId() != own && d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return d;
        return null;
    }

    private static java.util.Collection<Display> displays(DisplayManager dm) {
        Map<Integer, Display> all = new LinkedHashMap<>();
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) all.put(d.getDisplayId(), d);
        for (Display d : dm.getDisplays()) all.put(d.getDisplayId(), d);
        return all.values();
    }

    /** Whether the device has a second display now (any valid display besides the default one, read as {@link #secondDisplay}): what a title's companion settings
     * default to while the user has not chosen (CompanionSection.secondScreen, PeanutsPrefs.installCompanion). True when the displays cannot be read: the old default */
    public static boolean hasSecondDisplay(android.content.Context c) {
        try {
            DisplayManager dm = (DisplayManager) c.getSystemService(android.content.Context.DISPLAY_SERVICE);
            if (dm == null) return true;
            for (Display d : displays(dm)) if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return true;
            return false;
        } catch (RuntimeException e) { return true; }
    }

    /** The game's launch environment: PN_COMPANION=off when the title's Second screen is Off (chosen, or the default on a device without a second display), so the
     * host loads no companion at all (dm/peanuts/peanuts.c rt_start) instead of running one that has nowhere to show */
    public static void env(android.content.Context c, String titleId, java.util.List<String> env) {
        if (!CompanionSection.secondScreen(c, titleId).equals(CompanionSection.OFF)) return;
        for (String s : env) if (s.startsWith("PN_COMPANION=")) return;
        env.add("PN_COMPANION=off");
    }
}
