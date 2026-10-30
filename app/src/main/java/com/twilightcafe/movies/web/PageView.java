package com.twilightcafe.movies.web;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The WebView that shows the website, set up for a TV: no zoom, the box's own font size ignored (it would
 * break TV layouts), videos that play without a tap first, windows only through the app, no files or
 * location. It loads, reloads, goes back, and runs the app's scripts in the page (PageScripts).
 */
public final class PageView {
    private WebView web;

    public PageView(Context c, PageListener listener) {
        web = new WebView(c);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebViewClient(new PageClient(listener));
        web.setWebChromeClient(new ChromeClient(listener));
    }

    /** The WebView; null once it's gone. */
    public WebView view() {
        return web;
    }

    public boolean alive() {
        return web != null;
    }

    public boolean is(WebView v) {
        return v != null && v == web;
    }

    /** An object the page's scripts may call, under a name (only text goes in and out). */
    public void expose(Object bridge, String name) {
        if (web != null) web.addJavascriptInterface(bridge, name);
    }

    public String url() {
        return web != null ? web.getUrl() : null;
    }

    public void load(String url) {
        if (web != null) web.loadUrl(url);
    }

    public void reload() {
        if (web != null) web.reload();
    }

    public void run(String script) {
        run(script, null);
    }

    public void run(String script, ValueCallback<String> result) {
        if (web != null) web.evaluateJavascript(script, result);
    }

    /** The address of the page the history is at (null if none). */
    public String historyUrl() {
        if (web == null) return null;
        WebHistoryItem item = web.copyBackForwardList().getCurrentItem();
        return item != null ? item.getUrl() : null;
    }

    /** How far back the page before this one is (negative; 0 if none), past redirects to the same address. */
    public int stepsBack() {
        if (web == null) return 0;
        WebBackForwardList list = web.copyBackForwardList();
        WebHistoryItem current = list.getCurrentItem();
        if (current == null) return 0;
        int index = list.getCurrentIndex();
        String here = current.getUrl();
        for (int i = index - 1; i >= 0; i--) {
            WebHistoryItem item = list.getItemAtIndex(i);
            String url = item != null ? item.getUrl() : null;
            if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.equals(here)) continue;
            return i - index;
        }
        return 0;
    }

    public void go(int steps) {
        if (web != null) web.goBackOrForward(steps);
    }

    /** The app leaves the screen: the script first (it pauses the videos), then the page's timers. */
    public void pause(String script) {
        if (web != null) {
            web.evaluateJavascript(script, null);
            web.onPause();
            web.pauseTimers();
        }
        CookieManager.getInstance().flush();
    }

    public void resume() {
        if (web == null) return;
        web.onResume();
        web.resumeTimers();
    }

    public void destroy() {
        if (web == null) return;
        remove();
        web.stopLoading();
        web.destroy();
        web = null;
    }

    /** After a crash: the view is dead, only taken away. */
    public void drop() {
        if (web == null) return;
        remove();
        web.destroy();
        web = null;
    }

    private void remove() {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
    }

    /** The main version of the box's own web engine (Chrome 66 -> 66), 0 if unknown. */
    public static int engineVersion(Context c) {
        try {
            Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(WebSettings.getDefaultUserAgent(c));
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
