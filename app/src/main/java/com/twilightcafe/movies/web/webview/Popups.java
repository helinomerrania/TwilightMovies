package com.twilightcafe.movies.web.webview;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.twilightcafe.movies.web.PageListener;

/**
 * Windows the page opens, in Android's WebView. One the viewer clicked is given a hidden WebView, only to see
 * where it goes: that address goes to PageListener.popup (the app may open it in its own page), and the window
 * is gone. Windows a script opens by itself (ads) never get one.
 */
final class Popups {
    private final Context ctx;
    private final Handler main;
    private final PageListener listener;

    Popups(Context ctx, Handler main, PageListener listener) {
        this.ctx = ctx;
        this.main = main;
        this.listener = listener;
    }

    /** For WebChromeClient.onCreateWindow: true if the window is taken (to see where it goes). */
    boolean open(boolean userGesture, Message msg) {
        if (!userGesture || msg == null || !(msg.obj instanceof WebView.WebViewTransport)) return false;
        final WebView popup = new WebView(ctx);
        final boolean[] done = {false};
        final Runnable dispose = new Runnable() {
            @Override
            public void run() {
                try {
                    popup.stopLoading();
                    popup.destroy();
                } catch (Throwable ignored) {
                    // already gone
                }
            }
        };
        popup.setWebViewClient(new WebViewClient() {
            private void take(String url) {
                if (done[0] || url == null || url.startsWith("about:")) return;
                done[0] = true;
                listener.popup(url, true);
                main.post(dispose);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                take(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                take(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                take(url);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (!done[0]) {
                    done[0] = true;
                    main.post(dispose);
                }
                return true;
            }
        });
        ((WebView.WebViewTransport) msg.obj).setWebView(popup);
        msg.sendToTarget();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                dispose.run();
            }
        }, 15000);
        return true;
    }
}
