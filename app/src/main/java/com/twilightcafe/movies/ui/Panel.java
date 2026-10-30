package com.twilightcafe.movies.ui;

import android.view.View;
import android.widget.FrameLayout;

/**
 * One of the app's panels over the page (the menu, the guide, the websites...): a shade over the whole screen
 * (Views.scrim) with the panel's card in it, shown or not. Shown, it has the remote, even when the page behind
 * it took the focus (a web engine can, as its page loads): keepFocus gives it back.
 */
public abstract class Panel {
    protected final FrameLayout view;

    protected Panel(FrameLayout scrim) {
        this.view = scrim;
    }

    public View view() {
        return view;
    }

    public boolean shown() {
        return Views.shown(view);
    }

    public void hide() {
        view.setVisibility(View.GONE);
    }

    /** What has the remote when the panel opens (its first button, its field...). */
    protected abstract View first();

    /** Shown, it has the remote back if anything else took the focus; true if it's shown. */
    public boolean keepFocus() {
        if (!shown()) return false;
        if (!view.hasFocus()) {
            View f = first();
            if (f != null) f.requestFocus();
        }
        return true;
    }
}
