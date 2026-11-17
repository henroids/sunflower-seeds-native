package dev.peanuts.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
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

import dev.peanuts.PeanutsPrefs;

/**
 * The launcher on a second display: the focused cover and its actions, else the logo.
 * Shown while MainActivity is started, hidden while a game runs. Home drops its header meanwhile.
 */
public final class BottomScreen {
    static final String TAG = "peanuts";
    private static final String MAIN = "dev.nxnative.android.MainActivity";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** A cover's card (Library.bottom) */
    static final class Card {
        static final int K_READY = 0, K_BUILD = 1, K_NO_PROFILE = 2, K_BUILDING = 3;
        String titleId = "", name = "";
        Bitmap icon;
        int kind;
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

    private static Activity owner;          // the MainActivity
    private static BottomPanel panel;
    private static DisplayManager dm;
    private static DisplayManager.DisplayListener displays;
    private static boolean watching, started;
    private static View header;             // Home's header; null off Home
    private static Runnable openSettings;
    private static Card card;
    private static BuildInfo building;
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
            if (ev.getKeyCode() != android.view.KeyEvent.KEYCODE_BUTTON_X || header == null || !showing()) return false;
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) { Haptics.play(v, Haptics.Word.TICK); settings(); }
            return true;
        });
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(f -> {
            if (a != owner) return;
            ownerFocused = f;
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

    /** Both displays while the panel shows (BackgroundFlow), else null */
    static Display topDisplay() { Activity a = owner; return a != null && showing() ? a.getDisplay() : null; }
    static Display bottomDisplay() { return showing() ? panel.getDisplay() : null; }

    static boolean settings() {
        if (!showing() || openSettings == null) return false;
        openSettings.run();
        return true;
    }

    /** A cover got focus; while the D-pad is held, only the one it stops on is shown */
    static void focus(Card c, BuildInfo b) {
        if (c == null || owner == null) return;
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
            if (was != null && panel != null && panel.isShowing() && panel.page() == BottomPanel.BUILD) { panel.updateBuild(b); return; }
            show();
        }
    }

    /** Nothing focused: the logo */
    static void idle() {
        if (pendingCard != null) { UI.removeCallbacks(pendingCard); pendingCard = null; }
        card = null; building = null;
        show();
    }

    // ---- The panel ----

    private static void show() {
        Activity a = owner;
        if (a == null || !started || a.isFinishing() || a.isDestroyed() || !PeanutsPrefs.bottomScreen(a)) { hide(); return; }
        Display d = panel != null && panel.getDisplay() != null && panel.getDisplay().isValid() ? panel.getDisplay() : secondDisplay(a);
        if (d == null) { hide(); return; }
        if (panel != null && (panel.getDisplay() == null || panel.getDisplay().getDisplayId() != d.getDisplayId())) { dismiss(); }
        if (panel == null) {
            try { panel = new BottomPanel(a, d); }
            catch (RuntimeException e) { Log.w(TAG, "bottom screen: no window on display " + d.getDisplayId() + ": " + e); panel = null; return; }
            panel.dim(!ownerFocused);
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
        if (building != null && card != null && card.kind == Card.K_BUILDING) panel.showBuild(building);
        else if (card != null && header != null) panel.showCard(card);
        else panel.showLogo(header != null);
        applyHeader();
    }

    /** Hidden, not dismissed: a new window flashes black */
    private static void hide() {
        if (panel != null && panel.isShowing()) { try { panel.hide(); } catch (RuntimeException ignored) { } }
        applyHeader();
    }

    private static void dismiss() {
        if (panel != null) { try { panel.dismiss(); } catch (RuntimeException ignored) { } panel = null; }
        applyHeader();
    }

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
        if (owner != a) { if (owner != null) dismiss(); owner = a; started = a.hasWindowFocus(); }
        if (watching) return;
        watching = true;
        a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity x) { if (isMain(x)) { owner = x; started = true; listen(x); UI.post(BottomScreen::show); } }
            @Override public void onActivityResumed(Activity x) { if (isMain(x) && x == owner && !showing()) UI.post(BottomScreen::show); }
            @Override public void onActivityStopped(Activity x) { if (x == owner) { started = false; hide(); } }
            @Override public void onActivityDestroyed(Activity x) {
                if (x != owner) return;
                dismiss();
                if (dm != null && displays != null) { try { dm.unregisterDisplayListener(displays); } catch (RuntimeException ignored) { } }
                dm = null; displays = null; owner = null; header = null; openSettings = null; card = null; building = null; started = false;
                keysOn = null; ownerFocused = true;
            }
            @Override public void onActivityCreated(Activity x, Bundle b) {}
            @Override public void onActivityPaused(Activity x) {}
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
                if (panel != null && panel.getDisplay() != null && panel.getDisplay().getDisplayId() == id && !panel.fits()) { dismiss(); show(); }
            }
        };
        dm.registerDisplayListener(displays, UI);
    }
}
