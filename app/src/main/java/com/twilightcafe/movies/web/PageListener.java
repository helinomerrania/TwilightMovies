package com.twilightcafe.movies.web;

import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

/** What the website tells the app (through PageClient and ChromeClient), on the main thread unless it says. */
public interface PageListener {
    /** Every address the page and its frames load, on the web engine's threads: null to load it, or what to answer. */
    WebResourceResponse intercept(WebResourceRequest request);

    /** Whether the page itself may go to url (a link, a script's jump, a redirect). */
    boolean mayGo(String url);

    void started(String url);

    /** The new page is on the screen (Android 6 and newer). */
    void shown(String url);

    void finished(String url);

    void progress(int percent);

    /** The page itself couldn't load; why: the web engine's own words. */
    void failed(String url, String why);

    /** A security error on url (code: Android's SslError) while pageUrl is shown. Nothing was loaded from it. */
    void insecure(String url, String pageUrl, int code);

    /** A key the page didn't use: it ends with the app, never with Android. */
    void keyNotUsed(KeyEvent e);

    /** The page's engine crashed (often low memory on TV boxes). */
    void crashed(WebView view);

    /** The page's video full screen, with its own player's controls. */
    void fullscreen(View video, WebChromeClient.CustomViewCallback callback);

    void fullscreenEnd();

    /** The page opens a window: true if the app takes it (and answers msg). */
    boolean popup(boolean userGesture, Message msg);

    /** A script of the page's own that the box's engine can't even read; source: where it came from, or "". */
    void scriptError(String source);
}
