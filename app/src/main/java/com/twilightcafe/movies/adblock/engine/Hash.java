package com.twilightcafe.movies.adblock.engine;

/**
 * 64-bit hashes of host names, address tokens and the keys of ad-hiding rules. The filter file keeps hashes
 * instead of the names: half a million blocked hosts take 8 bytes each in memory. FNV-1a over the
 * characters, then the splitmix64 finalizer for an even spread. Everything hashed is lower-case ASCII
 * (hosts arrive in punycode), except the class and id names of ad-hiding rules, which keep their case.
 */
public final class Hash {
    private static final long START = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private Hash() {
    }

    public static long of(CharSequence s) {
        return of(s, 0, s.length());
    }

    public static long of(CharSequence s, int from, int to) {
        return finish(add(START, s, from, to));
    }

    /** The hash of s[from, to) followed by ".*": the name of an "entity" such as example.* in the lists. */
    static long entity(CharSequence s, int from, int to) {
        long h = add(START, s, from, to);
        h = (h ^ '.') * PRIME;
        h = (h ^ '*') * PRIME;
        return finish(h);
    }

    private static long add(long h, CharSequence s, int from, int to) {
        for (int i = from; i < to; i++) {
            h ^= s.charAt(i);
            h *= PRIME;
        }
        return h;
    }

    private static long finish(long h) {
        h ^= h >>> 30;
        h *= 0xbf58476d1ce4e5b9L;
        h ^= h >>> 27;
        h *= 0x94d049bb133111ebL;
        h ^= h >>> 31;
        return h;
    }
}
