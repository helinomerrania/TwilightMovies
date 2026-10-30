package com.twilightcafe.movies.adblock;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * One network rule of the filter lists (Adblock Plus / uBlock Origin syntax): an address pattern with its
 * options. "||ads.example.com/banner^$script,third-party,domain=site.com" is HOST_ANCHOR, the pattern
 * "ads.example.com/banner^", SCRIPT, THIRD_PARTY and domainsIn {site.com}. In a pattern, '*' is anything
 * and '^' a separator (anything but a letter, a digit or _ - . %, or the address's end).
 */
final class NetFilter {
    static final int EXCEPTION = 1;          // @@: an exception, never a block
    static final int IMPORTANT = 1 << 1;     // a block that exceptions can't undo
    static final int THIRD_PARTY = 1 << 2;   // only from another site
    static final int FIRST_PARTY = 1 << 3;   // only from the same site
    static final int MATCH_CASE = 1 << 4;
    static final int HOST_ANCHOR = 1 << 5;   // || : at the start of the host or of one of its labels
    static final int START_ANCHOR = 1 << 6;  // |  : at the start of the address
    static final int END_ANCHOR = 1 << 7;    // |  : at the end of the address

    private static final String[] NO_PARTS = new String[0];

    final int flags;
    final int types;
    final String pattern;
    final String redirect;
    final long[] domainsIn;    // the page's site must be one of these (domain=)
    final long[] domainsOut;   // and none of these (domain=~)
    final long[] toIn;         // the address's host must be one of these (to=)
    final long[] toOut;        // and none of these (to=~)
    final long[] denyAllow;    // the rule doesn't apply to these hosts (denyallow=)
    private final String[] parts;

    NetFilter(int flags, int types, String pattern, String redirect,
              long[] domainsIn, long[] domainsOut, long[] toIn, long[] toOut, long[] denyAllow) {
        this.flags = flags;
        this.types = types;
        this.pattern = pattern;
        this.redirect = redirect == null || redirect.isEmpty() ? null : redirect;
        this.domainsIn = domainsIn;
        this.domainsOut = domainsOut;
        this.toIn = toIn;
        this.toOut = toOut;
        this.denyAllow = denyAllow;
        this.parts = split(pattern);
    }

    boolean exception() {
        return (flags & EXCEPTION) != 0;
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

    // ------------------------------------------------------------------ options

    boolean optionsMatch(Request r) {
        if ((types & r.typeMask) == 0) return false;
        if ((flags & THIRD_PARTY) != 0 && !r.thirdParty) return false;
        if ((flags & FIRST_PARTY) != 0 && r.thirdParty) return false;
        if (domainsIn != null && !anyIn(domainsIn, r.docHashes, r.docCount)) return false;
        if (domainsOut != null && anyIn(domainsOut, r.docHashes, r.docCount)) return false;
        if (toIn != null && !anyIn(toIn, r.hostHashes, r.hostCount)) return false;
        if (toOut != null && anyIn(toOut, r.hostHashes, r.hostCount)) return false;
        return denyAllow == null || !anyIn(denyAllow, r.hostHashes, r.hostCount);
    }

    static boolean anyIn(long[] sorted, long[] hashes, int count) {
        if (sorted == null || sorted.length == 0) return false;
        for (int i = 0; i < count; i++) {
            if (Arrays.binarySearch(sorted, hashes[i]) >= 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the address pattern

    boolean matchesUrl(Request r) {
        if (parts.length == 0) return true;   // "*" or only options: every address
        String u = (flags & MATCH_CASE) != 0 ? r.url : r.lower;
        int n = u.length();
        boolean endAnchor = (flags & END_ANCHOR) != 0;
        boolean single = parts.length == 1;
        String first = parts[0];
        if ((flags & HOST_ANCHOR) != 0) {
            for (int s = r.hostStart; s < r.hostEnd; s++) {
                if (s != r.hostStart && u.charAt(s - 1) != '.') continue;
                int e = at(u, s, first);
                if (e < 0) continue;
                if (single) {
                    if (!endAnchor || e == n) return true;
                    continue;
                }
                return rest(u, e, endAnchor);   // the leftmost start leaves the most room for the rest
            }
            return false;
        }
        if ((flags & START_ANCHOR) != 0) {
            int e = at(u, 0, first);
            if (e < 0) return false;
            return single ? !endAnchor || e == n : rest(u, e, endAnchor);
        }
        if (single) return endAnchor ? endsWith(u, 0, first) : find(u, 0, first) >= 0;
        int s = find(u, 0, first);
        return s >= 0 && rest(u, at(u, s, first), endAnchor);
    }

    private boolean rest(String u, int pos, boolean endAnchor) {
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

    // ------------------------------------------------------------------ the token a rule is filed under

    static boolean tokenChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '%';
    }

    private static final String[] COMMON = {"http", "https", "www", "com", "net", "org", "js", "html", "php", "cdn"};

    /**
     * The hash of a whole word of the pattern that every matching address must also contain as a whole word
     * (so it can be looked up from the address's own words), or 0 when there is none. The longest one is
     * taken, common words (http, www, com...) last.
     */
    static long indexToken(String pattern, int flags) {
        String p = pattern.toLowerCase(java.util.Locale.ROOT);
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
            boolean leftOk = s == 0 ? (flags & (HOST_ANCHOR | START_ANCHOR)) != 0 : p.charAt(s - 1) != '*';
            boolean rightOk = e == n ? (flags & END_ANCHOR) != 0 : p.charAt(e) != '*';
            if (!leftOk || !rightOk || e - s < 2) continue;
            boolean common = false;
            String word = p.substring(s, e);
            for (String c : COMMON) {
                if (c.equals(word)) {
                    common = true;
                    break;
                }
            }
            if (bestStart < 0 || (bestCommon && !common) || (common == bestCommon && e - s > bestLen)) {
                bestStart = s;
                bestLen = e - s;
                bestCommon = common;
            }
        }
        return bestStart < 0 ? 0 : Hash.of(p, bestStart, bestStart + bestLen);
    }
}
