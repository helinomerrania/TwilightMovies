package com.twilightcafe.movies.app;

import android.util.Log;

import com.twilightcafe.movies.adblock.AdBlocker;
import com.twilightcafe.movies.adblock.surrogates.Surrogates;
import com.twilightcafe.movies.web.Bridge;
import com.twilightcafe.movies.web.RequestFilter;

import java.util.Map;

/**
 * The ad blocker, as either web engine asks it (on the engine's threads): every address the page loads
 * (RequestFilter), and what the page's ad-hiding script asks (Bridge). So web/ knows nothing of the ad
 * blocker, and the ad blocker nothing of the engines. What it blocks goes in Android's log (adb logcat -s
 * TwilightMovies), to see when a website needs a rule of its own.
 */
final class BlockerLink implements RequestFilter, Bridge {
    private final AdBlocker blocker;

    BlockerLink(AdBlocker blocker) {
        this.blocker = blocker;
    }

    @Override
    public Answer check(String url, String kind, String frameUrl, String pageUrl, Map<String, String> headers) {
        Surrogates.Body b = blocker.check(url, kind, frameUrl, pageUrl, headers);
        if (b == null) return null;
        Log.i("TwilightMovies", "blocked " + (kind != null ? kind : "") + " " + url);
        return new Answer(b.mime, b.data);
    }

    @Override
    public String ask(String question, String a, String b) {
        return blocker.cosmetic(question, a, b);
    }
}
