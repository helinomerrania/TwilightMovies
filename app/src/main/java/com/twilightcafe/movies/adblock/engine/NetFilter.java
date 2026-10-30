package com.twilightcafe.movies.adblock.engine;

import java.util.Arrays;

/**
 * One network rule of the filter lists (Adblock Plus / uBlock Origin syntax): an address pattern
 * (UrlPattern) and its options. "||ads.example.com/banner^$script,third-party,domain=site.com" is
 * HOST_ANCHOR, the pattern "ads.example.com/banner^", Request.SCRIPT, THIRD_PARTY and domainsIn {site.com}.
 */
public final class NetFilter {
    public static final int EXCEPTION = 1;          // @@: an exception, never a block
    public static final int IMPORTANT = 1 << 1;     // a block that exceptions can't undo
    public static final int THIRD_PARTY = 1 << 2;   // only from another site
    public static final int FIRST_PARTY = 1 << 3;   // only from the same site
    public static final int MATCH_CASE = 1 << 4;
    public static final int HOST_ANCHOR = 1 << 5;   // || : at the start of the host or of one of its labels
    public static final int START_ANCHOR = 1 << 6;  // |  : at the start of the address
    public static final int END_ANCHOR = 1 << 7;    // |  : at the end of the address

    final int flags;
    final int types;
    final String pattern;
    final String redirect;
    final long[] domainsIn;    // the page's site must be one of these (domain=)
    final long[] domainsOut;   // and none of these (domain=~)
    final long[] toIn;         // the address's host must be one of these (to=)
    final long[] toOut;        // and none of these (to=~)
    final long[] denyAllow;    // the rule doesn't apply to these hosts (denyallow=)
    private final UrlPattern url;

    /** The hash arrays are sorted host hashes (Hash.of), or null for none; redirect is a stand-in's name or null. */
    public NetFilter(int flags, int types, String pattern, String redirect,
                     long[] domainsIn, long[] domainsOut, long[] toIn, long[] toOut, long[] denyAllow) {
        this.flags = flags;
        this.types = types;
        this.pattern = pattern;
        this.redirect = redirect == null || redirect.isEmpty() ? null : redirect;
        this.domainsIn = orNull(domainsIn);
        this.domainsOut = orNull(domainsOut);
        this.toIn = orNull(toIn);
        this.toOut = orNull(toOut);
        this.denyAllow = orNull(denyAllow);
        this.url = new UrlPattern(pattern, flags);
    }

    private static long[] orNull(long[] a) {
        return a == null || a.length == 0 ? null : a;
    }

    boolean important() {
        return (flags & IMPORTANT) != 0;
    }

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

    boolean matchesUrl(Request r) {
        return url.matches((flags & MATCH_CASE) != 0 ? r.url : r.lower, r.hostStart, r.hostEnd);
    }

    /** Whether any of the first count hashes is in the sorted set. */
    static boolean anyIn(long[] sorted, long[] hashes, int count) {
        if (sorted == null || sorted.length == 0) return false;
        for (int i = 0; i < count; i++) {
            if (Arrays.binarySearch(sorted, hashes[i]) >= 0) return true;
        }
        return false;
    }
}
