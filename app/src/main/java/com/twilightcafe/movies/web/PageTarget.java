package com.twilightcafe.movies.web;

import android.view.KeyEvent;
import android.view.View;

/**
 * Whatever shows the page now, the WebView or the page's video full screen: where the remote's keys, the
 * pointer's touches and the focus go.
 */
public final class PageTarget {
    private final PageView page;
    private View fullscreen;

    public PageTarget(PageView page) {
        this.page = page;
    }

    /** null once the page is gone. */
    public View view() {
        return fullscreen != null ? fullscreen : page.view();
    }

    public boolean alive() {
        return page.alive();
    }

    public boolean fullscreen() {
        return fullscreen != null;
    }

    /** The page's video full screen (null: the page again). */
    public void setFullscreen(View video) {
        fullscreen = video;
    }

    /** A key, to the page (which gets the focus first). */
    public void send(KeyEvent e) {
        View t = view();
        if (t == null) return;
        if (!t.hasFocus()) t.requestFocus();
        t.dispatchKeyEvent(e);
    }

    public void focus() {
        View t = view();
        if (t != null) t.requestFocus();
    }
}
