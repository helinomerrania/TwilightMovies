package com.twilightcafe.movies;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * New versions from GitHub, the way Twilight TV gets its own. Now and then the app looks at the
 * repository's latest release; a newer build is downloaded straight into Android's installer (no
 * storage permission needed) and waits. It's only installed when someone chooses "Install the new
 * version" in the menu: Android then asks on the TV, and Install is pressed with the remote.
 *
 * Releases are tagged "build-N-M" (see .github/workflows/build.yml): N is the build's number, which is
 * also the app's version number. Builds made on a computer have no repository, so they never update.
 */
final class Updater {

    interface Listener {
        /** On the main thread: one of the check results below, and the build waiting to be installed (0 if none). */
        void onUpdateChecked(int result, int readyBuild);
    }

    // check results
    static final int UP_TO_DATE = 0;
    static final int READY = 1;
    static final int NO_BUILDS = 2;
    static final int FAILED = 3;

    // install results
    static final int STARTED = 10;
    static final int NEED_PERMISSION = 11;
    static final int UNSUPPORTED = 12;
    static final int NOTHING_WAITING = 13;
    static final int INSTALL_FAILED = 14;

    static final String APK = "twilight-movies.apk";
    static final long MAX_APK = 50L << 20;   // the app is about 1 MB: anything near this isn't it

    private final Context ctx;
    private final SharedPreferences prefs;
    private final String repo;
    private final int myBuild;
    private volatile boolean busy;
    private volatile int readyBuild;

    Updater(Context c, String repo, int myBuild) {
        this.ctx = c.getApplicationContext();
        this.prefs = c.getSharedPreferences("update", Context.MODE_PRIVATE);
        this.repo = repo == null ? "" : repo.trim();
        this.myBuild = myBuild;
        int waiting = prefs.getInt("build", 0);
        if (waiting > myBuild && sessionAlive(prefs.getInt("session", -1))) {
            readyBuild = waiting;
        } else if (waiting != 0) {
            forget();   // installed meanwhile (or the download is gone)
        }
    }

    boolean enabled() {
        return !repo.isEmpty();
    }

    int readyBuild() {
        return readyBuild;
    }

    long lastCheck() {
        return prefs.getLong("checked", 0);
    }

    /** Looks for a newer build and downloads it, on a thread of its own; the answer comes on the main thread. */
    void checkInBackground(final Handler ui, final Listener listener) {
        if (!enabled() || busy) return;
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = check();
                busy = false;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onUpdateChecked(result, readyBuild);
                    }
                });
            }
        }, "update-check").start();
    }

    private int check() {
        try {
            prefs.edit().putLong("checked", System.currentTimeMillis()).apply();
            String body = get("https://api.github.com/repos/" + repo + "/releases/latest");
            if (body == null) return NO_BUILDS;
            JSONObject release = new JSONObject(body);
            int build = buildNumber(release.optString("tag_name"));
            if (build <= myBuild) {
                forget();
                return UP_TO_DATE;
            }
            if (build == readyBuild && sessionAlive(prefs.getInt("session", -1))) return READY;
            String url = null;
            long size = -1;
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && APK.equals(a.optString("name"))) {
                    url = a.optString("browser_download_url");
                    size = a.optLong("size", -1);
                }
            }
            if (url == null || url.isEmpty() || size > MAX_APK) return FAILED;
            int session = download(url, size);
            prefs.edit().putInt("session", session).putInt("build", build).apply();
            readyBuild = build;
            return READY;
        } catch (Exception e) {
            return FAILED;   // no internet, GitHub busy, a broken download: tried again at the next check
        }
    }

    /** "build-12-1" -> 12 (0 if it isn't one of ours). */
    static int buildNumber(String tag) {
        try {
            String[] p = tag.split("-");
            return p.length >= 2 && p[0].equals("build") ? Integer.parseInt(p[1]) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The text at an https address, or null for 404. Throws when the internet or the server fails. */
    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "TwilightMovies-app");   // GitHub asks every visitor for a name
        c.setRequestProperty("Accept", "application/vnd.github+json, application/json");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > (2 << 20)) throw new IOException("answer too long");
                }
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }

    // Straight into Android's installer. size: GitHub's size of the file (-1 if unknown)
    private int download(String url, long size) throws IOException {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo old : pi.getMySessions()) {
            try {
                pi.abandonSession(old.getSessionId());
            } catch (RuntimeException e) {
                // already gone
            }
        }
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "TwilightMovies-app");
        boolean ok = false;
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            OutputStream out = session.openWrite(APK, 0, size > 0 ? size : -1);
            try {
                copy(in, out, size, MAX_APK);
                session.fsync(out);
            } finally {
                out.close();
                in.close();
            }
            ok = true;
        } finally {
            if (!ok) session.abandon();
            session.close();
            c.disconnect();
        }
        return id;
    }

    /**
     * The download, counted: more than GitHub said (or than the app could be), or less (the connection
     * closed early), and it's thrown away instead of being offered to Android, which would only show
     * "There was a problem parsing the package" on the TV. Returns the bytes copied.
     */
    static long copy(InputStream in, OutputStream out, long expected, long max) throws IOException {
        byte[] buf = new byte[65536];
        long got = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            got += n;
            if (got > max) throw new IOException("bigger than the app could be");
            if (expected > 0 && got > expected) throw new IOException("bigger than GitHub said");
            out.write(buf, 0, n);
        }
        if (expected > 0 && got != expected) throw new IOException(got + " of " + expected + " bytes");
        return got;
    }

    private boolean sessionAlive(int id) {
        if (id < 0) return false;
        try {
            return ctx.getPackageManager().getPackageInstaller().getSessionInfo(id) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void forget() {
        int id = prefs.getInt("session", -1);
        if (id >= 0) {
            try {
                ctx.getPackageManager().getPackageInstaller().abandonSession(id);
            } catch (RuntimeException e) {
                // already gone
            }
        }
        prefs.edit().remove("session").remove("build").apply();
        readyBuild = 0;
    }

    /** Android 8.0 and newer ask once whether this app may install apps. */
    boolean canInstall(Activity a) {
        return Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls();
    }

    /** Asks Android to install the waiting build: it shows its own question on the TV. */
    int install(Activity a) {
        int id = prefs.getInt("session", -1);
        if (readyBuild <= myBuild || !sessionAlive(id)) {
            readyBuild = 0;
            return NOTHING_WAITING;
        }
        if (!canInstall(a)) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                return NEED_PERMISSION;
            } catch (ActivityNotFoundException e) {
                return UNSUPPORTED;
            }
        }
        try {
            PackageInstaller.Session session = a.getPackageManager().getPackageInstaller().openSession(id);
            Intent done = new Intent(a, InstallResult.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(a, id, done, flags).getIntentSender());
            session.close();
            prefs.edit().remove("session").remove("build").apply();
            readyBuild = 0;
            return STARTED;
        } catch (IOException | RuntimeException e) {
            return INSTALL_FAILED;
        }
    }
}
