package com.twilightcafe.movies.adblock.engine;

import java.util.Arrays;

/**
 * Hosts as the rules see them: the hashes of a host and of each host it is under (a.b.com, b.com, com) and of
 * its "entity" names (a.b.*, b.*), which site it belongs to (the public suffix list), and the words of an
 * address. It fills in a Request before the rules are asked about it.
 */
public final class Hosts {
    private final PublicSuffix psl;

    public Hosts(PublicSuffix psl) {
        this.psl = psl;
    }

    PublicSuffix psl() {
        return psl;
    }

    /** The site a host belongs to (www.example.co.uk -> example.co.uk). */
    public String registrable(String host) {
        return psl.registrable(host);
    }

    long[] hashes(String host) {
        int n = host.length();
        if (n == 0) return new long[0];
        int suffix = psl.suffixStart(host);
        int labels = 1;
        for (int i = 0; i < n; i++) {
            if (host.charAt(i) == '.') labels++;
        }
        long[] out = new long[labels * 2];
        int c = 0;
        int nameEnd = suffix > 0 ? suffix - 1 : -1;   // "a.example" in a.example.co.uk
        for (int i = 0; i < n; i = host.indexOf('.', i) + 1) {
            out[c++] = Hash.of(host, i, n);
            if (nameEnd > i) out[c++] = Hash.entity(host, i, nameEnd);
            if (host.indexOf('.', i) < 0) break;
        }
        return c == out.length ? out : Arrays.copyOf(out, c);
    }

    void prepare(Request r) {
        if (r.prepared) return;
        r.prepared = true;
        r.hostHashes = hashes(r.host);
        r.hostCount = r.hostHashes.length;
        if (r.docHost != null) {
            r.docHashes = hashes(r.docHost);
            r.docCount = r.docHashes.length;
            r.thirdParty = !registrable(r.host).equals(registrable(r.docHost));
        }
        tokenize(r);
    }

    /** The address's words (runs of letters, digits and %), as hashes: how its rules are found. */
    private static void tokenize(Request r) {
        String u = r.lower;
        int n = Math.min(u.length(), 2048);
        long[] t = new long[32];
        int c = 0;
        int i = 0;
        while (i < n && c < 64) {
            if (!UrlPattern.tokenChar(u.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && UrlPattern.tokenChar(u.charAt(i))) i++;
            if (i - s < 2) continue;
            if (c == t.length) t = Arrays.copyOf(t, c * 2);
            t[c++] = Hash.of(u, s, i);
        }
        r.tokens = t;
        r.tokenCount = c;
    }
}
