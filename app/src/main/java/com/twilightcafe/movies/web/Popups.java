package com.twilightcafe.movies.web;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Windows the page opens. One the viewer clicked opens in the app's own page, if the app allows where it goes;
 * ad pop-ups (opened by a script, or going to another site) are dropped. The window itself is never shown.
 */
public final class Popups {
    public interface Rules {
        /** Whether the app's page may go to url. */
        boolean allowed(String url);

        void open(String url);

        /** A window the app didn't open (the viewer is told). */
        void refused();
    }

    private final Context ctx;
    private final Handler main;
    private final Rules rules;

    public Popups(Context ctx, Handler main, Rules rules) {
        this.ctx = ctx;
        this.main = main;
        this.rules = rules;
    }

    /** For WebChromeClient.onCreateWindow: true if the window is taken (to see where it goes). */
    public boolean open(boolean userGesture, Message msg) {
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
                if (rules.allowed(url)) rules.open(url);
                else rules.refused();
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
