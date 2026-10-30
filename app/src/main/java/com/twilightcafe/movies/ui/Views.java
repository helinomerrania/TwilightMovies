package com.twilightcafe.movies.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The app's own views, made the same way everywhere: buttons the remote can reach (the sunset when focused),
 * the dark card of a panel, labels, and the sizes between them. Right to left in Kurdish.
 */
public final class Views {
    private final Context ctx;
    private final float density;
    private final boolean rtl;

    public Views(Context c, boolean rtl) {
        this.ctx = c;
        this.density = c.getResources().getDisplayMetrics().density;
        this.rtl = rtl;
    }

    public int dp(float v) {
        return Math.round(v * density);
    }

    public float density() {
        return density;
    }

    public Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(Palette.ACCENT, dp(10)));
        bg.addState(new int[]{android.R.attr.state_pressed}, rounded(Palette.ACCENT, dp(10)));
        bg.addState(new int[]{}, rounded(Palette.BUTTON, dp(10)));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{Palette.ON_ACCENT, Palette.ON_ACCENT, Palette.TEXT}));
        b.setStateListAnimator(null);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    /** A button with its text in the middle (in a row of buttons). */
    public Button centered(String text, View.OnClickListener onClick) {
        Button b = button(text, onClick);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    /** The full-screen shade a panel sits on: hidden at first; it stops touches but never takes the remote. */
    public FrameLayout scrim(int color) {
        FrameLayout s = new FrameLayout(ctx);
        s.setBackgroundColor(color);
        s.setClickable(true);
        s.setFocusable(false);   // Android 8+ makes clickable views focusable
        s.setVisibility(View.GONE);
        if (rtl) s.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return s;
    }

    public LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(26), dp(22), dp(26), dp(22));
        c.setBackground(rounded(Palette.CARD, dp(18)));
        return c;
    }

    public LinearLayout column() {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    public LinearLayout row() {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    public TextView label(CharSequence text, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** A card, widthDp wide, in the middle of its scrim. */
    public FrameLayout.LayoutParams cardParams(int widthDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(20);
        return lp;
    }

    /** A button in a list: the whole width, 50 dp high. */
    public LinearLayout.LayoutParams listRow(int topDp) {
        return margin(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)), topDp);
    }

    /** The whole width, as high as it needs. */
    public LinearLayout.LayoutParams wide(int topDp) {
        return margin(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), topDp);
    }

    /** As wide and as high as it needs. */
    public LinearLayout.LayoutParams wrap(int topDp) {
        return margin(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), topDp);
    }

    /** One of several buttons sharing a row: an equal part of it, 50 dp high, startDp after the one before. */
    public LinearLayout.LayoutParams share(int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.setMarginStart(dp(startDp));
        return lp;
    }

    /** What's left of a column's height (a list that scrolls). */
    public LinearLayout.LayoutParams rest() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams margin(LinearLayout.LayoutParams lp, int topDp) {
        lp.topMargin = dp(topDp);
        return lp;
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static boolean shown(View v) {
        return v != null && v.getVisibility() == View.VISIBLE;
    }
}
