package com.twilightcafe.movies.web;

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

/** The page's loading, from Android's WebViewClient to the app's PageListener. */
final class PageClient extends WebViewClient {
    private final PageListener l;

    PageClient(PageListener l) {
        this.l = l;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (!Urls.isWebScheme(url)) return true;   // intent:, market: and the like: never (ads love them)
        return !l.mayGo(url);                      // Android 5 and 6: the page itself only
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (!Urls.isWebScheme(url)) return true;
        return request.isForMainFrame() && !l.mayGo(url);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return l.intercept(request);
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
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        l.failed(failingUrl, description);   // Android 5.x
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
        l.crashed(view);
        return true;
    }
}
