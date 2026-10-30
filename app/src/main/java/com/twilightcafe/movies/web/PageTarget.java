package com.twilightcafe.movies.web;

import android.view.KeyEvent;
import android.view.View;

/**
 * Whatever shows the page now, the page's view or its video full screen: where the remote's keys, the
 * pointer's touches and the focus go.
 */
public final class PageTarget {
    private final Page page;
    private boolean fullscreen;
    private View video;

    public PageTarget(Page page) {
        this.page = page;
    }

    /** null once the page is gone. */
    public View view() {
        return video != null ? video : page.view();
    }

    public boolean alive() {
        return page.alive();
    }

    /** Whether the page's engine says which keys the page didn't use (else the app can't know). */
    public boolean reportsUnusedKeys() {
        return page.reportsUnusedKeys();
    }

    public boolean fullscreen() {
        return fullscreen;
    }

    /** The page's video full screen, in its own view (or null: the page's view shows it); off: the page again. */
    public void setFullscreen(boolean on, View video) {
        fullscreen = on;
        this.video = on ? video : null;
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
