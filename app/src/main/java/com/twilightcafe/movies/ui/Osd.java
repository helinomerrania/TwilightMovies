package com.twilightcafe.movies.ui;

import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** A short message at the top of the screen ("Paused", "Press BACK again to leave"), gone after a while. */
public final class Osd {
    private final TextView view;
    private final Handler main;
    private final Runnable fade = new Runnable() {
        @Override
        public void run() {
            view.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                }
            });
        }
    };

    public Osd(Screen s) {
        main = s.main;
        view = s.views.label("", 18, Palette.TEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setMaxWidth(s.dp(760));
        view.setPadding(s.dp(22), s.dp(10), s.dp(22), s.dp(10));
        view.setBackground(Views.rounded(Palette.OSD, s.dp(24)));
        view.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = s.dp(30);
        s.add(view, lp);
    }

    public void show(String text, long millis) {
        view.setText(text);
        view.animate().cancel();
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        main.removeCallbacks(fade);
        main.postDelayed(fade, millis);
    }

    /** Gone at once (another message or a panel is coming). */
    public void hide() {
        main.removeCallbacks(fade);
        view.setVisibility(View.GONE);
    }
}
