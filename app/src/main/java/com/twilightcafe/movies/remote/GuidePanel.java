package com.twilightcafe.movies.remote;

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
 * The remote guide: what each key does. It shows once by itself on a new box, and from the menu any time; on
 * the first start it's also where the other language is chosen (its button, by the language's own name).
 */
public final class GuidePanel extends Panel {
    public interface Listener {
        void close();

        void otherLanguage();
    }

    /** Whether something else is on the screen (the guide waits). */
    public interface Busy {
        boolean busy();
    }

    private static final int[][] LINES = {
            {R.string.guide_arrows_key, R.string.guide_arrows},
            {R.string.guide_ok_key, R.string.guide_ok},
            {R.string.guide_back_key, R.string.guide_back},
            {R.string.guide_menu_key, R.string.guide_menu},
            {R.string.guide_media_key, R.string.guide_media},
            {R.string.guide_channel_key, R.string.guide_channel},
    };

    private final Screen screen;
    private final Button close;

    public GuidePanel(Screen s, final Listener listener) {
        super(s.views.scrim(Palette.SHADE));
        this.screen = s;
        LinearLayout card = s.views.card();
        card.addView(s.views.label(s.t(R.string.guide_title), 24, Palette.TEXT, true));
        LinearLayout rows = s.views.column();
        for (int[] line : LINES) {
            LinearLayout row = s.views.row();
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(s.dp(220), LinearLayout.LayoutParams.WRAP_CONTENT);
            kp.setMarginEnd(s.dp(14));
            row.addView(s.views.label(s.t(line[0]), 16, Palette.ACCENT, true), kp);
            row.addView(s.views.label(s.t(line[1]), 16, Palette.TEXT_SOFT, false),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            rows.addView(row, s.views.wide(12));
        }
        TextView tip = s.views.label(s.t(R.string.guide_pointer), 15, Palette.TEXT_DIM, false);
        tip.setLineSpacing(0f, 1.15f);
        rows.addView(tip, s.views.wide(18));
        ScrollView scroll = new ScrollView(s.activity);
        scroll.setFocusable(true);   // on a small screen, the arrows scroll it
        scroll.addView(rows);
        card.addView(scroll, s.views.rest());
        LinearLayout buttons = s.views.row();
        close = s.views.centered(s.t(R.string.guide_close), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.close();
            }
        });
        buttons.addView(close, s.views.share(0));
        if (s.texts.kurdishReady()) {
            buttons.addView(s.views.centered(s.texts.otherName(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.otherLanguage();
                }
            }), s.views.share(12));
        }
        card.addView(buttons, s.views.wide(18));
        view.addView(card, s.views.cardParams(660));
    }

    public void show() {
        view.setVisibility(View.VISIBLE);
        close.requestFocus();
    }

    @Override
    protected View first() {
        return close;
    }

    /** On a new box: open is run once nothing else is on the screen (looked at every 4 seconds, 12 times at most). */
    public void firstTime(final Busy busy, final Runnable open) {
        screen.main.postDelayed(new Runnable() {
            private int tries;

            @Override
            public void run() {
                if (!busy.busy()) open.run();
                else if (++tries < 12) screen.main.postDelayed(this, 4000);
            }
        }, 3000);
    }
}
