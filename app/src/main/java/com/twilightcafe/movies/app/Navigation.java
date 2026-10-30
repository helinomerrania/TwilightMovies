package com.twilightcafe.movies.app;

import android.os.SystemClock;
import android.webkit.WebView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.loading.ErrorScreen;
import com.twilightcafe.movies.sites.Site;
import com.twilightcafe.movies.util.Urls;
import com.twilightcafe.movies.video.Fullscreen;

/**
 * Where the page goes: a website's address, home, the same page again, back (past redirects; BACK twice on
 * the first page ends the app); and what happens around it: the error screen (its Host), a film full screen
 * (its Listener), and the page's engine crashing.
 */
final class Navigation implements ErrorScreen.Host, Fullscreen.Listener {
    private static long lastCrashAt;   // kept while the app starts again after a crash

    private final MoviesApp app;
    private long lastBackAt;
    private String resumeUrl;

    Navigation(MoviesApp app) {
        this.app = app;
    }

    void load(String url) {
        app.errors.hide();
        app.page.load(url);
    }

    void home() {
        app.panels.close();
        app.fullscreen.leave();
        load(app.sites.address());
    }

    void reload() {
        app.panels.close();
        app.fullscreen.leave();
        if (!app.page.alive()) return;
        if (app.errors.shown()) app.errors.tryAgain();
        else app.page.reload();
    }

    /** Another of the app's websites, chosen in the Websites panel: its own pointer, and its start. */
    void open(Site site) {
        app.sites.choose(site);
        app.pointer.setEnabled(app.sites.pointer());
        go(app.sites.address());
    }

    /** An address typed for the website shown (null: its own again). */
    void setAddress(String url) {
        if (url == null) app.sites.useOwnAddress();
        else app.sites.setAddress(url);
        app.blocker.sites.setOwn(app.sites.ownHosts());
        go(app.sites.address());
    }

    private void go(String url) {
        app.panels.close();
        app.fullscreen.leave();
        load(url);
        app.osd.show(app.screen.t(R.string.osd_opening, Urls.shortUrl(url)), 2000);
    }

    /** BACK: a panel closes, a film leaves full screen, the page goes back; on the first page, twice ends the app. */
    void back() {
        if (app.panels.anyShown()) {
            app.panels.close();
            return;
        }
        if (app.fullscreen.active()) {
            app.fullscreen.leave();
            return;
        }
        if (app.page.alive() && backInHistory()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAt < 2500) {
            app.screen.activity.finish();
            return;
        }
        lastBackAt = now;
        app.osd.show(app.screen.t(R.string.osd_back_again), 2500);
    }

    private boolean backInHistory() {
        String here = app.page.historyUrl();
        if (here == null) return false;
        String failed = app.errors.failedUrl();
        if (app.errors.shown() && failed != null && !failed.equals(here)) {
            app.errors.hide();   // the page that failed never entered the history: the one before shows again
            return true;
        }
        int steps = app.page.stepsBack();
        if (steps == 0) return false;
        app.errors.hide();
        app.page.go(steps);
        return true;
    }

    /** Something takes the screen: the pointer stops and hides, and keys half pressed are forgotten. */
    void takeOver() {
        app.pointer.stop();
        app.pointer.hide();
        app.keys.forget();
    }

    /** The page's engine crashed (often low memory on TV boxes): the app starts again, at the same page. */
    void crashed(WebView view) {
        if (!app.page.is(view)) return;
        long now = SystemClock.uptimeMillis();
        boolean again = lastCrashAt != 0 && now - lastCrashAt < 20000;   // twice in a row: at the website's start
        lastCrashAt = now;
        resumeUrl = again ? app.sites.address() : view.getUrl();
        app.fullscreen.drop();
        app.awake.set(false);
        app.page.drop();
        app.screen.main.post(new Runnable() {
            @Override
            public void run() {
                if (!app.screen.closing()) app.screen.activity.recreate();
            }
        });
    }

    /** Where the page was when its engine crashed (for the app's next start). */
    String resumeUrl() {
        return resumeUrl;
    }

    // ErrorScreen.Host

    @Override
    public String address() {
        return app.sites.address();
    }

    @Override
    public boolean typing() {
        return app.address.typing();
    }

    @Override
    public boolean panelOpen() {
        return app.panels.anyShown();
    }

    @Override
    public void showing() {
        app.splash.hide();
        app.loading.hide();
        app.fullscreen.leave();
        takeOver();
    }

    /** The page gets the remote (or the error screen, when it's up). */
    @Override
    public void focusPage() {
        if (app.errors.shown()) app.errors.focus();
        else app.target.focus();
    }

    @Override
    public void changeAddress() {
        app.panelActions.showAddress();
    }

    // Fullscreen.Listener

    @Override
    public void entered() {
        takeOver();
        app.awake.filmStarted();
    }

    @Override
    public void left() {
        takeOver();
        if (!app.panels.anyShown() && !app.errors.shown()) focusPage();
    }
}
