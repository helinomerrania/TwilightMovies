package com.twilightcafe.movies.adblock.surrogates;

/** Base64 to bytes (java.util.Base64 needs Android 8.0; this app runs on 5.0). */
final class Base64 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Base64() {
    }

    static byte[] decode(String s) {
        int[] v = new int[128];
        for (int i = 0; i < 128; i++) v[i] = -1;
        for (int i = 0; i < 64; i++) v[ALPHABET.charAt(i)] = i;
        byte[] out = new byte[s.length() * 3 / 4];
        int n = 0;
        int bits = 0;
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || v[c] < 0) continue;   // '=' and anything else
            acc = (acc << 6) | v[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (byte) (acc >> bits);
            }
        }
        byte[] r = new byte[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }
}
