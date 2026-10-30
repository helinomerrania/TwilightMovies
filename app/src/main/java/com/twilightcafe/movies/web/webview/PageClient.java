package com.twilightcafe.movies.web.webview;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.view.KeyEvent;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.twilightcafe.movies.util.Urls;
import com.twilightcafe.movies.web.PageListener;
import com.twilightcafe.movies.web.RequestFilter;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/** The page's loading, from Android's WebViewClient to the app's PageListener; every address to its RequestFilter. */
final class PageClient extends WebViewClient {
    private final PageListener l;
    private final RequestFilter filter;

    PageClient(PageListener l, RequestFilter filter) {
        this.l = l;
        this.filter = filter;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (!Urls.isWebScheme(url)) return true;   // intent:, market: and the like: never (ads love them)
        return !l.mayGo(url);                      // Android 6: the page itself only
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (!Urls.isWebScheme(url)) return true;
        return request.isForMainFrame() && !l.mayGo(url);
    }

    /** On the WebView's own threads. */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String kind = request.isForMainFrame() ? RequestFilter.PAGE : null;
        RequestFilter.Answer a = filter.check(request.getUrl().toString(), kind, null, null, request.getRequestHeaders());
        if (a == null) return null;
        Map<String, String> h = new HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-store");
        return new WebResourceResponse(a.mime, "UTF-8", 200, "OK", h, new ByteArrayInputStream(a.data));
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        l.started(url);
    }

    @Override
    public void onPageCommitVisible(WebView view, String url) {
        l.shown(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        l.finished(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (!request.isForMainFrame()) return;
        CharSequence d = error.getDescription();
        l.failed(request.getUrl().toString(), d != null ? d.toString() : "error " + error.getErrorCode());
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
        l.insecure(error.getUrl(), view.getUrl(), error.getPrimaryError());
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        l.keyNotUsed(event);   // never handed back to Android: everything the page doesn't use ends with the app
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        l.crashed();
        return true;
    }
}
