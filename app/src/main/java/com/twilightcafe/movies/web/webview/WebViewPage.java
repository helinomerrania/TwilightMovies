package com.twilightcafe.movies.web.webview;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.twilightcafe.movies.web.Bridge;
import com.twilightcafe.movies.web.History;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.PageListener;
import com.twilightcafe.movies.web.RequestFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The website in the box's own web engine (Android's WebView), set up for a TV: no zoom, the box's own font
 * size ignored (it would break TV layouts), videos that play without a tap first, windows only through the
 * app, no files or location.
 */
public final class WebViewPage implements Page {
    private final int version;
    private WebView web;

    public WebViewPage(Context c, Handler main, PageListener listener, RequestFilter filter) {
        version = engineVersion(c);
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
        web.setWebViewClient(new PageClient(listener, filter));
        web.setWebChromeClient(new ChromeClient(listener, new Popups(c, main, listener)));
    }

    @Override
    public View view() {
        return web;
    }

    @Override
    public boolean alive() {
        return web != null;
    }

    @Override
    public boolean inApp() {
        return false;
    }

    @Override
    public int engineVersion() {
        return version;
    }

    @Override
    public boolean reportsUnusedKeys() {
        return true;
    }

    /** Only text goes in and out (JsBridge). */
    @Override
    public void expose(Bridge bridge, String name) {
        if (web != null) web.addJavascriptInterface(new JsBridge(bridge), name);
    }

    @Override
    public String url() {
        return web != null ? web.getUrl() : null;
    }

    @Override
    public void load(String url) {
        if (web != null) web.loadUrl(url);
    }

    @Override
    public void reload() {
        if (web != null) web.reload();
    }

    @Override
    public void run(String script, final Result result) {
        if (web != null) web.evaluateJavascript(script, result == null ? null : v -> result.value(v));
    }

    @Override
    public String historyUrl() {
        if (web == null) return null;
        WebHistoryItem item = web.copyBackForwardList().getCurrentItem();
        return item != null ? item.getUrl() : null;
    }

    @Override
    public int stepsBack() {
        if (web == null) return 0;
        WebBackForwardList list = web.copyBackForwardList();
        WebHistoryItem current = list.getCurrentItem();
        if (current == null) return 0;
        String[] urls = new String[list.getSize()];
        for (int i = 0; i < urls.length; i++) {
            WebHistoryItem item = list.getItemAtIndex(i);
            urls[i] = item != null ? item.getUrl() : null;
        }
        return History.stepsBack(urls, list.getCurrentIndex());
    }

    @Override
    public void go(int steps) {
        if (web != null) web.goBackOrForward(steps);
    }

    @Override
    public void pause(String script) {
        if (web != null) {
            web.evaluateJavascript(script, null);
            web.onPause();
            web.pauseTimers();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    public void resume() {
        if (web == null) return;
        web.onResume();
        web.resumeTimers();
    }

    @Override
    public void destroy() {
        if (web == null) return;
        remove();
        web.stopLoading();
        web.destroy();
        web = null;
    }

    @Override
    public void drop() {
        if (web == null) return;
        remove();
        web.destroy();
        web = null;
    }

    private void remove() {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
    }

    /**
     * The main version of the box's own web engine (Chrome 66: 66), 0 if unknown or missing: from Android 8.0
     * on, without starting it (its package says); before, from what it calls itself.
     */
    public static int engineVersion(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                PackageInfo p = WebView.getCurrentWebViewPackage();
                if (p != null && p.versionName != null) return major(Pattern.compile("^(\\d+)"), p.versionName);
            }
            return major(Pattern.compile("Chrome/(\\d+)"), WebSettings.getDefaultUserAgent(c));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int major(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
