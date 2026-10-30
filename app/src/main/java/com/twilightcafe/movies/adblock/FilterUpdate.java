package com.twilightcafe.movies.adblock;

import android.content.Context;
import android.content.SharedPreferences;

import com.twilightcafe.movies.adblock.engine.FilterFile;
import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.util.Http;
import com.twilightcafe.movies.util.Streams;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

/**
 * Newer filters from GitHub: the repository's "filters" release, made every day (.github/workflows/filters.yml).
 * Looked at every 12 hours; a newer file is downloaded once the box's is a day and a half old (at once when
 * someone asks), checked (its size, its SHA-256, and that it reads) and kept for the next start.
 */
final class FilterUpdate {
    private static final long REFRESH_AFTER_MS = 36L * 3600 * 1000;
    private static final long CHECK_EVERY_MS = 12L * 3600 * 1000;
    private static final long MAX_FILE = 40L << 20;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final String repo;

    FilterUpdate(Context ctx, SharedPreferences prefs, String repo) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.repo = repo == null ? "" : repo.trim();
    }

    /** Whether it's time to look (or someone asked); builds made on a computer have no repository to look at. */
    boolean due(boolean asked) {
        if (repo.isEmpty()) return false;
        long since = System.currentTimeMillis() - prefs.getLong("checked", 0);
        return asked || since < 0 || since >= CHECK_EVERY_MS;
    }

    /** A newer filter set than have (downloaded, checked and kept), or null if there's none to take. */
    FilterSet fetch(boolean asked, FilterSet have) throws Exception {
        prefs.edit().putLong("checked", System.currentTimeMillis()).apply();
        String base = "https://github.com/" + repo + "/releases/download/filters/";
        String about = Http.text(base + "filters.json", null, 1 << 16);
        if (about == null) return null;
        JSONObject j = new JSONObject(about);
        if (j.optInt("format") != FilterFile.FORMAT) return null;   // for a newer app; this one keeps its own
        long created = j.getLong("created");
        long size = j.getLong("size");
        String sha = j.getString("sha256");
        long mine = have != null ? have.created : 0;
        if (created <= mine) return null;
        if (!asked && have != null && created - mine < REFRESH_AFTER_MS) return null;   // fewer downloads on mobile data
        if (size <= 0 || size > MAX_FILE) return null;
        File part = new File(ctx.getFilesDir(), FilterFiles.NAME + ".part");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            OutputStream out = new DigestOutputStream(new FileOutputStream(part), md);
            try {
                Http.download(base + "filters.bin", out, size, MAX_FILE);
            } finally {
                out.close();
            }
            if (!hex(md.digest()).equalsIgnoreCase(sha)) return null;
            FilterSet fresh;
            InputStream in = new FileInputStream(part);
            try {
                fresh = FilterSet.read(in);
            } finally {
                Streams.close(in);
            }
            return part.renameTo(FilterFiles.downloaded(ctx)) ? fresh : null;
        } finally {
            if (part.exists()) part.delete();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return s.toString();
    }
}
