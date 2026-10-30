package com.twilightcafe.movies.app;

import android.app.Activity;
import android.os.Bundle;

import com.twilightcafe.movies.BuildConfig;
import com.twilightcafe.movies.R;
import com.twilightcafe.movies.adblock.AdBlocker;
import com.twilightcafe.movies.loading.ErrorScreen;
import com.twilightcafe.movies.menu.MenuPanel;
import com.twilightcafe.movies.pointer.Pointer;
import com.twilightcafe.movies.remote.GuidePanel;
import com.twilightcafe.movies.remote.RemoteKeys;
import com.twilightcafe.movies.settings.Settings;
import com.twilightcafe.movies.sites.AddressPanel;
import com.twilightcafe.movies.sites.SiteCatalog;
import com.twilightcafe.movies.sites.Sites;
import com.twilightcafe.movies.sites.SitesPanel;
import com.twilightcafe.movies.text.Texts;
import com.twilightcafe.movies.ui.LoadingBar;
import com.twilightcafe.movies.ui.Osd;
import com.twilightcafe.movies.ui.Panels;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.ui.Splash;
import com.twilightcafe.movies.update.Updater;
import com.twilightcafe.movies.video.Fullscreen;
import com.twilightcafe.movies.video.KeepAwake;
import com.twilightcafe.movies.video.MediaKeys;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.PageScripts;
import com.twilightcafe.movies.web.PageTarget;

/**
 * Makes every part of the app once and wires them together: the one class that knows them all. Each part gets
 * what it needs when it's made; the few that bring many parts together (Navigation, PanelActions, MenuActions,
 * Blocking, Updates, PageEvents) get the app itself. The web engine is the box's own or Firefox's (Engines).
 * MainActivity hands it Android's events. ARCHITECTURE.md has the picture.
 */
public final class MoviesApp {
    private static final String STATE_URL = "resume_url";

    final Settings settings;
    final Screen screen;
    final Sites sites;
    final Updater updater;
    final AdBlocker blocker;
    final PageScripts scripts;
    final boolean engineChoice;   // whether Firefox's engine can run here, so the menu offers both

    final Navigation nav = new Navigation(this);
    final PanelActions panelActions = new PanelActions(this);
    final MenuActions menuActions = new MenuActions(this);
    final Blocking blocking = new Blocking(this);
    final Updates updates = new Updates(this);

    // On the screen, from the bottom up
    final Page page;
    final PageTarget target;
    final Fullscreen fullscreen;
    final Splash splash;
    final LoadingBar loading;
    final ErrorScreen errors;
    final Panels panels = new Panels(nav::focusPage);
    final MenuPanel menu;
    final SitesPanel sitesPanel;
    final AddressPanel address;
    final GuidePanel guide;
    final Osd osd;
    final Pointer pointer;

    final KeepAwake awake;
    final MediaKeys media;
    public final RemoteKeys keys;

    public MoviesApp(Activity a, Bundle state) {
        settings = new Settings(a);
        screen = new Screen(a, new Texts(a, settings.language()));
        sites = new Sites(SiteCatalog.read(a.getAssets()), settings);
        updater = new Updater(a, BuildConfig.REPO, BuildConfig.VERSION_CODE);
        blocker = new AdBlocker(a, BuildConfig.REPO);
        blocker.sites.setOwn(sites.ownHosts());
        blocker.setTexts(screen.t(R.string.page_blocked_title), screen.t(R.string.page_blocked_text), screen.texts.kurdish());
        blocker.start();   // the rules load while the start picture shows; the first page waits for them
        scripts = new PageScripts(a.getAssets());
        engineChoice = Engines.choice(a);

        BlockerLink link = new BlockerLink(blocker);
        page = Engines.make(a, screen.main, settings, new PageEvents(this), link);
        page.expose(link, blocker.bridgeName);
        screen.add(page.view());
        target = new PageTarget(page);
        fullscreen = new Fullscreen(screen, target, nav);
        screen.add(fullscreen.view());
        splash = new Splash(screen);
        loading = new LoadingBar(screen);
        errors = new ErrorScreen(screen, page, nav);
        screen.add(errors.view());
        menu = panels.add(new MenuPanel(screen, menuActions, menuActions, updater.enabled()));
        sitesPanel = panels.add(new SitesPanel(screen, sites, panelActions));
        address = panels.add(new AddressPanel(screen, panelActions));
        guide = panels.add(new GuidePanel(screen, panelActions));
        screen.add(menu.view());
        screen.add(sitesPanel.view());
        screen.add(address.view());
        screen.add(guide.view());
        osd = new Osd(screen);
        pointer = new Pointer(screen, target, page, scripts);
        screen.add(pointer.view());

        awake = new KeepAwake(screen, page, scripts);
        media = new MediaKeys(screen, page, scripts, target, osd);
        keys = new RemoteKeys(screen.main, target, pointer, media, panelActions);
        pointer.setEnabled(sites.pointer());

        String resume = state != null ? state.getString(STATE_URL) : null;
        nav.load(resume != null ? resume : sites.address());
        target.focus();
        if (!settings.guideShown()) guide.firstTime(panelActions, panelActions::firstGuide);
    }

    public void onResume() {
        page.resume();
        awake.start();
        updates.start();
    }

    public void onPause() {
        pointer.stop();
        keys.forget();
        awake.stop();
        updates.stop();
        page.pause(scripts.call(PageScripts.PAUSE_ALL));
    }

    /** Only the address: a whole page's state can be too big for Android to keep. */
    public void save(Bundle out) {
        String url = page.alive() ? page.url() : nav.resumeUrl();
        if (url != null) out.putString(STATE_URL, url);
    }

    public void onDestroy() {
        pointer.stop();
        screen.main.removeCallbacksAndMessages(null);
        errors.stop();
        page.destroy();
    }

    /** A system screen took over: half-finished key presses are forgotten. */
    public void onFocus(boolean hasFocus) {
        if (hasFocus) return;
        pointer.stop();
        keys.forget();
    }
}
