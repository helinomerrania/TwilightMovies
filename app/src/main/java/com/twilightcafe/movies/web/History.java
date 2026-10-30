package com.twilightcafe.movies.web;

/** The page's history, as both web engines keep it: where BACK goes. Plain Java. */
public final class History {
    private History() {
    }

    /**
     * How far back the page before urls[current] is (negative; 0 if none): past redirects to the same address,
     * and past what isn't a page of its own (about:blank, the blocked page's data:).
     */
    public static int stepsBack(String[] urls, int current) {
        if (urls == null || current < 0 || current >= urls.length || urls[current] == null) return 0;
        String here = urls[current];
        for (int i = current - 1; i >= 0; i--) {
            String url = urls[i];
            if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.equals(here)) continue;
            return i - current;
        }
        return 0;
    }
}
