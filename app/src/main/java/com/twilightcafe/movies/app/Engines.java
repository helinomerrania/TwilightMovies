package com.twilightcafe.movies.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.twilightcafe.movies.settings.Settings;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.PageListener;
import com.twilightcafe.movies.web.RequestFilter;
import com.twilightcafe.movies.web.gecko.GeckoEngine;
import com.twilightcafe.movies.web.gecko.GeckoPage;
import com.twilightcafe.movies.web.webview.WebViewPage;

/**
 * Which web engine shows the websites: the box's own (Android's WebView) when it's new enough for them, else
 * Firefox's, which comes inside the app (Android 8.0 and newer). The menu can choose either for the box
 * (Settings.engine). If Firefox's can't start, the box's own.
 */
final class Engines {
    /** From this version on, the box's own engine runs the websites' players (KurdFilm's needs Chrome 80 or newer). */
    static final int NEW_ENOUGH = 80;
    private static final String TAG = "TwilightMovies";

    private Engines() {
    }

    static Page make(Activity a, Handler main, Settings settings, PageListener listener, RequestFilter filter) {
        int own = WebViewPage.engineVersion(a);
        if (firefox(a, settings, own)) {
            try {
                Page p = new GeckoPage(a, main, listener, filter);
                Log.i(TAG, "web engine: Firefox " + p.engineVersion() + ", inside the app (the box's own: " + own + ")");
                return p;
            } catch (Throwable e) {   // it didn't start: the box's own
                Log.w(TAG, "Firefox's engine didn't start, so the box's own", e);
            }
        }
        Log.i(TAG, "web engine: the box's own, " + own);
        return new WebViewPage(a, main, listener, filter);
    }

    private static boolean firefox(Context c, Settings settings, int own) {
        if (!GeckoEngine.usable(c)) return false;
        String chosen = settings.engine();
        if (Settings.ENGINE_FIREFOX.equals(chosen)) return true;
        if (Settings.ENGINE_OWN.equals(chosen)) return false;
        return own < NEW_ENOUGH;   // 0: not known, or none at all
    }

    /** Whether the menu offers both (Firefox's engine can run on this box). */
    static boolean choice(Context c) {
        return GeckoEngine.usable(c);
    }

    /**
     * The other engine, from now on: the app starts again (there's no taking an engine out of a running app;
     * Firefox's stays until the app ends), at the website's start.
     */
    static void switchTo(Activity a, Settings settings, boolean firefox) {
        settings.setEngine(firefox ? Settings.ENGINE_FIREFOX : Settings.ENGINE_OWN);
        Intent i = a.getPackageManager().getLaunchIntentForPackage(a.getPackageName());
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            a.startActivity(i);
        }
        a.finish();
        Runtime.getRuntime().exit(0);
    }
}
