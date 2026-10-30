package com.twilightcafe.movies.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.twilightcafe.movies.text.Texts;

/**
 * What every part that puts something on the TV shares: the app's screen (the activity) and its main thread,
 * the view everything is put in, one over the other in the order added, the texts, and the app's views.
 */
public final class Screen {
    public final Activity activity;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final FrameLayout root;
    public final Texts texts;
    public final Views views;

    public Screen(Activity activity, Texts texts) {
        this.activity = activity;
        this.texts = texts;
        this.views = new Views(activity, texts.kurdish());
        this.root = new FrameLayout(activity);
        root.setBackgroundColor(Palette.BLACK);
        activity.setContentView(root);
    }

    public String t(int id) {
        return texts.t(id);
    }

    public String t(int id, Object... args) {
        return texts.t(id, args);
    }

    public int dp(float v) {
        return views.dp(v);
    }

    /** On top of everything added before, the whole screen. */
    public void add(View v) {
        root.addView(v, Views.fill());
    }

    public void add(View v, FrameLayout.LayoutParams lp) {
        root.addView(v, lp);
    }

    public boolean closing() {
        return activity.isFinishing();
    }
}
