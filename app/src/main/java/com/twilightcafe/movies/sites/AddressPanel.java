package com.twilightcafe.movies.sites;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Panel;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.ui.Views;
import com.twilightcafe.movies.util.Urls;

/** Typing the address the website shown opens at (if it moved): open it, or the website's own again. */
public final class AddressPanel extends Panel {
    public interface Listener {
        void open(String typed);

        void useOwn();

        void cancel();
    }

    private final Screen screen;
    private final EditText input;
    private final TextView own;

    public AddressPanel(Screen s, final Listener listener) {
        super(s.views.scrim(Palette.SHADE));
        this.screen = s;
        LinearLayout card = s.views.card();
        card.addView(s.views.label(s.t(R.string.address_title), 22, Palette.TEXT, true));
        input = field(s);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) listener.open(input.getText().toString());
                    return true;   // Enter on a keyboard plugged into the box
                }
                if (actionId != EditorInfo.IME_ACTION_GO && actionId != EditorInfo.IME_ACTION_DONE
                        && actionId != EditorInfo.IME_ACTION_NEXT && actionId != EditorInfo.IME_ACTION_SEND) return false;
                listener.open(input.getText().toString());
                return true;
            }
        });
        card.addView(input, s.views.wide(14));
        own = s.views.label("", 13, Palette.HINT, false);
        card.addView(own, s.views.wrap(8));
        LinearLayout row = s.views.row();
        Button[] buttons = {
            s.views.centered(s.t(R.string.address_open), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.open(input.getText().toString());
                }
            }),
            s.views.centered(s.t(R.string.address_use_default), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.useOwn();
                }
            }),
            s.views.centered(s.t(R.string.address_cancel), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.cancel();
                }
            }),
        };
        for (int i = 0; i < buttons.length; i++) row.addView(buttons[i], s.views.share(i > 0 ? 10 : 0));
        card.addView(row, s.views.wide(18));
        // wide: room for the Kurdish labels, even with the box's text set larger
        view.addView(card, s.views.cardParams(680));
    }

    private static EditText field(Screen s) {
        EditText e = new EditText(s.activity);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setImeOptions(EditorInfo.IME_ACTION_GO);
        e.setTextColor(Palette.TEXT);
        e.setHintTextColor(Palette.FIELD_HINT);
        e.setHint("https://");
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setPadding(s.dp(14), s.dp(10), s.dp(14), s.dp(10));
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // addresses read left to right
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = Views.rounded(Palette.FIELD, s.dp(10));
        on.setStroke(s.dp(2), Palette.ACCENT);
        GradientDrawable off = Views.rounded(Palette.FIELD, s.dp(10));
        off.setStroke(s.dp(1), Palette.FIELD_EDGE);
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        e.setBackground(bg);
        return e;
    }

    /** address: where the website opens now; ownAddress: its own, for "Use the website's own". */
    public void show(String address, String ownAddress) {
        input.setText(address);
        input.setSelection(input.getText().length());
        own.setText(screen.t(R.string.address_default, Urls.shortUrl(ownAddress)));
        view.setVisibility(View.VISIBLE);
        input.requestFocus();
        screen.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!shown()) return;
                InputMethodManager imm = keyboard();
                if (imm != null) imm.showSoftInput(input, 0);
            }
        }, 300);
    }

    @Override
    protected View first() {
        return input;
    }

    /** Whether someone is typing (the error screen waits). */
    public boolean typing() {
        return shown();
    }

    @Override
    public void hide() {
        InputMethodManager imm = keyboard();
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        super.hide();
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) screen.activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}
