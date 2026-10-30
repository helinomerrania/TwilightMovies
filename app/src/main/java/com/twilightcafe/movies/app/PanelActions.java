package com.twilightcafe.movies.app;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.remote.GuidePanel;
import com.twilightcafe.movies.remote.RemoteKeys;
import com.twilightcafe.movies.sites.AddressPanel;
import com.twilightcafe.movies.sites.Site;
import com.twilightcafe.movies.sites.SitesPanel;
import com.twilightcafe.movies.util.Urls;

/**
 * Opening the app's panels (the menu, the websites, the address, the guide) and what their buttons do; and
 * what the remote's keys ask of them (RemoteKeys.Actions).
 */
final class PanelActions implements RemoteKeys.Actions, SitesPanel.Listener, AddressPanel.Listener,
        GuidePanel.Listener, GuidePanel.Busy {
    private final MoviesApp app;

    PanelActions(MoviesApp app) {
        this.app = app;
    }

    /** Before a panel opens: the others close, the pointer stops. false if there's no page to show it over. */
    private boolean prepare() {
        if (!app.page.alive()) return false;
        app.nav.takeOver();
        app.panels.hideAll();
        return true;
    }

    @Override
    public void showMenu() {
        if (prepare()) app.menu.show();
    }

    /** The menu, with "Install the new version" chosen. */
    void showMenuAtUpdate() {
        if (prepare()) app.menu.showUpdate();
    }

    void showSites() {
        if (prepare()) app.sitesPanel.show();
    }

    void showGuide() {
        if (prepare()) app.guide.show();
    }

    void showAddress() {
        if (prepare()) app.address.show(app.sites.address(), app.sites.current().address);
    }

    /** The other language: every panel again in it, and the page where it was. */
    void language() {
        app.settings.setLanguage(app.screen.texts.other());
        app.panels.close();
        app.screen.activity.recreate();
    }

    /** The guide on a new box, once (GuidePanel.firstTime). */
    void firstGuide() {
        app.settings.setGuideShown(true);
        showGuide();
    }

    // RemoteKeys.Actions

    @Override
    public boolean menuShown() {
        return app.menu.shown();
    }

    @Override
    public void closePanels() {
        app.panels.close();
    }

    @Override
    public void back() {
        app.nav.back();
    }

    @Override
    public boolean panelOpen() {
        return app.panels.anyShown() || app.errors.shown();
    }

    @Override
    public void click(float x, float y) {
        app.blocking.click(x, y);
    }

    // SitesPanel.Listener

    @Override
    public void open(Site site) {
        app.nav.open(site);
    }

    // AddressPanel.Listener

    @Override
    public void open(String typed) {
        String url = Urls.normalize(typed);
        if (url == null) app.osd.show(app.screen.t(R.string.osd_bad_address), 2500);
        else app.nav.setAddress(url);
    }

    @Override
    public void useOwn() {
        app.nav.setAddress(null);
    }

    @Override
    public void cancel() {
        app.panels.close();
    }

    // GuidePanel.Listener and Busy

    @Override
    public void close() {
        app.panels.close();
    }

    @Override
    public void otherLanguage() {
        app.settings.setGuideShown(false);   // the guide again, in the other language
        language();
    }

    @Override
    public boolean busy() {
        return !app.page.alive() || panelOpen() || app.fullscreen.active() || app.splash.shown();
    }
}
