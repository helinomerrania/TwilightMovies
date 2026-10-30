package com.twilightcafe.movies.video;

import android.os.SystemClock;
import android.view.KeyEvent;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Osd;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.PageScripts;
import com.twilightcafe.movies.web.PageTarget;

/**
 * The remote's play/pause, fast-forward and rewind (and OK on a film full screen). The page gets each key
 * first; only if it doesn't use the key, and the video didn't change, does the app play, pause or seek itself
 * (scripts/media.js), so a player that already handles the remote never gets a double action. Android's WebView
 * says which keys the page didn't use; Firefox's engine doesn't, and would hand an unused media key on to
 * Android (which can start another app's music), so there the app does media keys itself, and OK after a moment.
 */
public final class MediaKeys {
    private static final long PAGE_FIRST_MS = 400;   // Firefox's engine: how long the page has to use OK itself

    private final Screen screen;
    private final Page page;
    private final PageScripts scripts;
    private final PageTarget target;
    private final Osd osd;
    // what to do if the page reports the key it was just given as unused
    private String pending;
    private int pendingSecs;
    private int pendingKey;
    private long pendingAt;

    public MediaKeys(Screen screen, Page page, PageScripts scripts, PageTarget target, Osd osd) {
        this.screen = screen;
        this.page = page;
        this.scripts = scripts;
        this.target = target;
        this.osd = osd;
    }

    /**
     * Gives the page the key (down, up) after noting the video ('q'); action ('t' play/pause, 's' seek secs) is
     * done by the app if the page then reports the key unused. strict: OK, which a focused button gets instead.
     */
    public void press(final KeyEvent down, final KeyEvent up, final String action, final int secs, int strict) {
        if (!page.alive()) return;
        final boolean told = page.reportsUnusedKeys();
        final boolean toPage = told || strict == 1;
        final boolean[] sent = {false};
        final Runnable plain = () -> {
            if (sent[0]) return;
            sent[0] = true;
            pending = null;
            if (toPage) send(down, up);
        };
        screen.main.postDelayed(plain, 400);   // the page didn't answer: the key alone
        page.run(scripts.call(PageScripts.MEDIA, "q", 0, strict), noted -> {
            if (sent[0]) return;
            sent[0] = true;
            screen.main.removeCallbacks(plain);
            boolean ok = noted != null && noted.contains("ok");
            pending = ok && told ? action : null;
            pendingSecs = secs;
            pendingKey = down.getKeyCode();
            pendingAt = SystemClock.uptimeMillis();
            if (toPage) send(down, up);
            if (ok && !told) screen.main.postDelayed(() -> act(action, secs), toPage ? PAGE_FIRST_MS : 0);
        });
    }

    /** Play, pause, stop, next, previous: the page first, if the engine says whether it used them; else the app. */
    public void other(KeyEvent down, KeyEvent up) {
        if (page.reportsUnusedKeys()) {
            send(down, up);   // notUsed does the rest
            return;
        }
        int c = down.getKeyCode();
        if (c == KeyEvent.KEYCODE_MEDIA_PLAY) act("p", 0);
        else if (c == KeyEvent.KEYCODE_MEDIA_PAUSE || c == KeyEvent.KEYCODE_MEDIA_STOP) act("z", 0);
    }

    private void send(KeyEvent down, KeyEvent up) {
        target.send(down);
        target.send(up);
    }

    /** A key (going down) the page didn't use: true if it's one this does (then it's done). */
    public boolean notUsed(KeyEvent e) {
        int code = e.getKeyCode();
        if (pending != null && code == pendingKey && SystemClock.uptimeMillis() - pendingAt < 2000) {
            final String action = pending;
            final int secs = pendingSecs;
            pending = null;
            screen.main.postDelayed(() -> act(action, secs), 300);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                act("p", 0);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                act("z", 0);
                return true;
            default:
                return false;
        }
    }

    /** The app plays, pauses or seeks the video itself, and says so on the screen. */
    private void act(final String action, final int secs) {
        if (!page.alive()) return;
        page.run(scripts.call(PageScripts.MEDIA, action, secs, 0), r -> {
            if (r == null) return;
            if (r.contains("seeked")) {
                osd.show(secs > 0 ? screen.t(R.string.osd_forward, secs) : screen.t(R.string.osd_rewind, -secs), 900);
            } else if (r.contains("paused") && !"p".equals(action)) {
                osd.show(screen.t(R.string.osd_paused), 900);
            } else if (r.contains("playing") && !"z".equals(action)) {
                osd.show(screen.t(R.string.osd_playing), 900);
            }
        });
    }
}
