package com.twilightcafe.movies.update;

import static com.twilightcafe.movies.test.Check.eq;

/** The app's releases on GitHub: build numbers from their names. */
public final class ReleaseTest {
    private ReleaseTest() {
    }

    public static void run() {
        eq("build-12-1 is build 12", Release.buildNumber("build-12-1"), 12);
        eq("build-7-3 (run again) is build 7", Release.buildNumber("build-7-3"), 7);
        eq("the ad blocker's filters aren't a build", Release.buildNumber("filters"), 0);
        eq("nor another name", Release.buildNumber("v1.2"), 0);
        eq("nor a broken one", Release.buildNumber("build-x-1"), 0);
        eq("nor nothing", Release.buildNumber(""), 0);
        eq("the app's file", Release.APK, "twilight-movies.apk");
    }
}
