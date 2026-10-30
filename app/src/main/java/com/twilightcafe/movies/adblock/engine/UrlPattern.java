package com.twilightcafe.movies.adblock.engine;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The address part of a network rule: "ads.example.com/banner^" with its anchors (|| at a host label,
 * | at the start or the end). In a pattern, '*' is anything and '^' a separator: anything but a letter, a
 * digit or _ - . %, or the address's end. Also which whole word of the pattern a rule is filed under.
 */
public final class UrlPattern {
    private static final String[] NO_PARTS = new String[0];
    private static final String[] COMMON = {"http", "https", "www", "com", "net", "org", "js", "html", "php", "cdn"};

    private final String[] parts;
    private final boolean hostAnchor;
    private final boolean startAnchor;
    private final boolean endAnchor;

    UrlPattern(String pattern, int flags) {
        this.parts = split(pattern);
        this.hostAnchor = (flags & NetFilter.HOST_ANCHOR) != 0;
        this.startAnchor = (flags & NetFilter.START_ANCHOR) != 0;
        this.endAnchor = (flags & NetFilter.END_ANCHOR) != 0;
    }

    private static String[] split(String p) {
        if (p.isEmpty()) return NO_PARTS;
        ArrayList<String> out = new ArrayList<String>();
        int s = 0;
        for (int i = 0; i <= p.length(); i++) {
            if (i == p.length() || p.charAt(i) == '*') {
                if (i > s) out.add(p.substring(s, i));
                s = i + 1;
            }
        }
        return out.toArray(NO_PARTS);
    }

    /** Whether u (the address, lower-case unless the rule matches case) matches; host is where its host is. */
    boolean matches(String u, int hostStart, int hostEnd) {
        if (parts.length == 0) return true;   // "*" or only options: every address
        int n = u.length();
        boolean single = parts.length == 1;
        String first = parts[0];
        if (hostAnchor) {
            for (int s = hostStart; s < hostEnd; s++) {
                if (s != hostStart && u.charAt(s - 1) != '.') continue;
                int e = at(u, s, first);
                if (e < 0) continue;
                if (single) {
                    if (!endAnchor || e == n) return true;
                    continue;
                }
                return rest(u, e);   // the leftmost start leaves the most room for the rest
            }
            return false;
        }
        if (startAnchor) {
            int e = at(u, 0, first);
            if (e < 0) return false;
            return single ? !endAnchor || e == n : rest(u, e);
        }
        if (single) return endAnchor ? endsWith(u, 0, first) : find(u, 0, first) >= 0;
        int s = find(u, 0, first);
        return s >= 0 && rest(u, at(u, s, first));
    }

    private boolean rest(String u, int pos) {
        for (int i = 1; i < parts.length; i++) {
            if (endAnchor && i == parts.length - 1) return endsWith(u, pos, parts[i]);
            int s = find(u, pos, parts[i]);
            if (s < 0) return false;
            pos = at(u, s, parts[i]);
        }
        return true;
    }

    /** Whether part occurs at or after from, ending exactly at the address's end. */
    private static boolean endsWith(String u, int from, String part) {
        int n = u.length();
        int s = n - part.length();
        if (s >= from && at(u, s, part) == n) return true;
        if (part.charAt(part.length() - 1) == '^') {   // a final '^' may match the end itself
            s++;
            return s >= from && at(u, s, part) == n;
        }
        return false;
    }

    /** The leftmost start of part at or after from, or -1. */
    private static int find(String u, int from, String part) {
        char c0 = part.charAt(0);
        int n = u.length();
        for (int s = from; s <= n; s++) {
            if (c0 != '^') {
                s = u.indexOf(c0, s);
                if (s < 0) return -1;
            }
            if (at(u, s, part) >= 0) return s;
        }
        return -1;
    }

    /** Where part ends if it matches at s, or -1. */
    private static int at(String u, int s, String part) {
        int n = u.length();
        int j = s;
        int len = part.length();
        for (int k = 0; k < len; k++) {
            char c = part.charAt(k);
            if (c == '^') {
                if (j == n) return k == len - 1 ? j : -1;
                if (!separator(u.charAt(j))) return -1;
                j++;
                continue;
            }
            if (j >= n || u.charAt(j) != c) return -1;
            j++;
        }
        return j;
    }

    static boolean separator(char c) {
        if (c >= 'a' && c <= 'z') return false;
        if (c >= 'A' && c <= 'Z') return false;
        if (c >= '0' && c <= '9') return false;
        if (c == '_' || c == '-' || c == '.' || c == '%') return false;
        return c < 128;
    }

    // ------------------------------------------------------------------ the word a rule is filed under

    static boolean tokenChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '%';
    }

    /**
     * The hash of a whole word of the pattern that every matching address must also contain as a whole word
     * (so the rule can be found from the address's own words), or 0 when there is none. The longest one is
     * taken, common words (http, www, com...) last.
     */
    public static long indexToken(String pattern, int flags) {
        String p = pattern.toLowerCase(Locale.ROOT);
        int bestStart = -1;
        int bestLen = 0;
        boolean bestCommon = true;
        int n = p.length();
        int i = 0;
        while (i < n) {
            if (!tokenChar(p.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && tokenChar(p.charAt(i))) i++;
            int e = i;
            boolean leftOk = s == 0 ? (flags & (NetFilter.HOST_ANCHOR | NetFilter.START_ANCHOR)) != 0 : p.charAt(s - 1) != '*';
            boolean rightOk = e == n ? (flags & NetFilter.END_ANCHOR) != 0 : p.charAt(e) != '*';
            if (!leftOk || !rightOk || e - s < 2) continue;
            boolean common = isCommon(p.substring(s, e));
            if (bestStart < 0 || (bestCommon && !common) || (common == bestCommon && e - s > bestLen)) {
                bestStart = s;
                bestLen = e - s;
                bestCommon = common;
            }
        }
        return bestStart < 0 ? 0 : Hash.of(p, bestStart, bestStart + bestLen);
    }

    private static boolean isCommon(String word) {
        for (String c : COMMON) {
            if (c.equals(word)) return true;
        }
        return false;
    }
}
