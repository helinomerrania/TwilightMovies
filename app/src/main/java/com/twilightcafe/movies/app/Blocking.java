package com.twilightcafe.movies.app;

import android.os.SystemClock;
import android.webkit.ValueCallback;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.adblock.SitePolicy;
import com.twilightcafe.movies.util.Urls;
import com.twilightcafe.movies.web.PageScripts;
import com.twilightcafe.movies.web.Popups;

/**
 * The ad blocker's part in the app: stopping jumps to other sites (and saying so), hiding the ad boxes of each
 * page, clearing see-through layers before the pointer clicks, its switch and "allow" in the menu, and which
 * pop-ups may open (Popups.Rules).
 */
final class Blocking implements Popups.Rules {
    private final MoviesApp app;
    private String lastJump;      // the last jump to another site that was stopped (the menu can allow it)
    private long lastNoteAt;
    private boolean hidingAsked;  // for the page now loading

    Blocking(MoviesApp app) {
        this.app = app;
    }

    private String t(int id, Object... args) {
        return app.screen.t(id, args);
    }

    String label() {
        return app.blocker.activeHere() ? t(R.string.menu_adblock_on, app.blocker.blockedOnPage()) : t(R.string.menu_adblock_off);
    }

    /** "Allow site.com", after a jump there was stopped (null: none). */
    String allowLabel() {
        return lastJump != null ? t(R.string.menu_allow_jump, app.blocker.sites.site(Urls.hostOf(lastJump))) : null;
    }

    /** On or off for the website shown (kept for it), then the page again. */
    void toggle() {
        String host = app.blocker.pageHost();
        if (host.isEmpty()) host = Urls.hostOf(app.sites.address());
        boolean on = !app.blocker.sites.activeFor(host);
        app.blocker.sites.setActive(host, on);
        app.panels.close();
        app.osd.show(t(on ? R.string.osd_adblock_on : R.string.osd_adblock_off, app.blocker.sites.site(host)), 3000);
        app.page.reload();
    }

    /** The page goes where it was stopped last, and to that site from then on. */
    void allowLastJump() {
        String url = lastJump;
        lastJump = null;
        app.panels.close();
        if (url == null || !app.page.alive()) return;
        String host = Urls.hostOf(url);
        app.blocker.sites.allow(host);
        app.osd.show(t(R.string.osd_jump_allowed, app.blocker.sites.site(host)), 3000);
        app.page.load(url);
    }

    /** Whether the page may go to url (a link, a script's jump, a redirect); said on the screen if not. */
    boolean mayGo(String url) {
        int verdict = app.blocker.checkJump(url);
        if (verdict == SitePolicy.GO) return true;
        if (verdict == SitePolicy.JUMP) lastJump = url;
        long now = SystemClock.uptimeMillis();
        if (now - lastNoteAt > 3000) {   // ad scripts try again and again: said once
            lastNoteAt = now;
            String site = app.blocker.sites.site(Urls.hostOf(url));
            app.osd.show(t(verdict == SitePolicy.AD ? R.string.osd_ad_page_blocked : R.string.osd_jump_blocked, site), 5000);
        }
        return false;
    }

    void pageStarted() {
        hidingAsked = false;
    }

    /** Early (at 30%), so the ad boxes don't flash; and again when the page shows and when it's done. */
    void progress(int percent) {
        if (percent < 30 || hidingAsked) return;
        hidingAsked = true;
        hideAds();
    }

    /** The ad-hiding script, into the page shown (it works once a page, however often it's put in). */
    void hideAds() {
        if (!app.page.alive() || app.blocker.filters() == null || !app.blocker.activeHere()) return;
        app.page.run(app.scripts.call(PageScripts.HIDE_ADS, app.blocker.bridgeName));
    }

    /** A click with the pointer at x, y, after taking away see-through ad layers over the page there. */
    void click(final float x, final float y) {
        if (!app.page.alive() || app.fullscreen.active() || !app.blocker.activeHere()) {
            app.pointer.tap(x, y);
            return;
        }
        final boolean[] done = {false};
        final Runnable tap = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                app.pointer.tap(x, y);
            }
        };
        app.screen.main.postDelayed(tap, 250);   // if the page doesn't answer at once, the click anyway
        float w = Math.max(1, app.screen.root.getWidth());
        float h = Math.max(1, app.screen.root.getHeight());
        app.page.run(app.scripts.call(PageScripts.AD_LAYERS, x / w, y / h), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String removed) {
                app.screen.main.removeCallbacks(tap);
                tap.run();
            }
        });
    }

    // Popups.Rules: a pop-up the viewer clicked opens if it stays on the website, or goes to a site the app allows

    @Override
    public boolean allowed(String url) {
        return app.page.alive() && Urls.isWebScheme(url) && (app.sites.shows(url) || app.blocker.sites.ownOrAllowed(url));
    }

    @Override
    public void open(String url) {
        app.nav.load(url);
    }

    @Override
    public void refused() {
        app.osd.show(t(R.string.osd_popup_blocked), 1500);
    }
}
