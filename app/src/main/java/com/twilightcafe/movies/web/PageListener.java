package com.twilightcafe.movies.web;

import android.view.KeyEvent;
import android.view.View;

/** What the website tells the app, from either web engine, on the main thread. */
public interface PageListener {
    /** Whether the page itself may go to url (a link, a script's jump, a redirect). */
    boolean mayGo(String url);

    /** A window the page opened, to url (the window itself never opens; the app's page may go there). */
    void popup(String url, boolean userGesture);

    void started(String url);

    /** The new page is on the screen. */
    void shown(String url);

    void finished(String url);

    void progress(int percent);

    /** The page itself couldn't load; why: the web engine's own words. */
    void failed(String url, String why);

    /** A security error on url (code: the engine's) while pageUrl is shown. Nothing was loaded from it. */
    void insecure(String url, String pageUrl, int code);

    /** A key the page didn't use (only from engines that say so: Page.reportsUnusedKeys). */
    void keyNotUsed(KeyEvent e);

    /** The page's engine crashed (often low memory on TV boxes), or Android closed it. */
    void crashed();

    /**
     * The page's video full screen. video: the view that shows it, to put over the page (Android's WebView), or
     * null when the page's own view does (Firefox's engine); exit: how the page leaves full screen.
     */
    void fullscreen(View video, Runnable exit);

    void fullscreenEnd();

    /** A script of the page's own that the engine can't even read (it's too old); source: from where. */
    void scriptError(String source);
}
