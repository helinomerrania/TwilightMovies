package com.twilightcafe.movies.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The panels BACK closes (the menu, the websites, the address, the guide): whether one is open, and closing
 * them. The error screen isn't one of them: it stays until the page loads.
 */
public final class Panels {
    private final List<Panel> all = new ArrayList<Panel>();
    private final Runnable focusPage;

    /** focusPage: gives the remote back to the page (or to the error screen, if it's up). */
    public Panels(Runnable focusPage) {
        this.focusPage = focusPage;
    }

    public <T extends Panel> T add(T panel) {
        all.add(panel);
        return panel;
    }

    public boolean anyShown() {
        for (Panel p : all) {
            if (p.shown()) return true;
        }
        return false;
    }

    /** Closes them all; the page gets the remote again if one was open. */
    public void close() {
        boolean was = anyShown();
        hideAll();
        if (was) focusPage.run();
    }

    /** Hides them all, for another panel to take over. */
    public void hideAll() {
        for (Panel p : all) {
            if (p.shown()) p.hide();
        }
    }
}
