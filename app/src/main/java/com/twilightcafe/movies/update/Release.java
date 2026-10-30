package com.twilightcafe.movies.update;

/** The app's releases on GitHub: tagged "build-N-M" (N: the build's number), with the app as one file. Plain Java. */
public final class Release {
    public static final String APK = "twilight-movies.apk";
    /** The app is about 90 MB with Firefox's engine inside it: anything near this isn't it. */
    public static final long MAX_APK = 250L << 20;

    private Release() {
    }

    /** "build-12-1" -> 12 (0 if it isn't one of the app's). */
    public static int buildNumber(String tag) {
        try {
            String[] p = tag.split("-");
            return p.length >= 2 && p[0].equals("build") ? Integer.parseInt(p[1]) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
