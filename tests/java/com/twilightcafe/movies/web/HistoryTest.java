package com.twilightcafe.movies.web;

import static com.twilightcafe.movies.test.Check.eq;

/** Where BACK goes in the page's history, in either web engine (web/History). */
public final class HistoryTest {
    private HistoryTest() {
    }

    public static void run() {
        String a = "https://films.example/";
        String b = "https://films.example/film/7";
        eq("back one page", History.stepsBack(new String[]{a, b}, 1), -1);
        eq("the first page: nowhere", History.stepsBack(new String[]{a, b}, 0), 0);
        eq("past a redirect to the same address", History.stepsBack(new String[]{a, b, b}, 2), -2);
        eq("past about:blank and the blocked page", History.stepsBack(new String[]{a, "about:blank", "data:text/html,x", b}, 3), -3);
        eq("only those before it: nowhere", History.stepsBack(new String[]{"about:blank", b}, 1), 0);
        eq("in the middle of the history (after going back)", History.stepsBack(new String[]{a, b, a}, 1), -1);
        eq("no history yet", History.stepsBack(new String[0], -1), 0);
        eq("a place past its end", History.stepsBack(new String[]{a}, 3), 0);
        eq("none at all", History.stepsBack(null, 0), 0);
    }
}
