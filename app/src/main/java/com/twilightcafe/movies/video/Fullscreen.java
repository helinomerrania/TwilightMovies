package com.twilightcafe.movies.video;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.ui.Views;
import com.twilightcafe.movies.web.PageTarget;

/** A film full screen: the page's video view over the page (with its own player's controls), and back again. */
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
    private View video;
    private WebChromeClient.CustomViewCallback callback;

    public Fullscreen(Screen s, PageTarget target, Listener listener) {
        this.target = target;
        this.listener = listener;
        holder = new FrameLayout(s.activity);
        holder.setBackgroundColor(Palette.BLACK);
        holder.setVisibility(View.GONE);
    }

    /** The view the video goes in (over the page, under the app's panels). */
    public View view() {
        return holder;
    }

    public boolean active() {
        return video != null;
    }

    public void enter(View v, WebChromeClient.CustomViewCallback cb) {
        if (video != null) {
            cb.onCustomViewHidden();   // one at a time
            return;
        }
        video = v;
        callback = cb;
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        holder.addView(v, Views.fill());
        holder.setVisibility(View.VISIBLE);
        target.setFullscreen(v);
        listener.entered();
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.requestFocus();
    }

    public void leave() {
        if (video == null) return;
        WebChromeClient.CustomViewCallback cb = callback;
        drop();
        if (cb != null) {
            try {
                cb.onCustomViewHidden();
            } catch (Throwable ignored) {
                // the web engine already left full screen
            }
        }
        listener.left();
    }

    /** Taken away without a word to the page (its engine crashed, and the video with it). */
    public void drop() {
        if (video == null) return;
        holder.removeView(video);
        holder.setVisibility(View.GONE);
        video = null;
        callback = null;
        target.setFullscreen(null);
    }
}
