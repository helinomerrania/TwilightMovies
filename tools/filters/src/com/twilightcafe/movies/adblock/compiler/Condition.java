package com.twilightcafe.movies.adblock.compiler;

import java.util.Set;

/** The condition of an "!#if" line in a list: names, !, &&, || and brackets. A name is true if it's in env. */
final class Condition {
    private final String s;
    private final Set<String> env;
    private int i;

    private Condition(String s, Set<String> env) {
        this.s = s;
        this.env = env;
    }

    static boolean eval(String expr, Set<String> env) {
        return new Condition(expr, env).or();
    }

    private void space() {
        while (i < s.length() && s.charAt(i) == ' ') i++;
    }

    private boolean or() {
        boolean v = and();
        space();
        while (s.startsWith("||", i)) {
            i += 2;
            boolean r = and();
            v = v || r;
            space();
        }
        return v;
    }

    private boolean and() {
        boolean v = not();
        space();
        while (s.startsWith("&&", i)) {
            i += 2;
            boolean r = not();
            v = v && r;
            space();
        }
        return v;
    }

    private boolean not() {
        space();
        if (i < s.length() && s.charAt(i) == '!') {
            i++;
            return !not();
        }
        if (i < s.length() && s.charAt(i) == '(') {
            i++;
            boolean v = or();
            space();
            if (i < s.length() && s.charAt(i) == ')') i++;
            return v;
        }
        int start = i;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
        return env.contains(s.substring(start, i));
    }
}
