package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.Hash;
import com.twilightcafe.movies.adblock.engine.NetFilter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** A network rule as read from a list, before it becomes a NetFilter: its pattern, flags, types and options. */
final class RuleSpec {
    static final int DOC_ALLOW = 1;     // @@...$document: leave the page alone
    static final int NO_COSMETIC = 2;   // @@...$elemhide
    static final int NO_GENERIC = 4;    // @@...$generichide

    boolean exception;
    boolean badfilter;
    int flags;
    int types;
    int special;
    String pattern;
    String host;            // for ||host^ with options
    String redirect;
    final Set<String> domIn = new LinkedHashSet<>();
    final Set<String> domOut = new LinkedHashSet<>();
    final Set<String> toIn = new LinkedHashSet<>();
    final Set<String> toOut = new LinkedHashSet<>();
    final Set<String> deny = new LinkedHashSet<>();

    NetFilter toFilter() {
        return new NetFilter(flags, types, pattern, redirect, hashes(domIn), hashes(domOut), hashes(toIn),
                hashes(toOut), hashes(deny));
    }

    /** The sorted hashes of a set of hosts, each once. */
    static long[] hashes(Set<String> set) {
        long[] a = new long[set.size()];
        int i = 0;
        for (String s : set) a[i++] = Hash.of(s);
        Arrays.sort(a);
        int n = 0;
        for (int j = 0; j < a.length; j++) {
            if (j == 0 || a[j] != a[j - 1]) a[n++] = a[j];
        }
        return Arrays.copyOf(a, n);
    }
}
