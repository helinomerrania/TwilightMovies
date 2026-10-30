package com.twilightcafe.movies.loading;

import static com.twilightcafe.movies.test.Check.eq;

/** The error screen's waits between tries. */
public final class BackoffTest {
    private BackoffTest() {
    }

    public static void run() {
        Backoff b = new Backoff();
        StringBuilder waits = new StringBuilder();
        for (int i = 0; i < 6; i++) waits.append(b.next()).append(' ');
        eq("10 seconds, 20, 40, then every minute", waits.toString().trim(), "10 20 40 60 60 60");
        b.reset();
        eq("from the start again once a page loads (or the internet is back)", b.next(), Backoff.FIRST_S);
        eq("...and longer again after that", b.next(), 2 * Backoff.FIRST_S);
    }
}
