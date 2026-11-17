package dev.peanuts.ui;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import dev.peanuts.Companion;
import dev.peanuts.PeanutsPrefs;

/**
 * The launcher on a second display: the focused cover and its actions, else the logo.
 * Shown while MainActivity is started, hidden while a game runs. Home drops its header meanwhile.
 */
public final class BottomScreen {
    static final String TAG = "peanuts";
    private static final String MAIN = "dev.nxnative.android.MainActivity";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final long LAUNCH_STUCK_MS = 5000, LAUNCH_DIALOG_MS = 300, HANDOFF_MAX_MS = 8000, COVERED_MS = 150;

    /** A cover's card (Library.bottom) */
    static final class Card {
        static final int K_READY = 0, K_BUILD = 1, K_NO_PROFILE = 2, K_BUILDING = 3;
        String titleId = "", name = "";
        Bitmap icon;
        int kind;
        int pos = -1, cols = 1;         // place in the grid: the slide follows the D-pad
        String state = "";              // the cover's own words
        String companion;               // ready: the companion's version, null if none
        long played;                    // ready: last Play, 0 if never
        String found = "", space = "";  // to build: what the scan found
        Runnable primary, second;       // A and Y, null if none
        String primaryLabel = "", secondLabel = "";
        int primaryIcon;
    }

    /** A running or stopped build (Library.buildInfo) */
    static final class BuildInfo {
        String titleId = "", name = "", body = "";
        CharSequence step = "";
        Bitmap icon;
        int percent; boolean failed, indeterminate;
        Runnable stop, screensOff, open;
    }

    /** Play pressed: the panel stays until the game says what it shows there (Companion.STATE) */
    private static final class Launch {
        String titleId, name;
        Bitmap icon;
        boolean companion, lostFocus;   // companion: the start page shows
        String said;                    // loading, playing, shown, free, none, stopped
    }

    private static Activity owner;          // the MainActivity
    private static BottomPanel panel;
    private static DisplayManager dm;
    private static DisplayManager.DisplayListener displays;
    private static boolean watching, started, resumed;
    private static View header;             // Home's header; null off Home
    private static Runnable openSettings;
    private static Card card;
    private static BuildInfo building;
    private static Launch launch;
    private static long lastCardAt;
    private static Runnable pendingCard;
    private static Activity keysOn;
    private static boolean ownerFocused = true;

    private BottomScreen() {}

    // ---- Hooks ----

    /** MainActivity.buildHome (PEANUTS hook) */
    public static void home(Activity a, View homeHeader, Runnable settings) {
        watch(a);
        header = homeHeader;
        openSettings = settings;
        keys(a);
        applyHeader();
    }

    /** X opens Settings; losing window focus to a dialog dims the panel */
    private static void keys(Activity a) {
        if (keysOn == a) return;
        keysOn = a;
        View decor = a.getWindow().getDecorView();
        decor.addOnUnhandledKeyEventListener((v, ev) -> {
            if (ev.getKeyCode() != android.view.KeyEvent.KEYCODE_BUTTON_X || header == null || !showing() || launch != null) return false;
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) { Haptics.play(v, Haptics.Word.TICK); settings(); }
            return true;
        });
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(f -> {
            if (a != owner) return;
            ownerFocused = f;
            Launch l = launch;
            if (l != null) {   // the start page is not dimmed
                if (!f) { l.lostFocus = true; UI.removeCallbacks(launchDialog); UI.postDelayed(launchDialog, LAUNCH_DIALOG_MS); }
                else if (l.lostFocus && started) launchCancelled();   // a dialog came and went: no game
                return;
            }
            if (panel != null) panel.dim(!f);
        });
    }

    /** Off Home (Settings, a game's page): the logo */
    static void page(Activity a) {
        if (a != owner) return;
        header = null;
        card = null; building = null;
        show();
    }

    static boolean showing() { return panel != null && panel.isShowing(); }

    static boolean settings() {
        if (!showing() || openSettings == null || launch != null) return false;
        openSettings.run();
        return true;
    }

    /** A cover got focus; while the D-pad is held, only the one it stops on is shown */
    static void focus(Card c, BuildInfo b) {
        if (c == null || owner == null || launch != null) return;
        if (pendingCard != null) UI.removeCallbacks(pendingCard);
        long now = SystemClock.uptimeMillis();
        boolean rested = now - lastCardAt > 250;
        lastCardAt = now;
        pendingCard = () -> { pendingCard = null; card = c; building = c.kind == Card.K_BUILDING ? b : null; show(); };
        if (rested) pendingCard.run(); else UI.postDelayed(pendingCard, 90);
    }

    /** Build progress; null: the build is gone */
    static void build(BuildInfo b) {
        Card c = card;
        if (b == null) { if (c != null && c.kind == Card.K_BUILDING) { card = null; building = null; show(); } return; }
        if (c != null && c.kind == Card.K_BUILDING && b.titleId.equalsIgnoreCase(c.titleId)) {
            BuildInfo was = building;
            building = b;
            if (launch == null && was != null && panel != null && panel.isShowing() && panel.page() == BottomPanel.BUILD) { panel.updateBuild(b); return; }
            show();
        }
    }

    /** Nothing focused: the logo */
    static void idle() {
        if (pendingCard != null) { UI.removeCallbacks(pendingCard); pendingCard = null; }
        card = null; building = null;
        show();
    }

    /** The grid changed (a filter, Find): a game no longer in it leaves the bottom screen too */
    static void only(Set<String> ids) {
        Card c = card;
        if (c != null && !ids.contains(c.titleId)) idle();
    }

    /** A cover's art landed */
    static void icon(String titleId, Bitmap b) {
        if (card != null && card.titleId.equals(titleId)) card.icon = b;
        if (building != null && building.titleId.equals(titleId)) building.icon = b;
        if (launch != null && launch.titleId.equals(titleId)) launch.icon = b;
        if (showing()) panel.icon(titleId, b);
    }

    // ---- Play ----

    /** Library.play, before the launch. With a companion the cover grows into the start page; without, the card stays */
    static void launching(String titleId, String name, Bitmap icon, boolean companion) {
        if (!showing() || !started) return;
        Launch l = new Launch();
        l.titleId = titleId; l.name = name; l.icon = icon; l.companion = companion;
        launch = l;
        if (pendingCard != null) { UI.removeCallbacks(pendingCard); pendingCard = null; }
        if (companion) { panel.dim(false); panel.showLaunch(titleId, name, icon, "Starting the game"); }
        UI.removeCallbacks(launchStuck);
        UI.postDelayed(launchStuck, LAUNCH_STUCK_MS);
    }

    /** The launch did not start a game: back to the card */
    static void launchCancelled() {
        if (launch == null) return;
        launch = null;
        UI.removeCallbacks(launchStuck);
        UI.removeCallbacks(launchDialog);
        UI.removeCallbacks(launchEnd);
        show();
        if (panel != null) panel.dim(!ownerFocused);
    }

    /** Still in front a while after Play (an error, a toast): no game is coming */
    private static final Runnable launchStuck = () -> { if (launch != null && started) launchCancelled(); };
    /** Focus gone but still resumed: a dialog, not the game (a game pauses the launcher first) */
    private static final Runnable launchDialog = () -> { if (launch != null && resumed && !ownerFocused) launchCancelled(); };
    /** The game never said: let go */
    private static final Runnable launchEnd = BottomScreen::handOff;

    /** The game is in front and has said what it shows on this display: covered, or left to Android */
    private static void handOff() {
        Launch l = launch;
        if (l == null) return;
        launch = null;
        UI.removeCallbacks(launchEnd);
        UI.removeCallbacks(launchStuck);
        UI.removeCallbacks(launchDialog);
        if (started || !showing()) return;
        boolean covered = "loading".equals(l.said) || "playing".equals(l.said) || "shown".equals(l.said);
        if (covered) UI.postDelayed(() -> { if (!started && launch == null) hide(); }, COVERED_MS);   // its window has drawn by then
        else leave();
    }

    /** The launcher left the front: the page fades, then the display is Android's */
    private static void leave() {
        if (!showing()) return;
        BottomPanel p = panel;
        p.fadeOut(() -> { if (panel != p) return; if (!started) hide(); else p.appear(); });   // back already: fade in again
    }

    /** From the game's process (Companion.tell) */
    private static final BroadcastReceiver companionState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Launch l = launch;
            if (l == null) return;
            l.said = i.getStringExtra("state");
            if (!started) handOff();
        }
    };

    // ---- The panel ----

    private static void show() {
        Activity a = owner;
        if (a == null || !started || a.isFinishing() || a.isDestroyed() || !PeanutsPrefs.bottomScreen(a)) { if (launch == null) hide(); return; }
        Display d = panel != null && panel.getDisplay() != null && panel.getDisplay().isValid() ? panel.getDisplay() : secondDisplay(a);
        if (d == null) { hide(); return; }
        if (panel != null && (panel.getDisplay() == null || panel.getDisplay().getDisplayId() != d.getDisplayId())) { dismiss(); }
        boolean was = showing();
        if (panel == null) {
            try { panel = new BottomPanel(a, d); }
            catch (RuntimeException e) { Log.w(TAG, "bottom screen: no window on display " + d.getDisplayId() + ": " + e); panel = null; return; }
            panel.dim(!ownerFocused && launch == null);
            BackgroundFlow.stale();
            BottomPanel made = panel;
            // cancelled by Android (display change): make a new one
            made.setOnDismissListener(x -> { if (panel == made) { panel = null; applyHeader(); UI.post(BottomScreen::show); } });
        }
        try {
            if (!panel.isShowing()) panel.show();
        } catch (RuntimeException e) {   // display refuses app windows, or just went away
            Log.w(TAG, "bottom screen not shown on display " + d.getDisplayId() + ": " + e);
            dismiss();
            return;
        }
        panel.setActive(on(d));
        BackgroundFlow.field(a.getDisplay(), d);
        Launch l = launch;
        if (l != null && l.companion) panel.showLaunch(l.titleId, l.name, l.icon, "Starting the game");
        else if (building != null && card != null && card.kind == Card.K_BUILDING) panel.showBuild(building);
        else if (card != null && header != null) panel.showCard(card);
        else panel.showLogo(header != null);
        if (!was) panel.appear();
        applyHeader();
    }

    /** Hidden, not dismissed: a new window flashes black */
    private static void hide() {
        if (panel != null && panel.isShowing()) { panel.setActive(false); try { panel.hide(); } catch (RuntimeException ignored) { } }
        BackgroundFlow.clear();
        applyHeader();
    }

    private static void dismiss() {
        if (panel != null) { try { panel.dismiss(); } catch (RuntimeException ignored) { } panel = null; }
        BackgroundFlow.clear();
        applyHeader();
    }

    private static boolean on(Display d) { return d.getState() != Display.STATE_OFF; }

    /** Home's header: gone while the panel shows */
    private static void applyHeader() {
        View h = header;
        if (h == null) return;
        int want = showing() ? View.GONE : View.VISIBLE;
        if (h.getVisibility() != want) h.setVisibility(want);
    }

    /** Settings › Library › Bottom screen */
    static void settingChanged() {
        if (owner == null) return;
        if (PeanutsPrefs.bottomScreen(owner)) show(); else dismiss();
    }

    /** First valid other display. The Thor lists Screen-2 only as a presentation display, the emulator only in the plain list */
    static Display secondDisplay(Activity a) {
        DisplayManager m = (DisplayManager) a.getSystemService(Context.DISPLAY_SERVICE);
        if (m == null) return null;
        int own = a.getDisplay() != null ? a.getDisplay().getDisplayId() : Display.DEFAULT_DISPLAY;
        Map<Integer, Display> all = new LinkedHashMap<>();
        for (Display d : m.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) all.put(d.getDisplayId(), d);
        for (Display d : m.getDisplays()) all.put(d.getDisplayId(), d);
        for (Display d : all.values()) if (d.getDisplayId() != own && d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return d;
        return null;
    }

    // ---- Lifecycle ----

    private static void watch(Activity a) {
        if (owner != a) { if (owner != null) dismiss(); owner = a; started = resumed = a.hasWindowFocus(); }
        if (watching) return;
        watching = true;
        a.getApplicationContext().registerReceiver(companionState, new IntentFilter(Companion.STATE), Context.RECEIVER_NOT_EXPORTED);
        a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity x) {
                if (!isMain(x)) return;
                owner = x; started = true;
                launch = null; UI.removeCallbacks(launchEnd); UI.removeCallbacks(launchStuck); UI.removeCallbacks(launchDialog);   // back from the game
                listen(x);
                UI.post(BottomScreen::show);
            }
            @Override public void onActivityResumed(Activity x) { if (isMain(x) && x == owner) { resumed = true; if (!showing()) UI.post(BottomScreen::show); } }
            @Override public void onActivityStopped(Activity x) {
                if (x != owner) return;
                started = false;
                Launch l = launch;
                if (l == null || !showing()) { launch = null; leave(); return; }
                UI.removeCallbacks(launchStuck);
                UI.removeCallbacks(launchDialog);
                if (l.said != null) handOff(); else UI.postDelayed(launchEnd, HANDOFF_MAX_MS);
            }
            @Override public void onActivityDestroyed(Activity x) {
                if (x != owner) return;
                launch = null; UI.removeCallbacks(launchEnd); UI.removeCallbacks(launchStuck); UI.removeCallbacks(launchDialog);
                dismiss();
                if (dm != null && displays != null) { try { dm.unregisterDisplayListener(displays); } catch (RuntimeException ignored) { } }
                dm = null; displays = null; owner = null; header = null; openSettings = null; card = null; building = null; started = resumed = false;
                keysOn = null; ownerFocused = true;
            }
            @Override public void onActivityCreated(Activity x, Bundle b) {}
            @Override public void onActivityPaused(Activity x) { if (x == owner) resumed = false; }
            @Override public void onActivitySaveInstanceState(Activity x, Bundle b) {}
        });
        listen(a);
        if (started) UI.post(BottomScreen::show);
    }

    private static boolean isMain(Activity x) { return MAIN.equals(x.getClass().getName()); }

    private static void listen(Activity a) {
        if (displays != null) return;
        dm = (DisplayManager) a.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        displays = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) { show(); }
            @Override public void onDisplayRemoved(int id) { if (panel != null && panel.getDisplay() != null && panel.getDisplay().getDisplayId() == id) dismiss(); show(); }
            @Override public void onDisplayChanged(int id) {
                BackgroundFlow.stale();
                Display d = panel != null ? panel.getDisplay() : null;
                if (d == null || d.getDisplayId() != id) return;
                if (!panel.fits()) { dismiss(); show(); return; }
                if (showing()) panel.setActive(on(d));   // the screen turned off or on
            }
        };
        dm.registerDisplayListener(displays, UI);
    }
}
