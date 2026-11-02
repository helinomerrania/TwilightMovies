package com.twilightcafe.movies.sites;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Panel;
import com.twilightcafe.movies.ui.Screen;

import java.util.ArrayList;
import java.util.List;

/** The Websites panel (in the menu once the app has more than one): each website by its name and address. */
public final class SitesPanel extends Panel {
    public interface Listener {
        void open(Site site);
    }

    private final Screen screen;
    private final Sites sites;
    private final List<Button> buttons = new ArrayList<Button>();
    private Button current;   // the website shown, when the panel opened

    public SitesPanel(Screen s, Sites sites, final Listener listener) {
        super(s.views.scrim(Palette.SHADE));
        this.screen = s;
        this.sites = sites;
        LinearLayout card = s.views.card();
        card.addView(s.views.label(s.t(R.string.sites_title), 24, Palette.TEXT, true));
        LinearLayout list = s.views.column();
        for (final Site site : sites.all()) {
            Button b = s.views.button("", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.open(site);
                }
            });
            buttons.add(b);
            list.addView(b, s.views.listRow(buttons.size() == 1 ? 12 : 8));
        }
        ScrollView scroll = new ScrollView(s.activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        card.addView(scroll, s.views.rest());
        view.addView(card, s.views.cardParams(560));
    }

    /** Each website with its address now; the one shown has the remote. */
    public void show() {
        boolean kurdish = screen.texts.kurdish();
        List<Site> all = sites.all();
        Button focus = buttons.get(0);
        for (int i = 0; i < all.size(); i++) {
            Site site = all.get(i);
            buttons.get(i).setText(site.name(kurdish) + "  ·  " + site.where(sites.address(site)));
            if (site == sites.current()) focus = buttons.get(i);
        }
        view.setVisibility(View.VISIBLE);
        current = focus;
        focus.requestFocus();
    }

    @Override
    protected View first() {
        return current;
    }
}
