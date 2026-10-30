package com.twilightcafe.movies.adblock.engine;

import java.util.Locale;

/**
 * One address a page asks for, as the filter rules see it: the address, its host, the page (or frame) that
 * asks, and what kind of thing it is ({@link #SCRIPT}, {@link #IMAGE}...). FilterSet fills in the rest.
 */
public final class Request {
    // What a request is for. A rule without a type applies to all of them except DOCUMENT (a whole page).
    public static final int SCRIPT = 1;
    public static final int IMAGE = 1 << 1;
    public static final int STYLESHEET = 1 << 2;
    public static final int OBJECT = 1 << 3;
    public static final int XHR = 1 << 4;
    public static final int SUBDOCUMENT = 1 << 5;   // a frame
    public static final int PING = 1 << 6;
    public static final int MEDIA = 1 << 7;
    public static final int FONT = 1 << 8;
    public static final int OTHER = 1 << 9;
    public static final int DOCUMENT = 1 << 10;     // the page itself
    public static final int WEBSOCKET = 1 << 11;
    public static final int ALL = (1 << 12) - 1;
    public static final int DEFAULT = ALL & ~DOCUMENT;
    /** A request whose kind can't be told (the engine asked for anything, "*\/*"): a script, a fetch... */
    public static final int UNKNOWN = SCRIPT | XHR | OTHER | PING | OBJECT | WEBSOCKET;

    private static final long[] NONE = new long[0];

    public final String url;
    final String lower;
    final int hostStart;
    final int hostEnd;
    public final String host;
    /** The page or frame the request comes from (its host, lower-case), or null if unknown. */
    public final String docHost;
    final int typeMask;

    // Filled in by FilterSet.prepare
    long[] hostHashes = NONE;
    int hostCount;
    long[] docHashes = NONE;
    int docCount;
    long[] tokens = NONE;
    int tokenCount;
    boolean thirdParty;
    boolean prepared;
    /** After a block: the surrogate to answer with ("noopjs"...), if the rule named one. */
    public String redirect;

    public Request(String url, String docHost, int typeMask) {
        this.url = url;
        this.lower = url.toLowerCase(Locale.ROOT);
        int n = lower.length();
        int scheme = lower.indexOf("://");
        int hs = scheme < 0 ? 0 : scheme + 3;
        int end = hs;
        while (end < n) {
            char c = lower.charAt(end);
            if (c == '/' || c == '?' || c == '#') break;
            end++;
        }
        int at = lower.lastIndexOf('@', end - 1);
        if (at >= hs) hs = at + 1;
        int he = end;
        if (hs < he && lower.charAt(hs) == '[') {
            int rb = lower.indexOf(']', hs);
            if (rb > 0 && rb < he) he = rb + 1;
        } else {
            int colon = lower.indexOf(':', hs);
            if (colon >= 0 && colon < he) he = colon;
        }
        if (he > hs && lower.charAt(he - 1) == '.') he--;   // "example.com." is example.com
        this.hostStart = hs;
        this.hostEnd = he;
        this.host = lower.substring(hs, he);
        String d = docHost == null ? null : docHost.toLowerCase(Locale.ROOT);
        this.docHost = d == null || d.isEmpty() ? null : d;
        this.typeMask = typeMask == 0 ? UNKNOWN : typeMask;
    }

    /** The host of an address ("https://a.b.com:8080/x" -> "a.b.com"), lower-case, or "" if none. */
    public static String hostOf(String url) {
        return url == null ? "" : new Request(url, null, OTHER).host;
    }
}
