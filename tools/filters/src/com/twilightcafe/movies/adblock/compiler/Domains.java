package com.twilightcafe.movies.adblock.compiler;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/** Hosts as the lists write them: in ASCII (punycode), lower-case, and "example.*" kept as an entity. */
final class Domains {
    /** A plain host, as after "||" in a rule (an optional ^ at its end). */
    static final Pattern HOST = Pattern.compile("^[a-z0-9_][a-z0-9_.-]*\\^?$");

    private Domains() {
    }

    /** h in ASCII, lower-case; null if it can't be a host. */
    static String ascii(String h) {
        if (h == null || h.isEmpty()) return null;
        try {
            if (h.endsWith(".*")) return ascii(h.substring(0, h.length() - 2)) + ".*";
            for (int i = 0; i < h.length(); i++) {
                if (h.charAt(i) >= 128) return IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            }
            return h;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
