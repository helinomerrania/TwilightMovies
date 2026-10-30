package com.twilightcafe.movies.adblock;

import android.webkit.JavascriptInterface;

/**
 * What the page's hide-ads script (assets/scripts/hide-ads.js) asks the app, on the web engine's own thread.
 * Only CSS comes back: the rules that hide the site's ad boxes, then the generic ones for the classes and ids
 * the page has.
 */
public final class CosmeticBridge {
    private final AdBlocker blocker;

    CosmeticBridge(AdBlocker blocker) {
        this.blocker = blocker;
    }

    @JavascriptInterface
    public String page(String host) {
        return blocker.pageCss(host);
    }

    @JavascriptInterface
    public String generic(String host, String keys) {
        return blocker.genericCss(host, keys);
    }
}
