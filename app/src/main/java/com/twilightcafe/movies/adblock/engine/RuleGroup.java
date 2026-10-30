package com.twilightcafe.movies.adblock.engine;

import java.util.Arrays;

/**
 * Network rules filed under a key, sorted by it: a host's hash (||host^ with options), a word of the address
 * (UrlPattern.indexToken), the site of the pages they are for (domain=), or nothing (key 0, every one tried).
 */
public final class RuleGroup {
    static final RuleGroup EMPTY = new RuleGroup(new long[0], new NetFilter[0]);

    final long[] keys;
    final NetFilter[] filters;

    /** keys sorted, filters[i] filed under keys[i]. */
    public RuleGroup(long[] keys, NetFilter[] filters) {
        if (keys.length != filters.length) throw new IllegalArgumentException("one key for each rule");
        this.keys = keys;
        this.filters = filters;
    }

    public int size() {
        return filters.length;
    }

    /** The first rule under one of the keys whose options (and, unless byHost, address) match r. */
    NetFilter find(long[] lookFor, int count, Request r, boolean checkUrl) {
        if (filters.length == 0) return null;
        for (int i = 0; i < count; i++) {
            long k = lookFor[i];
            for (int j = first(keys, k); j >= 0 && j < keys.length && keys[j] == k; j++) {
                NetFilter f = filters[j];
                if (f.optionsMatch(r) && (!checkUrl || f.matchesUrl(r))) return f;
            }
        }
        return null;
    }

    /** The first rule of all that matches r (for rules filed under nothing). */
    NetFilter any(Request r) {
        for (NetFilter f : filters) {
            if (f.optionsMatch(r) && f.matchesUrl(r)) return f;
        }
        return null;
    }

    /** The first index of key in sorted keys, or -1. */
    static int first(long[] keys, long key) {
        int i = Arrays.binarySearch(keys, key);
        if (i < 0) return -1;
        while (i > 0 && keys[i - 1] == key) i--;
        return i;
    }
}
