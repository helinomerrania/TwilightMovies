package com.twilightcafe.movies;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

import com.twilightcafe.movies.app.MoviesApp;

/**
 * Twilight Movies: TV websites (KurdFilm's first; the list is assets/sites.json) full screen, driven by the TV
 * remote, with an ad blocker between them and the internet.
 *
 * This class is only Android's screen for the app: it makes the app's parts (MoviesApp) and passes Android's
 * events on to them (start, stop, the remote's keys). How the parts fit together: ARCHITECTURE.md.
 */
public class MainActivity extends Activity {
    private MoviesApp app;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        app = new MoviesApp(this, state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        app.onResume();
    }

    @Override
    protected void onPause() {
        app.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        app.save(out);
    }

    @Override
    protected void onDestroy() {
        app.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        app.onFocus(hasFocus);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        return app.keys.handle(e) || super.dispatchKeyEvent(e);
    }
}
