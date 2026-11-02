package com.twilightcafe.movies.util;

import java.util.Locale;

/** Web addresses (and the app's own pages'): their host, a short form for the screen, and what someone typed made into one. Plain Java. */
public final class Urls {
    private Urls() {
    }

    /** The address of the app's own pages (the Twilight screens, assets/gecko/ui/): "twilight:" and the page's path. */
    public static final String OWN = "twilight:";

    public static boolean isOwn(String url) {
        return url != null && url.startsWith(OWN);
    }

    /** Whether a page may load this address itself (not intent:, market: and the like: ads love them). */
    public static boolean isWebScheme(String url) {
        if (url == null) return true;
        String u = url.trim().toLowerCase(Locale.US);
        return u.startsWith("http:") || u.startsWith("https:") || u.startsWith("about:")
                || u.startsWith("data:") || u.startsWith("blob:") || u.startsWith("javascript:");
    }

    /** The host of an address, in lower case ("app.kurdfilm.krd"); an address without one (about:blank) as it is. */
    public static String hostOf(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        if (scheme < 0) return url;
        int start = scheme + 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == '\\') {
                end = i;
                break;
            }
        }
        String host = url.substring(start, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        if (host.startsWith("[")) {   // an IPv6 address
            int close = host.indexOf(']');
            if (close > 0) host = host.substring(0, close + 1);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) host = host.substring(0, colon);
        }
        return host.toLowerCase(Locale.US);
    }

    /** The address without "https://" and a last "/", for the screen. */
    public static String shortUrl(String url) {
        if (url == null) return "";
        String s = url;
        int i = s.indexOf("://");
        if (i >= 0) s = s.substring(i + 3);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean sameHost(String a, String b) {
        String ha = hostOf(a);
        return !ha.isEmpty() && ha.equals(hostOf(b));
    }

    /** What someone typed ("kurdfilm.krd/tv") made into a full web address, or null if it can't be one. */
    public static String normalize(String input) {
        if (input == null) return null;
        String s = input.trim().replace(" ", "");
        if (s.isEmpty()) return null;
        if (!s.contains("://")) s = "https://" + s;
        String scheme = s.substring(0, s.indexOf("://")).toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        String host = hostOf(s);
        if (host.isEmpty() || !host.contains(".") || host.startsWith(".") || host.endsWith(".")) return null;
        return s;
    }
}
