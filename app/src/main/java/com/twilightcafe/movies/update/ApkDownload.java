package com.twilightcafe.movies.update;

import android.content.Context;
import android.content.pm.PackageInstaller;

import com.twilightcafe.movies.util.Http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A new build's APK, downloaded straight into an Android installer session (no storage permission needed),
 * where it waits until it's installed. A download that's cut short or too big is thrown away instead of being
 * offered to Android, which would only show "There was a problem parsing the package" on the TV.
 */
final class ApkDownload {
    private final Context ctx;

    ApkDownload(Context ctx) {
        this.ctx = ctx;
    }

    /** Downloads url (size: GitHub's size of the file, -1 if unknown) into a new session; returns its id. */
    int download(String url, long size) throws IOException {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo old : pi.getMySessions()) abandon(old.getSessionId());
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        boolean ok = false;
        try {
            OutputStream out = session.openWrite(Release.APK, 0, size > 0 ? size : -1);
            try {
                Http.download(url, out, size, Release.MAX_APK);
                session.fsync(out);
            } finally {
                out.close();
            }
            ok = true;
        } finally {
            if (!ok) session.abandon();
            session.close();
        }
        return id;
    }

    /** Whether the session with a download is still there. */
    boolean alive(int id) {
        if (id < 0) return false;
        try {
            return ctx.getPackageManager().getPackageInstaller().getSessionInfo(id) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    void abandon(int id) {
        if (id < 0) return;
        try {
            ctx.getPackageManager().getPackageInstaller().abandonSession(id);
        } catch (RuntimeException e) {
            // already gone
        }
    }
}
