package com.twilightcafe.movies.loading;

/** How long the error screen waits before each try: 10 seconds, then 20, 40, and every 60 after that. Plain Java. */
public final class Backoff {
    static final int FIRST_S = 10;
    static final int MAX_S = 60;
    private int next = FIRST_S;

    /** The wait before the next try, in seconds (each one longer, up to a minute). */
    public int next() {
        int n = next;
        next = Math.min(MAX_S, next * 2);
        return n;
    }

    /** From the start again (the page loaded, or the internet came back). */
    public void reset() {
        next = FIRST_S;
    }
}
