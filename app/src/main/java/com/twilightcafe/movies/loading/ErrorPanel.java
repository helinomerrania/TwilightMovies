package com.twilightcafe.movies.loading;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Panel;
import com.twilightcafe.movies.ui.Screen;

/**
 * The error screen's views: which website can't be reached and why, the countdown to the next try, "Try again"
 * and "Change address", and the web engine's own words, small, for whoever fixes it.
 */
final class ErrorPanel extends Panel {
    interface Listener {
        void retry();

        void changeAddress();
    }

    private final TextView title;
    private final TextView detail;
    private final TextView status;
    private final TextView code;
    private final Button retry;

    ErrorPanel(Screen s, final Listener listener) {
        super(s.views.scrim(Palette.BLACK));
        LinearLayout box = s.views.column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        title = centered(s.views.label("", 26, Palette.TEXT, true));
        box.addView(title, s.views.wrap(0));
        detail = centered(s.views.label("", 16, Palette.TEXT_DIM, false));
        detail.setMaxWidth(s.dp(640));
        detail.setLineSpacing(0f, 1.15f);
        box.addView(detail, s.views.wrap(12));
        status = centered(s.views.label("", 16, Palette.ACCENT, true));
        box.addView(status, s.views.wrap(16));
        LinearLayout row = s.views.row();
        retry = s.views.centered(s.t(R.string.error_try_again), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.retry();
            }
        });
        row.addView(retry, new LinearLayout.LayoutParams(s.dp(220), s.dp(52)));
        Button change = s.views.centered(s.t(R.string.error_change_address), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.changeAddress();
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(s.dp(220), s.dp(52));
        cp.setMarginStart(s.dp(14));
        row.addView(change, cp);
        box.addView(row, s.views.wrap(22));
        code = centered(s.views.label("", 12, Palette.FAINT, false));
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(code, s.views.wrap(18));
        view.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        return t;
    }

    void set(String what, String why, String engineWords) {
        title.setText(what);
        detail.setText(why);
        code.setText(engineWords);
    }

    void status(String text) {
        status.setText(text);
    }

    void show() {
        view.setVisibility(View.VISIBLE);
    }

    void focus() {
        retry.requestFocus();
    }

    @Override
    protected View first() {
        return retry;
    }
}
