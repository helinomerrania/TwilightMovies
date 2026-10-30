package com.twilightcafe.movies.app;

import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.adblock.AdBlocker;
import com.twilightcafe.movies.web.PageListener;

/** What the website tells the app, handed to the parts it's for. */
final class PageEvents implements PageListener {
    private final MoviesApp app;
    private final AdBlocker blocker;   // asked on the web engine's own threads
    private boolean engineNoted;

    PageEvents(MoviesApp app) {
        this.app = app;
        this.blocker = app.blocker;
    }

    @Override
    public WebResourceResponse intercept(WebResourceRequest request) {
        return blocker.intercept(request);
    }

    @Override
    public boolean mayGo(String url) {
        return app.blocking.mayGo(url);
    }

    @Override
    public void started(String url) {
        app.loading.show();
        app.blocking.pageStarted();
        blocker.setPage(url);
    }

    @Override
    public void shown(String url) {
        app.splash.fade();
        app.blocking.hideAds();
    }

    @Override
    public void finished(String url) {
        app.loading.hide();
        app.splash.fade();
        app.blocking.hideAds();
        app.errors.finished(url);
    }

    @Override
    public void progress(int percent) {
        app.loading.set(percent);
        if (percent >= 70) app.splash.fade();
        app.blocking.progress(percent);
    }

    @Override
    public void failed(String url, String why) {
        app.errors.failed(url, why);
    }

    @Override
    public void insecure(String url, String pageUrl, int code) {
        app.errors.insecure(url, pageUrl, code);
    }

    /** Media keys the app does itself; page up and down (and the channel keys) scroll. */
    @Override
    public void keyNotUsed(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || !app.page.alive() || app.media.notUsed(e)) return;
        int c = e.getKeyCode();
        boolean up = c == KeyEvent.KEYCODE_CHANNEL_UP || c == KeyEvent.KEYCODE_PAGE_UP;
        boolean down = c == KeyEvent.KEYCODE_CHANNEL_DOWN || c == KeyEvent.KEYCODE_PAGE_DOWN;
        if ((up || down) && !app.fullscreen.active() && e.getRepeatCount() % 4 == 0) app.pointer.scrollMiddle(up ? -0.8f : 0.8f);
    }

    @Override
    public void crashed(WebView view) {
        app.nav.crashed(view);
    }

    @Override
    public void fullscreen(View video, WebChromeClient.CustomViewCallback callback) {
        app.fullscreen.enter(video, callback);
    }

    @Override
    public void fullscreenEnd() {
        app.fullscreen.leave();
    }

    @Override
    public boolean popup(boolean userGesture, Message msg) {
        return app.popups.open(userGesture, msg);
    }

    /** One of the website's own scripts the box's engine can't even read: the engine is too old (said once). */
    @Override
    public void scriptError(String source) {
        if (engineNoted || app.engine <= 0 || app.engine >= 80) return;
        if (!source.isEmpty() && !app.sites.shows(source)) return;
        engineNoted = true;
        app.osd.show(app.screen.t(R.string.osd_old_engine, app.engine), 10000);
    }
}
