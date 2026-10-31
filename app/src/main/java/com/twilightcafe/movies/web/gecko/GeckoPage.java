package com.twilightcafe.movies.web.gecko;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.twilightcafe.movies.web.Bridge;
import com.twilightcafe.movies.web.History;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.PageListener;
import com.twilightcafe.movies.web.RequestFilter;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

/**
 * The website in Firefox's engine, which comes inside the app (GeckoView): for boxes whose own engine is too
 * old for the websites. The page's events go to the app through GeckoEvents and GeckoNavigation, its dialogs
 * through GeckoPrompts, and the app's scripts and ad blocker reach it through the app's Extension.
 */
public final class GeckoPage implements Page {
    /** The app's own loads: not asked about as the page's own are (PageListener.mayGo). */
    private static final int OWN_LOAD = GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE;

    final Handler main;
    final RequestFilter filter;
    private final Extension extension;
    private final GeckoKeyboard keyboard;
    private GeckoView view;
    private GeckoSession session;
    String location;                    // the page's address now
    private String[] history = {};      // the session's history, and where in it the page is
    private int historyAt = -1;
    private String waiting;             // the address to open once the extension is ready
    private final Runnable openWaiting = new Runnable() {
        @Override
        public void run() {
            String url = waiting;
            waiting = null;
            if (url != null && session != null) open(url);
        }
    };

    public GeckoPage(Context c, Handler main, PageListener listener, RequestFilter filter) {
        this.main = main;
        this.filter = filter;
        GeckoRuntime runtime = GeckoEngine.runtime(c);
        extension = GeckoEngine.extension(runtime);
        session = new GeckoSession(new GeckoSessionSettings.Builder()
                .usePrivateMode(false)                 // the website's sign-in stays
                .useTrackingProtection(false)          // the ad blocker does it, and players work as in a browser
                .suspendMediaWhenInactive(false)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build());
        GeckoEvents events = new GeckoEvents(this, listener);
        session.setProgressDelegate(events);
        session.setContentDelegate(events);
        session.setHistoryDelegate(events);
        session.setPermissionDelegate(events);
        session.setNavigationDelegate(new GeckoNavigation(this, listener));
        session.setPromptDelegate(new GeckoPrompts(c));
        keyboard = new GeckoKeyboard(session.getTextInput().getDelegate());
        session.getTextInput().setDelegate(keyboard);
        session.open(runtime);
        session.setActive(true);
        view = new GeckoView(c);
        view.setBackgroundColor(Color.BLACK);
        view.coverUntilFirstPaint(Color.BLACK);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener((v, e) -> {
            keyboard.touched();
            return false;                          // the page's own touch, as ever
        });
        view.setSession(session);
        extension.attach(this, filter);
    }

    @Override
    public View view() {
        return view;
    }

    @Override
    public boolean alive() {
        return session != null;
    }

    @Override
    public boolean inApp() {
        return true;
    }

    @Override
    public int engineVersion() {
        return GeckoEngine.version();
    }

    /** The engine never says: the app plays and pauses by itself (MediaKeys). */
    @Override
    public boolean reportsUnusedKeys() {
        return false;
    }

    /** In the extension's own part of the page (page.js), which the page's scripts never see. */
    @Override
    public void expose(Bridge bridge, String name) {
        extension.expose(this, bridge, name);
    }

    @Override
    public String url() {
        return location;
    }

    /** Once the extension is ready (the first page waits for the ad blocker), then the blocker's look at it. */
    @Override
    public void load(String url) {
        if (session == null) return;
        waiting = url;
        extension.whenReady(openWaiting);
    }

    private void open(final String url) {
        Extension.WORK.execute(new Runnable() {
            @Override
            public void run() {
                final RequestFilter.Answer instead = filter.check(url, RequestFilter.PAGE, null, url, null);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (session == null) return;
                        if (instead != null) showInstead(instead);
                        else session.load(new GeckoSession.Loader().uri(url).flags(OWN_LOAD));
                    }
                });
            }
        });
    }

    /** What the ad blocker shows in place of an ad page. */
    void showInstead(RequestFilter.Answer a) {
        if (session == null) return;
        session.load(new GeckoSession.Loader().data(a.data, a.mime)
                .flags(OWN_LOAD | GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI));
    }

    @Override
    public void reload() {
        if (session != null) session.reload();
    }

    /** The on-screen keyboard, for the page's field the remote is on (OK on it: assets/gecko/arrows-keys.js). */
    void showKeyboard() {
        if (session == null || view == null) return;
        if (!view.hasFocus()) view.requestFocus();
        keyboard.show(session);
    }

    @Override
    public void run(String script, Result result) {
        if (session != null) extension.run(this, script, result);
    }

    void setHistory(String[] urls, int at) {
        history = urls;
        historyAt = at;
    }

    @Override
    public String historyUrl() {
        return historyAt >= 0 && historyAt < history.length ? history[historyAt] : null;
    }

    @Override
    public int stepsBack() {
        return History.stepsBack(history, historyAt);
    }

    @Override
    public void go(int steps) {
        if (session != null && steps != 0) session.gotoHistoryIndex(historyAt + steps);
    }

    @Override
    public void pause(String script) {
        if (session == null) return;
        run(script, null);
        session.setActive(false);
    }

    @Override
    public void resume() {
        if (session != null) session.setActive(true);
    }

    @Override
    public void destroy() {
        if (session == null) return;
        waiting = null;
        extension.detach(this);
        if (view != null) {
            view.releaseSession();
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        }
        session.close();
        session = null;
        view = null;
    }

    @Override
    public void drop() {
        destroy();
    }
}
