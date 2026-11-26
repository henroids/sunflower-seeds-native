package dev.nxnative.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * PEANUTS: session logs sent to the owner's private log repository on GitHub (REPO), so a run can be looked at without a computer. Settings › Help › Send logs to
 * GitHub takes a fine-grained key (on this phone only, in the app's private preferences) that may write that one repository. With a key, every finished run's
 * log goes up by itself (Home's onResume: the logs not written to for AGE_MS that were not sent yet), and the in-game panel's Stop and share the log sends that
 * run's at once (ExitWatch). A log goes up gzipped as logs/<device model>-<its name>.gz through the contents API; one that fails stays for the next try
 */
final class LogUpload {
    private LogUpload() {}

    static final String REPO = "henroids/sunflower-seeds-logs";
    private static final long AGE_MS = 60_000;      // a log still being written (a game running) is left for later
    private static final int KEEP_SENT = 200;       // names remembered as sent
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static boolean busy;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("log_upload", Context.MODE_PRIVATE); }
    static boolean on(Context c) { return !prefs(c).getString("key", "").isEmpty(); }

    /** Settings › Help: the key's dialog (paste, send the newest now, turn off) */
    static void settings(MainActivity a, Runnable changed) {
        EditText key = new EditText(a);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint(on(a) ? "Key saved (paste a new one to replace it)" : "github_pat_…");
        int p = Math.round(20 * a.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a); box.setPadding(p, 0, p, 0); box.addView(key);
        PadFocus.Builder b = new PadFocus.Builder(a);
        b.setTitle("Send logs to GitHub").setView(box)
         .setMessage("Each finished session's log goes to the private repository " + REPO + " by itself. Paste a fine-grained key that can write that repository only.")
         .setPositiveButton("Save", (d, w) -> {
             String k = key.getText().toString().trim();
             if (!k.isEmpty()) { prefs(a).edit().putString("key", k).apply(); changed.run(); sendPending(a); }
         })
         .setNegativeButton("Cancel", null);
        if (on(a)) b.setNeutralButton("Turn off", (d, w) -> { prefs(a).edit().remove("key").apply(); changed.run(); });
        b.show();
    }

    /** Every finished log not sent yet, in the background; a toast says how it went when anything was sent or failed */
    static void sendPending(Context c) {
        if (!on(c)) return;
        File[] f = LogProvider.dir(c).listFiles((d, n) -> n.endsWith(".log"));
        if (f == null) return;
        Set<String> sent = prefs(c).getStringSet("sent", new HashSet<>());
        long now = System.currentTimeMillis();
        Arrays.sort(f, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        java.util.List<File> todo = new java.util.ArrayList<>();
        for (File x : f) if (!sent.contains(x.getName()) && now - x.lastModified() > AGE_MS) todo.add(x);
        if (sent.isEmpty() && todo.size() > 1) todo = todo.subList(todo.size() - 1, todo.size());   // first use: the newest only, not every old log on the phone
        send(c, todo);
    }

    /** One run's log now (Stop and share the log), whatever its age */
    static void sendNow(Context c, File log) { send(c, java.util.List.of(log)); }

    private static synchronized void send(Context c, java.util.List<File> logs) {
        if (logs.isEmpty() || busy) return;
        busy = true;
        Context app = c.getApplicationContext();
        String key = prefs(app).getString("key", "");
        new Thread(() -> {
            int ok = 0; String err = null;
            for (File log : logs) {
                String e = put(key, log);
                if (e == null) { ok++; markSent(app, log.getName()); } else { err = e; break; }
            }
            int n = ok; String why = err;
            UI.post(() -> {
                busy = false;
                if (why != null) Toast.makeText(app, "Log not sent: " + why + ". It will be tried again.", Toast.LENGTH_LONG).show();
                else Toast.makeText(app, n == 1 ? "Log sent" : n + " logs sent", Toast.LENGTH_SHORT).show();
            });
        }, "log-upload").start();
    }

    private static void markSent(Context c, String name) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("sent", new HashSet<>()));
        s.add(name);
        if (s.size() > KEEP_SENT) { java.util.List<String> l = new java.util.ArrayList<>(s); java.util.Collections.sort(l); s = new HashSet<>(l.subList(l.size() - KEEP_SENT, l.size())); }
        prefs(c).edit().putStringSet("sent", s).apply();
    }

    /** PUT logs/<name>.gz: null when it is there (made now, or already), else why not */
    private static String put(String key, File log) {
        HttpURLConnection h = null;
        try {
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(log); OutputStream out = new GZIPOutputStream(gz)) {
                byte[] buf = new byte[1 << 16]; for (int r; (r = in.read(buf)) > 0;) out.write(buf, 0, r);
            }
            // the device's model before the log's name: two devices' logs of the same second had one name, and the second was taken for sent (422) and never went up
            String name = android.os.Build.MODEL.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "") + "-" + log.getName();
            String body = "{\"message\":\"Log " + name + "\",\"content\":\"" + Base64.encodeToString(gz.toByteArray(), Base64.NO_WRAP) + "\"}";
            h = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/contents/logs/" + name + ".gz").openConnection();
            h.setRequestMethod("PUT");
            h.setConnectTimeout(15_000); h.setReadTimeout(60_000);
            h.setRequestProperty("Authorization", "Bearer " + key);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("Content-Type", "application/json");
            h.setDoOutput(true);
            try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = h.getResponseCode();
            if (code == 200 || code == 201 || code == 422) return null;   // 422: a file of that name is there already (sent before)
            return code == 401 ? "the key was refused" : code == 403 || code == 404 ? "the key cannot write " + REPO : "GitHub answered " + code;
        } catch (java.io.IOException e) {
            return "no connection";
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
