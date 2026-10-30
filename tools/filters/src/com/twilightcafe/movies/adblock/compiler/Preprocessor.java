package com.twilightcafe.movies.adblock.compiler;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The lists' "!#if", "!#else" and "!#endif" lines: which rules are for this app. The app is a Chromium web
 * engine (env_chromium), and the lists are read as uBlock Origin reads them (ext_ublock); anything else
 * (mobile, Firefox, Safari, uBO Lite, HTML filtering...) is false.
 */
final class Preprocessor {
    static final Set<String> ENV = new HashSet<>(Arrays.asList("env_chromium", "ext_ublock"));

    private final Deque<boolean[]> open = new ArrayDeque<>();   // {active outside, the condition}
    private boolean active = true;

    /** Whether line was one of the directives (then it's not a rule). */
    boolean directive(String line) {
        if (line.startsWith("!#if")) {
            boolean c = Condition.eval(line.substring(4).trim(), ENV);
            open.push(new boolean[]{active, c});
            active = active && c;
            return true;
        }
        if (line.startsWith("!#else")) {
            boolean[] top = open.peek();
            if (top != null) active = top[0] && !top[1];
            return true;
        }
        if (line.startsWith("!#endif")) {
            boolean[] top = open.poll();
            if (top != null) active = top[0];
            return true;
        }
        return false;
    }

    /** Whether the lines now read are for this app. */
    boolean active() {
        return active;
    }
}
