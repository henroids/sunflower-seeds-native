package dev.peanuts.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Peanuts cracked (CrackedCount's rules) kept in the app's preferences: the taps on the background are upstream's own counter (Prefs.bananasPopped, read
 * here), the points of builds, shaders, play time and firsts are Peanuts' ("peanuts" preferences, one line). About and the second display listen for
 * changes. Every call runs in the launcher's process.
 */
public final class Cracked {
    private Cracked() {}

    private static final String FILE = "peanuts", KEY = "cracked_points";
    private static final String UP_FILE = "launcher", UP_TAPS = "bananas_popped";   // upstream's Prefs (its file and key; read only)
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final List<Runnable> listeners = new ArrayList<>();
    private static SharedPreferences.OnSharedPreferenceChangeListener tapWatch;   // held: SharedPreferences keeps its listeners weakly

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    static synchronized CrackedCount.State state(Context c) { return CrackedCount.load(prefs(c).getString(KEY, null)); }

    private static synchronized void save(Context c, CrackedCount.State s) {
        prefs(c).edit().putString(KEY, CrackedCount.save(s)).apply();
        changed();
    }

    /** Upstream's preferences file, opened as upstream opens it (Prefs.open: MODE_MULTI_PROCESS, so a write of another process is seen) */
    @SuppressWarnings("deprecation")
    private static SharedPreferences upstream(Context c) { return c.getApplicationContext().getSharedPreferences(UP_FILE, Context.MODE_MULTI_PROCESS); }

    /** The peanuts tapped on the background, all time (upstream's counter: the old "Bananas Popped" carries over) */
    static long taps(Context c) {
        try { return upstream(c).getLong(UP_TAPS, 0); }
        catch (ClassCastException e) { return 0; }
    }

    /** Peanuts cracked: the taps and the points */
    public static long total(Context c) { return CrackedCount.total(state(c), taps(c)); }

    /** A build of a game version ended well, having prepared shaders shaders (0 when not known) */
    public static synchronized void built(Context c, String titleId, String version, long shaders) {
        CrackedCount.State s = state(c);
        if (CrackedCount.built(s, titleId, version, shaders) > 0) save(c, s);
    }

    /** A game was played for ms */
    public static synchronized void played(Context c, long ms) {
        CrackedCount.State s = state(c);
        long was = s.playMs;
        CrackedCount.played(s, ms);
        if (s.playMs != was || ms > 0) save(c, s);
    }

    /** A game started */
    public static synchronized void started(Context c, String titleId) {
        CrackedCount.State s = state(c);
        if (CrackedCount.started(s, titleId) > 0) save(c, s);
    }

    // ---- Play time ----

    private static final String RUN = "cracked_run", SETTLED = "cracked_settled";
    /** One worker, in order: a Play's settling and noting, then every settle after it (back in the launcher, About) */
    private static final java.util.concurrent.Executor BG = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "peanuts-cracked"));

    /**
     * The launcher starts a game (GameReturn.remember: Home's Play, a shortcut, a frontend): the run before it is settled (its play time counted), the
     * title's first start counts, and this start is noted, so the record the game's screen writes is known as a player's run (a build's shader capture
     * writes one too, and is not). Reading the record and asking Android for the processes happen on the worker, not at the Play's tap (igawa6's note on
     * #315); the Play's time is taken here. Should the game's screen write its record before the worker gets to it, that record counts nothing until its
     * start is noted (CrackedCount.toCount), and the run before it was settled when the launcher came back, before this Play
     */
    public static void starting(Context c, String titleId) {
        Context app = c.getApplicationContext();
        String noted = titleId.toUpperCase(java.util.Locale.ROOT) + "@" + System.currentTimeMillis();
        BG.execute(() -> {
            settleNow(app);
            started(app, titleId);
            prefs(app).edit().putString(RUN, noted).apply();
        });
    }

    /** Back in the launcher (or About shows): a run that has ended is counted, off the main thread */
    public static void settle(Context c) { Context app = c.getApplicationContext(); BG.execute(() -> settleNow(app)); }

    /**
     * The game's run record (files/last-exit.txt: title, pid, start, written by its screen) against the start noted above (CrackedCount.toCount): once its
     * process has ended, the time from its start to the process's end (Android's exit record of that pid) is played time, at most six hours a run (a game
     * left in the background keeps its process). Counted once per run. On the worker only: it reads a file and asks Android
     */
    private static synchronized void settleNow(Context c) {
        SharedPreferences p = prefs(c);
        CrackedCount.Run run = CrackedCount.toCount(record(new java.io.File(c.getFilesDir(), "last-exit.txt")), p.getString(RUN, null), p.getString(SETTLED, null));
        if (run == null) return;
        long end = ended(c, run.pid);
        if (end <= 0) return;   // still running, or not known yet
        p.edit().putString(SETTLED, run.key).apply();
        played(c, CrackedCount.playedMs(run.start, end));
    }

    /** When the process of that pid ended (Android's exit record), 0 when it runs or Android has no record of it */
    private static long ended(Context c, int pid) {
        android.app.ActivityManager am = c.getSystemService(android.app.ActivityManager.class);
        if (am == null) return 0;
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
        if (running != null) for (android.app.ActivityManager.RunningAppProcessInfo r : running) if (r.pid == pid) return 0;
        try {
            for (android.app.ApplicationExitInfo e : am.getHistoricalProcessExitReasons(c.getPackageName(), pid, 1)) if (e.getPid() == pid) return e.getTimestamp();
        } catch (RuntimeException ignored) { /* no record */ }
        return 0;
    }

    private static java.util.Map<String, String> record(java.io.File f) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (!f.isFile()) return out;
        try {
            for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());   // a later line wins (state=..., log=...)
            }
        } catch (java.io.IOException ignored) { /* no record */ }
        return out;
    }

    /** The player saved a Mii of their own */
    public static synchronized void mii(Context c) {
        CrackedCount.State s = state(c);
        if (CrackedCount.mii(s) > 0) save(c, s);
    }

    /** r runs on the main thread when the total changed (a tap, a build, ...) until it is passed to unlisten */
    static void listen(Context c, Runnable r) {
        synchronized (listeners) { if (!listeners.contains(r)) listeners.add(r); }
        if (tapWatch == null) {
            tapWatch = (p, key) -> { if (UP_TAPS.equals(key)) changed(); };
            upstream(c).registerOnSharedPreferenceChangeListener(tapWatch);
        }
    }

    static void unlisten(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    private static void changed() {
        List<Runnable> now;
        synchronized (listeners) { now = new ArrayList<>(listeners); }
        for (Runnable r : now) UI.post(r);
    }
}
