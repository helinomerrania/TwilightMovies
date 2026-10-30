package com.twilightcafe.movies.app;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.adblock.AdBlocker;
import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.update.Updater;

/**
 * New versions of the app (Updater) and of the ad blocker's filters (AdBlocker): looked for by themselves now
 * and then, or at once from the menu. A new version found by itself is said once, and never over a film; it's
 * installed only from the menu.
 */
final class Updates {
    private static final long FIRST_MS = 20000;                 // the first look, after the start
    private static final long EVERY_MS = 6L * 3600 * 1000;      // then this often while the app is open
    private static final long MIN_GAP_MS = 3L * 3600 * 1000;    // and never more often than this

    private final MoviesApp app;
    private boolean announced;
    private boolean awaitingPermission;

    private final Runnable look = new Runnable() {
        @Override
        public void run() {
            app.blocker.checkForUpdate(false, null);   // the filters: looked at twice a day, downloaded every day and a half
            Updater u = app.updater;
            if (!u.enabled()) return;
            long since = System.currentTimeMillis() - u.lastCheck();
            if (since < 0 || since >= MIN_GAP_MS) {
                u.checkInBackground(app.screen.main, new Updater.Listener() {
                    @Override
                    public void onUpdateChecked(int result) {
                        checked(result, false);
                    }
                });
            } else if (u.readyBuild() > u.myBuild()) {
                checked(Updater.READY, false);
            }
            app.screen.main.postDelayed(this, EVERY_MS);
        }
    };

    Updates(MoviesApp app) {
        this.app = app;
    }

    /** The app is on the screen: the first look soon; and back from Android's "allow installing", the install. */
    void start() {
        app.screen.main.removeCallbacks(look);
        app.screen.main.postDelayed(look, FIRST_MS);
        if (!awaitingPermission) return;
        awaitingPermission = false;
        if (app.updater.readyBuild() <= app.updater.myBuild() || !app.updater.canInstall(app.screen.activity)) return;
        app.screen.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                install();
            }
        }, 700);
    }

    void stop() {
        app.screen.main.removeCallbacks(look);
    }

    /** The menu's button: "Look for a new version", or "Install build N" once one is waiting. */
    String label() {
        int ready = app.updater.readyBuild();
        return ready > app.updater.myBuild() ? app.screen.t(R.string.menu_install_update, ready) : app.screen.t(R.string.menu_check_update);
    }

    void button() {
        if (app.updater.readyBuild() > app.updater.myBuild()) {
            install();
            return;
        }
        app.panels.close();
        app.osd.show(app.screen.t(R.string.osd_checking), 20000);
        app.updater.checkInBackground(app.screen.main, new Updater.Listener() {
            @Override
            public void onUpdateChecked(int result) {
                checked(result, true);
            }
        });
        app.blocker.checkForUpdate(true, new AdBlocker.Done() {   // and the filters, however old they are
            @Override
            public void updated(boolean newer) {
                if (!newer) return;
                app.screen.main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        FilterSet f = app.blocker.filters();
                        if (f == null || app.screen.closing()) return;
                        app.osd.show(app.screen.t(R.string.osd_filters_updated, app.screen.texts.count(f.rules)), 4000);
                    }
                }, 4500);   // after the app's own answer
            }
        });
    }

    private void checked(int result, boolean asked) {
        app.menu.refreshUpdate();
        if (app.screen.closing() || !app.page.alive()) return;
        boolean ready = result == Updater.READY && app.updater.readyBuild() > app.updater.myBuild();
        if (asked) {
            app.osd.hide();
            if (ready) app.panelActions.showMenuAtUpdate();   // asked, and there is one: the menu, with Install chosen
            else app.osd.show(result == Updater.UP_TO_DATE ? app.screen.t(R.string.osd_up_to_date, app.updater.myBuild())
                    : app.screen.t(R.string.osd_check_failed), 4000);
        } else if (ready && !announced && !app.awake.on() && !app.fullscreen.active()) {
            announced = true;   // found by itself: said once, and never over a film (the menu shows it anyway)
            app.osd.show(app.screen.t(R.string.osd_update_ready), 7000);
        }
    }

    private void install() {
        app.panels.close();
        int r = app.updater.install(app.screen.activity);
        app.menu.refreshUpdate();
        switch (r) {
            case Updater.STARTED:
                app.osd.show(app.screen.t(R.string.osd_update_started), 6000);
                break;
            case Updater.NEED_PERMISSION:
                awaitingPermission = true;
                app.osd.show(app.screen.t(R.string.osd_update_permission), 10000);
                break;
            case Updater.UNSUPPORTED:
                app.osd.show(app.screen.t(R.string.osd_update_unsupported), 8000);
                break;
            case Updater.NOTHING_WAITING:
                button();   // the download is gone: look again
                break;
            default:
                app.osd.show(app.screen.t(R.string.osd_update_failed), 5000);
                break;
        }
    }
}
