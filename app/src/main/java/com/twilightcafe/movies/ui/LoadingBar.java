package com.twilightcafe.movies.ui;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/** The thin sunset line at the top of the screen while a page loads. */
public final class LoadingBar {
    private final ProgressBar bar;

    public LoadingBar(Screen s) {
        bar = new ProgressBar(s.activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(false);
        bar.setProgressTintList(ColorStateList.valueOf(Palette.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        s.add(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.dp(4), Gravity.TOP));
    }

    /** How far the page is (0 to 100); at 100 the line goes. */
    public void set(int percent) {
        bar.setProgress(percent);
        bar.setVisibility(percent < 100 ? View.VISIBLE : View.GONE);
    }

    public void show() {
        bar.setVisibility(View.VISIBLE);
    }

    public void hide() {
        bar.setVisibility(View.GONE);
    }
}
