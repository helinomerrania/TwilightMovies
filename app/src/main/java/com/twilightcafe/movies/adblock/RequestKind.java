package com.twilightcafe.movies.adblock;

import com.twilightcafe.movies.adblock.engine.Request;

import java.util.Locale;
import java.util.Map;

/**
 * What a request of the page is for (a script, a picture, a frame...), as the rules ask: from what the web
 * engine asked for (Accept), else from the address's ending, else "unknown" (which the rules for scripts,
 * requests and other things still match). Plain Java.
 */
public final class RequestKind {
    private RequestKind() {
    }

    public static int of(Map<String, String> headers, String url) {
        String accept = header(headers, "Accept");
        if (accept != null) {
            String a = accept.toLowerCase(Locale.ROOT);
            if (a.startsWith("text/html") || a.startsWith("application/xhtml")) return Request.SUBDOCUMENT;
            if (a.startsWith("image/")) return Request.IMAGE;
            if (a.startsWith("text/css")) return Request.STYLESHEET;
            if (a.startsWith("video/") || a.startsWith("audio/")) return Request.MEDIA;
            if (a.startsWith("font/") || a.startsWith("application/font")) return Request.FONT;
        }
        if (header(headers, "Range") != null) return Request.MEDIA;
        String p = path(url);
        if (ends(p, ".js", ".mjs")) return Request.SCRIPT;
        if (ends(p, ".css")) return Request.STYLESHEET;
        if (ends(p, ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".avif", ".bmp")) return Request.IMAGE;
        if (ends(p, ".woff2", ".woff", ".ttf", ".otf", ".eot")) return Request.FONT;
        if (ends(p, ".mp4", ".m3u8", ".ts", ".m4s", ".mpd", ".webm", ".mp3", ".m4a", ".aac")) return Request.MEDIA;
        return Request.UNKNOWN;
    }

    /** A header, whatever the case of its name (null if it isn't there). */
    public static String header(Map<String, String> headers, String name) {
        if (headers == null) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** The address in lower case, without its query and fragment. */
    private static String path(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        int cut = u.length();
        int q = u.indexOf('?');
        if (q >= 0) cut = q;
        int hash = u.indexOf('#');
        if (hash >= 0 && hash < cut) cut = hash;
        return u.substring(0, cut);
    }

    private static boolean ends(String p, String... endings) {
        for (String e : endings) {
            if (p.endsWith(e)) return true;
        }
        return false;
    }
}
