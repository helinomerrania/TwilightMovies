package com.twilightcafe.movies.video;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.ui.Views;
import com.twilightcafe.movies.web.PageTarget;

/**
 * A film full screen, and back again. Android's WebView hands over a view of its own for the video, put over the
 * page (with its player's controls); in Firefox's engine the page's own view shows it, so only the remote goes
 * to it.
 */
public final class Fullscreen {
    public interface Listener {
        /** The video is full screen (it has the remote now). */
        void entered();

        /** Back to the page. */
        void left();
    }

    private final FrameLayout holder;
    private final PageTarget target;
    private final Listener listener;
    private boolean on;
    private View video;
    private Runnable exit;

    public Fullscreen(Screen s, PageTarget target, Listener listener) {
        this.target = target;
        this.listener = listener;
        holder = new FrameLayout(s.activity);
        holder.setBackgroundColor(Palette.BLACK);
        holder.setVisibility(View.GONE);
    }

    /** The view a video view goes in (over the page, under the app's panels). */
    public View view() {
        return holder;
    }

    public boolean active() {
        return on;
    }

    /** v: the video's own view (null: the page's view shows it); exit: how the page leaves full screen. */
    public void enter(View v, Runnable exit) {
        if (on) {
            exit.run();   // one at a time
            return;
        }
        on = true;
        video = v;
        this.exit = exit;
        if (v != null) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            holder.addView(v, Views.fill());
            holder.setVisibility(View.VISIBLE);
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
        }
        target.setFullscreen(true, v);
        listener.entered();
        target.focus();
    }

    public void leave() {
        if (!on) return;
        Runnable e = exit;
        drop();
        if (e != null) {
            try {
                e.run();
            } catch (Throwable ignored) {
                // the web engine already left full screen
            }
        }
        listener.left();
    }

    /** Taken away without a word to the page (its engine crashed, and the video with it). */
    public void drop() {
        if (!on) return;
        if (video != null) {
            holder.removeView(video);
            holder.setVisibility(View.GONE);
        }
        on = false;
        video = null;
        exit = null;
        target.setFullscreen(false, null);
    }
}
