package com.twilightcafe.movies.app;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.menu.MenuPanel;
import com.twilightcafe.movies.util.Urls;

/** What the menu's buttons do, and its labels that change (MenuPanel asks each time it opens). */
final class MenuActions implements MenuPanel.Actions, MenuPanel.Status {
    private final MoviesApp app;

    MenuActions(MoviesApp app) {
        this.app = app;
    }

    private String t(int id, Object... args) {
        return app.screen.t(id, args);
    }

    // MenuPanel.Actions

    @Override
    public void home() {
        app.nav.home();
    }

    @Override
    public void sites() {
        app.panelActions.showSites();
    }

    @Override
    public void reload() {
        app.nav.reload();
    }

    /** The mouse pointer on or off, for the website shown. */
    @Override
    public void togglePointer() {
        boolean on = !app.pointer.enabled();
        app.sites.setPointer(on);
        app.pointer.setEnabled(on);
        app.panels.close();
        if (on) app.pointer.show();
        app.osd.show(t(on ? R.string.osd_pointer_on : R.string.osd_pointer_off), 3500);
    }

    @Override
    public void toggleAdBlocker() {
        app.blocking.toggle();
    }

    @Override
    public void allowJump() {
        app.blocking.allowLastJump();
    }

    @Override
    public void guide() {
        app.panelActions.showGuide();
    }

    @Override
    public void language() {
        app.panelActions.language();
    }

    @Override
    public void address() {
        app.panelActions.showAddress();
    }

    @Override
    public void update() {
        app.updates.button();
    }

    @Override
    public void exit() {
        app.screen.activity.finish();
    }

    // MenuPanel.Status

    /** The page's address, the app's build, and the box's web engine. */
    @Override
    public String subtitle() {
        String url = app.page.url();
        String sub = Urls.shortUrl(url != null ? url : app.sites.address()) + "  ·  " + t(R.string.menu_build, app.updater.myBuild());
        return app.engine > 0 ? sub + "  ·  " + t(R.string.menu_engine, app.engine) : sub;
    }

    /** The ad blocker's rules: how many, and from when. */
    @Override
    public String filters() {
        FilterSet f = app.blocker.filters();
        return f != null ? t(R.string.menu_filters, app.screen.texts.count(f.rules), app.screen.texts.date(f.created)) : null;
    }

    @Override
    public String sitesLabel() {
        if (app.sites.all().size() < 2) return null;
        return t(R.string.menu_sites, app.sites.current().name(app.screen.texts.kurdish()));
    }

    @Override
    public String pointerLabel() {
        return t(app.pointer.enabled() ? R.string.menu_pointer_on : R.string.menu_pointer_off);
    }

    @Override
    public String adBlockerLabel() {
        return app.blocking.label();
    }

    @Override
    public String allowLabel() {
        return app.blocking.allowLabel();
    }

    @Override
    public String updateLabel() {
        return app.updates.label();
    }
}
