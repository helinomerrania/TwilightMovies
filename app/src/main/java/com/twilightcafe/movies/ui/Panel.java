package com.twilightcafe.movies.ui;

import android.view.View;
import android.widget.FrameLayout;

/**
 * One of the app's panels over the page (the menu, the guide, the websites...): a shade over the whole screen
 * (Views.scrim) with the panel's card in it, shown or not.
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
}
