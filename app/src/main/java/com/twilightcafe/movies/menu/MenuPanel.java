package com.twilightcafe.movies.menu;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Panel;
import com.twilightcafe.movies.ui.Screen;

/**
 * The app's menu (MENU, or hold OK or BACK): the page's address, the app's build and the web engine at the
 * top, the ad blocker's rules under them, then a button for each thing the app does. Labels that change (on or
 * off, how many blocked, a new version, which engine) are asked for each time it opens.
 */
public final class MenuPanel extends Panel {
    /** What each button does. */
    public interface Actions {
        void home();

        void sites();

        void reload();

        void togglePointer();

        void toggleAdBlocker();

        void allowJump();

        void guide();

        void language();

        void address();

        void update();

        /** The other web engine (the box's own, or Firefox's). */
        void switchEngine();

        void exit();
    }

    /** The labels that change; null hides that line or button. */
    public interface Status {
        String subtitle();

        String filters();

        String sitesLabel();

        String pointerLabel();

        String adBlockerLabel();

        String allowLabel();

        String updateLabel();

        String engineLabel();
    }

    private final Status status;
    private final TextView subtitle;
    private final TextView filters;
    private final Button home;
    private final Button sites;
    private final Button pointer;
    private final Button adBlocker;
    private final Button allow;
    private final Button update;
    private final Button engine;

    /** updates: whether this build can update itself (one built on GitHub). */
    public MenuPanel(Screen s, final Actions a, Status status, boolean updates) {
        super(s.views.scrim(Palette.VEIL));
        this.status = status;
        LinearLayout card = s.views.card();
        card.addView(s.views.label(s.t(R.string.app_name), 24, Palette.TEXT, true));
        subtitle = s.views.label("", 14, Palette.SUBTITLE, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtitle.setTextDirection(s.texts.kurdish() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        card.addView(subtitle, s.views.wide(2));
        filters = s.views.label("", 13, Palette.HINT, false);
        filters.setSingleLine(true);
        filters.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams fl = s.views.wide(2);
        fl.bottomMargin = s.dp(10);
        card.addView(filters, fl);

        LinearLayout list = s.views.column();
        home = add(s, list, 4, s.t(R.string.menu_home), v -> a.home());
        sites = add(s, list, 8, "", v -> a.sites());
        add(s, list, 8, s.t(R.string.menu_reload), v -> a.reload());
        pointer = add(s, list, 8, "", v -> a.togglePointer());
        adBlocker = add(s, list, 8, "", v -> a.toggleAdBlocker());
        allow = add(s, list, 8, "", v -> a.allowJump());
        add(s, list, 8, s.t(R.string.menu_guide), v -> a.guide());
        Button language = add(s, list, 8, s.texts.kurdishReady() ? s.texts.languageLabel() : "", v -> a.language());
        language.setVisibility(s.texts.kurdishReady() ? View.VISIBLE : View.GONE);
        add(s, list, 8, s.t(R.string.menu_address), v -> a.address());
        update = add(s, list, 8, "", v -> a.update());
        update.setVisibility(updates ? View.VISIBLE : View.GONE);
        engine = add(s, list, 8, "", v -> a.switchEngine());
        add(s, list, 8, s.t(R.string.menu_exit), v -> a.exit());

        ScrollView scroll = new ScrollView(s.activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        card.addView(scroll, s.views.rest());
        card.addView(s.views.label(s.t(R.string.menu_hint), 13, Palette.HINT, false), s.views.wrap(14));
        view.addView(card, s.views.cardParams(440));
    }

    private static Button add(Screen s, LinearLayout list, int topDp, String text, View.OnClickListener onClick) {
        Button b = s.views.button(text, onClick);
        list.addView(b, s.views.listRow(topDp));
        return b;
    }

    /** Opens with every label as it is now; Home has the remote. */
    public void show() {
        subtitle.setText(status.subtitle());
        set(filters, status.filters());
        set(sites, status.sitesLabel());
        pointer.setText(status.pointerLabel());
        adBlocker.setText(status.adBlockerLabel());
        set(allow, status.allowLabel());
        update.setText(status.updateLabel());
        set(engine, status.engineLabel());
        view.setVisibility(View.VISIBLE);
        home.requestFocus();
    }

    /** The update button's label again (a check just ended), open or not. */
    public void refreshUpdate() {
        update.setText(status.updateLabel());
    }

    /** Opens with "Install the new version" chosen. */
    public void showUpdate() {
        show();
        update.requestFocus();
    }

    private static void set(TextView v, String text) {
        v.setText(text != null ? text : "");
        v.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }
}
