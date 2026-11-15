package dev.peanuts;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;

/**
 * Opening the app from its icon while a game runs goes back to the game, not to Home.
 *
 * Home (MainActivity) is singleTask and the task's root, so the launcher's intent brings Home itself forward and Android closes the game's screen above it. The
 * game is not stopped by that (its process and its host go on, suspended), but the player landed on Home and had to tap the game's cover to get back (seen on
 * the AYN Thor, 2026-11-15). Here the last game started is remembered, and a plain launcher start with the game's process alive starts its screen again: the
 * game shows its "Paused" screen as after any return.
 *
 * Only the launcher's own intent does this (MAIN + LAUNCHER, no extras): a shortcut, a frontend or an adb start that names what it wants is left alone.
 */
public final class GameReturn {
    private static final String PREFS = "peanuts_game_return", GAME_ACTIVITY = "dev.nxnative.android.GameActivity", GAME_PROCESS = ":game";
    private static final String[] KEYS = {"title_id", "name", "game", "pack"};

    private GameReturn() {}

    /** A game's screen is being started with these extras (Home's Play, a shortcut or a frontend): kept for {@link #back}. */
    public static void remember(Context c, String titleId, String name, String game, String pack) {
        if (titleId == null || game == null || pack == null) return;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title_id", titleId).putString("name", name == null ? "" : name).putString("game", game).putString("pack", pack).apply();
    }

    /** True when {@code launch} is the launcher's plain start and a game runs: its screen was started again, and Home has nothing more to do with this intent. */
    public static boolean back(Activity home, Intent launch) {
        if (launch == null || !Intent.ACTION_MAIN.equals(launch.getAction()) || !launch.hasCategory(Intent.CATEGORY_LAUNCHER)) return false;
        if (launch.getExtras() != null && !launch.getExtras().isEmpty()) return false;
        if (!gameAlive(home)) return false;
        SharedPreferences p = home.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Intent i = new Intent().setClassName(home, GAME_ACTIVITY);
        for (String k : KEYS) {
            String v = p.getString(k, null);
            if (v == null) return false;   // no game was started by this install's Home yet
            i.putExtra(k, v);
        }
        try {
            home.startActivity(i);
            Log.i(Companion.TAG, "opened from the launcher while " + p.getString("name", "a game") + " runs: back to the game");
            return true;
        } catch (RuntimeException e) {
            Log.w(Companion.TAG, "could not go back to the running game: " + e);
            return false;
        }
    }

    private static boolean gameAlive(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> l = am == null ? null : am.getRunningAppProcesses();
        if (l != null) for (ActivityManager.RunningAppProcessInfo p : l) if (p.processName != null && p.processName.endsWith(GAME_PROCESS)) return true;
        return false;
    }
}
