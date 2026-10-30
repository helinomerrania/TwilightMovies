package com.twilightcafe.movies.video;

import android.content.Context;
import android.media.AudioManager;
import android.view.WindowManager;
import android.webkit.ValueCallback;

import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.web.PageScripts;
import com.twilightcafe.movies.web.PageView;

/**
 * Keeps the TV from dimming, sleeping or starting its screensaver while a film plays, in the page or full
 * screen. It looks every 20 seconds (scripts/playing.js); sound counts too, for players the page's script can't
 * see (in a frame of another site). Two quiet looks in a row, and the TV may sleep again.
 */
public final class KeepAwake {
    private static final long LOOK_EVERY_MS = 20000;

    private final Screen screen;
    private final PageView page;
    private final PageScripts scripts;
    private boolean on;
    private int quiet;

    private final Runnable look = new Runnable() {
        @Override
        public void run() {
            if (!page.alive()) return;
            AudioManager am = (AudioManager) screen.activity.getSystemService(Context.AUDIO_SERVICE);
            final boolean sound = am != null && am.isMusicActive();
            page.run(scripts.call(PageScripts.PLAYING), new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String playing) {
                    if (sound || "1".equals(playing)) {
                        quiet = 0;
                        set(true);
                    } else if (++quiet >= 2) {
                        set(false);
                    }
                }
            });
            screen.main.postDelayed(this, LOOK_EVERY_MS);
        }
    };

    public KeepAwake(Screen screen, PageView page, PageScripts scripts) {
        this.screen = screen;
        this.page = page;
        this.scripts = scripts;
    }

    /** The app is on the screen: the first look in 5 seconds. */
    public void start() {
        screen.main.removeCallbacks(look);
        screen.main.postDelayed(look, 5000);
    }

    /** The app left the screen: the TV may sleep. */
    public void stop() {
        screen.main.removeCallbacks(look);
        set(false);
    }

    /** A film went full screen: awake at once. */
    public void filmStarted() {
        quiet = 0;
        set(true);
    }

    public boolean on() {
        return on;
    }

    public void set(boolean awake) {
        if (awake == on) return;
        on = awake;
        if (awake) screen.activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else screen.activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
