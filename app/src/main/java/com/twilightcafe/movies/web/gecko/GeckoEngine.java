package com.twilightcafe.movies.web.gecko;

import android.content.Context;
import android.os.Build;

import com.twilightcafe.movies.util.Streams;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Firefox's engine (GeckoView 157), which comes inside the app. It starts once for the whole app and stays
 * (there can't be two); its pages (GeckoPage) are made again whenever the app starts again. With it starts the
 * app's own Extension: the ad blocker, and the app's scripts in the page.
 */
public final class GeckoEngine {
    private static GeckoRuntime runtime;
    private static Extension extension;

    private GeckoEngine() {
    }

    /** Whether it can run on this box: Android 8.0 or newer, and the engine's code for its processor. */
    public static boolean usable(Context c) {
        if (Build.VERSION.SDK_INT < 26) return false;
        String dir = c.getApplicationInfo().nativeLibraryDir;
        return dir != null && new File(dir, "libxul.so").isFile();
    }

    /** Its main version (157), from its own user agent; 0 if unknown. */
    public static int version() {
        try {
            Matcher m = Pattern.compile("rv:(\\d+)").matcher(GeckoSession.getDefaultUserAgent());
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The engine, started once for the app; pageLog: the pages' own messages in Android's log (try builds only). */
    static GeckoRuntime runtime(Context c, boolean pageLog) {
        if (runtime == null) {
            Context app = c.getApplicationContext();
            GeckoRuntimeSettings.Builder s = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(pageLog)
                    .doubleTapZoomingEnabled(false)
                    .inputAutoZoomEnabled(false)
                    .automaticFontSizeAdjustment(false)    // the box's own font size would break TV layouts
                    .fontSizeFactor(1f)
                    .loginAutofillEnabled(false)
                    .aboutConfigEnabled(false);
            String config = writeConfig(app);
            if (config != null) s.configFilePath(config);
            runtime = GeckoRuntime.create(app, s.build());
        }
        return runtime;
    }

    static Extension extension(GeckoRuntime r) {
        if (extension == null) extension = new Extension(r);
        return extension;
    }

    /**
     * Its settings at the start: a film's picture is never put to sleep, for being out of sight or paused a
     * while (on TV boxes it can stay frozen when it plays again, while the sound goes on; Twilight TV saw it).
     */
    private static String writeConfig(Context c) {
        String yaml = "prefs:\n"
                + "  media.suspend-background-video.enabled: false\n"
                + "  media.dormant-on-pause-timeout-ms: -1\n";
        File f = new File(c.getFilesDir(), "gecko-config.yaml");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(yaml.getBytes("UTF-8"));
            return f.getAbsolutePath();
        } catch (IOException e) {
            return null;
        } finally {
            Streams.close(out);
        }
    }
}
