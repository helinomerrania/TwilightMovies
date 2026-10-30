package com.twilightcafe.movies.test;

import java.util.Objects;

/** The tests' helper: each check is a sentence saying what it checks, printed "ok" or "FAIL" (with what came out). */
public final class Check {
    static int pass;
    static int fail;

    private Check() {
    }

    /** got must equal want. */
    public static void eq(String name, Object got, Object want) {
        report(name, Objects.equals(got, want), "got " + got + ", expected " + want);
    }

    /** passed must be true; detail says what's wrong when it isn't. */
    public static void ok(String name, boolean passed, Object detail) {
        report(name, passed, String.valueOf(detail));
    }

    public static void ok(String name, boolean passed) {
        report(name, passed, "");
    }

    private static void report(String name, boolean passed, String detail) {
        if (passed) {
            pass++;
            System.out.println("  ok   " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name + (detail.isEmpty() ? "" : ": " + detail));
        }
    }
}
