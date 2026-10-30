package com.twilightcafe.movies.ui;

import android.view.View;
import android.widget.ImageView;

import com.twilightcafe.movies.R;

/** Twilight's picture while the first page loads: it fades once the page shows, or after 8 seconds anyway. */
public final class Splash {
    private final Screen screen;
    private final ImageView view;
    private boolean fading;
    private final Runnable fade = new Runnable() {
        @Override
        public void run() {
            if (!Views.shown(view) || fading) return;
            fading = true;
            view.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                }
            });
        }
    };

    public Splash(Screen s) {
        screen = s;
        view = new ImageView(s.activity);
        view.setImageResource(R.drawable.splash_art);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        s.add(view);
        s.main.postDelayed(fade, 8000);
    }

    public boolean shown() {
        return Views.shown(view);
    }

    /** The page is showing: the picture fades. */
    public void fade() {
        screen.main.post(fade);
    }

    /** Gone at once (for the error screen). */
    public void hide() {
        view.setVisibility(View.GONE);
    }
}
