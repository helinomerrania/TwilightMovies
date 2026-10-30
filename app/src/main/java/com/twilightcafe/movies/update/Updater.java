package com.twilightcafe.movies.update;

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

import com.twilightcafe.movies.InstallResult;
import com.twilightcafe.movies.util.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * New versions from GitHub, the way Twilight TV gets its own. Now and then the app looks at the repository's
 * latest release; a newer build is downloaded straight into Android's installer (ApkDownload) and waits. It's
 * only installed when someone chooses "Install the new version" in the menu: Android then asks on the TV, and
 * Install is pressed with the remote.
 *
 * Releases are tagged "build-N-M" (.github/workflows/build.yml): N is the build's number, which is also the
 * app's version number. Builds made on a computer have no repository, so they never update.
 */
public final class Updater {
    public interface Listener {
        /** On the main thread: one of the check results below. */
        void onUpdateChecked(int result);
    }

    // check results
    public static final int UP_TO_DATE = 0;
    public static final int READY = 1;
    public static final int NO_BUILDS = 2;
    public static final int FAILED = 3;

    // install results
    public static final int STARTED = 10;
    public static final int NEED_PERMISSION = 11;
    public static final int UNSUPPORTED = 12;
    public static final int NOTHING_WAITING = 13;
    public static final int INSTALL_FAILED = 14;

    private final SharedPreferences prefs;
    private final ApkDownload apk;
    private final String repo;
    private final int myBuild;
    private volatile boolean busy;
    private volatile int readyBuild;

    public Updater(Context c, String repo, int myBuild) {
        this.prefs = c.getSharedPreferences("update", Context.MODE_PRIVATE);
        this.apk = new ApkDownload(c.getApplicationContext());
        this.repo = repo == null ? "" : repo.trim();
        this.myBuild = myBuild;
        int waiting = prefs.getInt("build", 0);
        if (waiting > myBuild && apk.alive(prefs.getInt("session", -1))) {
            readyBuild = waiting;
        } else if (waiting != 0) {
            forget();   // installed meanwhile, or the download is gone
        }
    }

    public boolean enabled() {
        return !repo.isEmpty();
    }

    public int myBuild() {
        return myBuild;
    }

    /** The build downloaded and waiting to be installed (0 if none). */
    public int readyBuild() {
        return readyBuild;
    }

    public long lastCheck() {
        return prefs.getLong("checked", 0);
    }

    /** Looks for a newer build and downloads it, on a thread of its own; the answer comes on the main thread. */
    public void checkInBackground(final Handler main, final Listener listener) {
        if (!enabled() || busy) return;
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = check();
                busy = false;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onUpdateChecked(result);
                    }
                });
            }
        }, "update-check").start();
    }

    private int check() {
        try {
            prefs.edit().putLong("checked", System.currentTimeMillis()).apply();
            String body = Http.text("https://api.github.com/repos/" + repo + "/releases/latest",
                    "application/vnd.github+json, application/json", 2 << 20);
            if (body == null) return NO_BUILDS;
            JSONObject release = new JSONObject(body);
            int build = Release.buildNumber(release.optString("tag_name"));
            if (build <= myBuild) {
                forget();
                return UP_TO_DATE;
            }
            if (build == readyBuild && apk.alive(prefs.getInt("session", -1))) return READY;
            String url = null;
            long size = -1;
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && Release.APK.equals(a.optString("name"))) {
                    url = a.optString("browser_download_url");
                    size = a.optLong("size", -1);
                }
            }
            if (url == null || url.isEmpty() || size > Release.MAX_APK) return FAILED;
            int session = apk.download(url, size);
            prefs.edit().putInt("session", session).putInt("build", build).apply();
            readyBuild = build;
            return READY;
        } catch (Exception e) {
            return FAILED;   // no internet, GitHub busy, a broken download: tried again at the next check
        }
    }

    private void forget() {
        apk.abandon(prefs.getInt("session", -1));
        prefs.edit().remove("session").remove("build").apply();
        readyBuild = 0;
    }

    /** Android 8.0 and newer ask once whether this app may install apps. */
    public boolean canInstall(Activity a) {
        return Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls();
    }

    /** Asks Android to install the waiting build: it shows its own question on the TV. */
    public int install(Activity a) {
        int id = prefs.getInt("session", -1);
        if (readyBuild <= myBuild || !apk.alive(id)) {
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
