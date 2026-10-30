package com.twilightcafe.movies.adblock.engine;

import java.util.Arrays;

/**
 * The public suffix list (publicsuffix.org): which part of a host anyone can register under, such as "com",
 * "co.uk", "com.iq" or "blogspot.com". It tells which site a host belongs to: www.example.co.uk and
 * cdn.example.co.uk are the same site, example.co.uk; a.blogspot.com and b.blogspot.com are not. That
 * decides what is "third-party" for the filter rules, and the "example.*" names some rules use.
 */
public final class PublicSuffix {
    final long[] rules;               // "co.uk"
    final long[] wildcards;           // "*.ck" kept as "ck"
    final long[] exceptions;          // "!www.ck" kept as "www.ck"

    public PublicSuffix(long[] rules, long[] wildcards, long[] exceptions) {
        this.rules = rules;
        this.wildcards = wildcards;
        this.exceptions = exceptions;
    }

    /** Where the public suffix starts in host (0 when the whole host is one, or for an IP address). */
    int suffixStart(String host) {
        int n = host.length();
        if (n == 0 || isIp(host)) return 0;
        // label starts from the right: starts[0] is the last label's
        int[] starts = new int[16];
        int count = 0;
        for (int i = n - 1; i >= -1 && count < starts.length; i--) {
            if (i == -1 || host.charAt(i) == '.') starts[count++] = i + 1;
        }
        int best = 1;   // the default rule "*": the last label alone
        for (int k = 1; k <= count; k++) {
            long h = Hash.of(host, starts[k - 1], n);
            if (Arrays.binarySearch(exceptions, h) >= 0) return k >= 2 ? starts[k - 2] : starts[0];
            if (Arrays.binarySearch(rules, h) >= 0) best = k;
            if (k >= 2 && Arrays.binarySearch(wildcards, Hash.of(host, starts[k - 2], n)) >= 0) best = k;
        }
        return starts[Math.min(best, count) - 1];
    }

    /** The registrable domain: the public suffix and one label more ("example.co.uk"), or the host itself. */
    String registrable(String host) {
        int s = suffixStart(host);
        if (s <= 1) return host;
        int dot = host.lastIndexOf('.', s - 2);
        return host.substring(dot + 1);
    }

    static boolean isIp(String host) {
        if (host.indexOf(':') >= 0 || host.startsWith("[")) return true;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if ((c < '0' || c > '9') && c != '.') return false;
        }
        return true;
    }
}
