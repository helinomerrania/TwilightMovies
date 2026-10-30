package com.twilightcafe.movies.remote;

import android.os.Handler;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.twilightcafe.movies.pointer.Pointer;
import com.twilightcafe.movies.video.MediaKeys;
import com.twilightcafe.movies.web.PageTarget;

/**
 * The remote, key by key. The arrows and OK go to the website (it has its own TV interface), or move and click
 * the mouse pointer when it's on. BACK goes back. MENU, or holding OK or BACK, opens the app's menu. The media
 * keys play, pause and seek (MediaKeys); page and channel keys scroll. While a panel is open, keys are its own.
 */
public final class RemoteKeys {
    /** What the keys do in the rest of the app. */
    public interface Actions {
        boolean menuShown();

        void showMenu();

        void closePanels();

        /** BACK: a panel closes, a film leaves full screen, the page goes back, or (twice) the app ends. */
        void back();

        boolean panelOpen();

        /** The panel shown keeps the remote: the focus back in it, if the page took it. */
        void panelFocus();

        /** OK with the pointer: a click at x, y. */
        void click(float x, float y);
    }

    private static final long OK_LONG_MS = 650;
    private static final long BACK_LONG_MS = 800;
    private static final int TO_PAGE = 1;
    private static final int TO_POINTER = 2;

    private final Handler main;
    private final PageTarget target;
    private final Pointer pointer;
    private final MediaKeys media;
    private final Actions actions;
    private final SparseIntArray routes = new SparseIntArray();   // where each arrow held down goes
    private KeyEvent okDown;
    private KeyEvent mediaDown;
    private boolean okLongFired;
    private boolean backTracking;
    private boolean backLongFired;

    private final Runnable okLong = new Runnable() {
        @Override
        public void run() {
            okDown = null;
            okLongFired = true;
            actions.showMenu();
        }
    };
    private final Runnable backLong = new Runnable() {
        @Override
        public void run() {
            backLongFired = true;
            if (!actions.menuShown()) actions.showMenu();
        }
    };

    public RemoteKeys(Handler main, PageTarget target, Pointer pointer, MediaKeys media, Actions actions) {
        this.main = main;
        this.target = target;
        this.pointer = pointer;
        this.media = media;
        this.actions = actions;
    }

    /** true if the key was used here; false: Android's own handling (focus moves in a panel). */
    public boolean handle(KeyEvent e) {
        int code = e.getKeyCode();
        int action = e.getAction();
        if (!target.alive() || (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)) return false;
        if (code == KeyEvent.KEYCODE_MENU) {
            if (action == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (actions.menuShown()) actions.closePanels();
                else actions.showMenu();
            }
            return true;
        }
        if (Keys.back(code)) return back(e);
        if (Keys.ok(code) && action == KeyEvent.ACTION_UP && okLongFired) {
            okLongFired = false;   // the release after a long press (the menu is already open)
            return true;
        }
        if (actions.panelOpen()) {
            actions.panelFocus();
            return false;
        }
        if (Keys.ok(code)) return ok(e);
        if (Keys.arrow(code)) return arrow(e);
        if (Keys.media(code)) return media(e);
        if (Keys.page(code)) {
            if (pointer.enabled() && !target.fullscreen()) {
                if (action == KeyEvent.ACTION_DOWN && e.getRepeatCount() % 4 == 0) pointer.scrollHere(Keys.up(code) ? -0.8f : 0.8f);
                return true;
            }
            target.send(target.reportsUnusedKeys() ? e : Keys.asPage(e));   // Firefox's engine scrolls by page keys
            return true;
        }
        return false;
    }

    /** Forgets keys half pressed (a system screen took over, or the app's own did). */
    public void forget() {
        main.removeCallbacks(okLong);
        main.removeCallbacks(backLong);
        routes.clear();
        okDown = null;
        mediaDown = null;
        backTracking = false;
    }

    private boolean ok(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okDown = new KeyEvent(e);
                okLongFired = false;
                main.removeCallbacks(okLong);
                main.postDelayed(okLong, OK_LONG_MS);
            }
            return true;
        }
        main.removeCallbacks(okLong);
        KeyEvent down = okDown;
        okDown = null;
        if (down == null || e.isCanceled()) return true;
        boolean full = target.fullscreen();
        if (pointer.enabled() && (!full || pointer.visible())) {
            if (pointer.visible()) actions.click(pointer.x(), pointer.y());
            pointer.show();
        } else if (full) {
            media.press(down, new KeyEvent(e), "t", 0, 1);   // the player first; if it ignores OK, play/pause
        } else {
            target.send(down);
            target.send(e);
        }
        return true;
    }

    private boolean back(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                backTracking = true;
                backLongFired = false;
                main.removeCallbacks(backLong);
                main.postDelayed(backLong, BACK_LONG_MS);
            }
            return true;
        }
        main.removeCallbacks(backLong);
        boolean tracked = backTracking;
        backTracking = false;
        if (backLongFired) {
            backLongFired = false;
            return true;
        }
        if (tracked && !e.isCanceled()) actions.back();
        return true;
    }

    private boolean arrow(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            boolean full = target.fullscreen();
            boolean steer = pointer.enabled() && (!full || pointer.visible()
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN);
            routes.put(code, steer ? TO_POINTER : TO_PAGE);
        }
        int route = routes.get(code, 0);
        if (e.getAction() == KeyEvent.ACTION_UP) routes.delete(code);
        if (route == TO_POINTER) {
            if (e.getAction() == KeyEvent.ACTION_UP) pointer.end(code);
            else if (e.getRepeatCount() == 0) pointer.start(code);
        } else if (route == TO_PAGE) {
            target.send(e);
        }
        return true;
    }

    private boolean media(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) mediaDown = new KeyEvent(e);
            return true;
        }
        KeyEvent down = mediaDown;
        mediaDown = null;
        if (down == null || down.getKeyCode() != code || e.isCanceled()) return true;
        KeyEvent up = new KeyEvent(e);
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) media.press(down, up, "t", 0, 0);
        else if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) media.press(down, up, "s", 10, 0);
        else if (code == KeyEvent.KEYCODE_MEDIA_REWIND) media.press(down, up, "s", -10, 0);
        else media.other(down, up);   // play, pause, stop, next, previous
        return true;
    }
}
