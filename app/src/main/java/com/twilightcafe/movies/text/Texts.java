package com.twilightcafe.movies.text;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.twilightcafe.movies.R;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The app's own texts: Kurdish (res/values-b+ckb), the language of the café and of the website, unless English
 * was chosen (res/values); numbers and dates in the same language. Kurdish only once its translation is in
 * the app. A text with no Kurdish translation yet shows in English.
 */
public final class Texts {
    public static final String KURDISH = "ckb";   // Central Kurdish (Sorani)
    public static final String ENGLISH = "en";

    private final Context ctx;
    private final boolean kurdishReady;
    private final boolean kurdish;
    private final Resources res;

    /** chosen: the language chosen in the menu ("" if never). */
    public Texts(Context c, String chosen) {
        ctx = c;
        kurdishReady = translated();
        kurdish = kurdishReady && !ENGLISH.equals(chosen);
        res = kurdish ? in(KURDISH) : c.getResources();
    }

    public String t(int id) {
        return res.getString(id);
    }

    /** A text with its %1$s, %2$d... filled in (without args: as it is, % and all). */
    public String t(int id, Object... args) {
        return args.length == 0 ? res.getString(id) : res.getString(id, args);
    }

    public boolean kurdish() {
        return kurdish;
    }

    /** Whether there is another language to choose (the Kurdish translation is in the app). */
    public boolean kurdishReady() {
        return kurdishReady;
    }

    /** The other language: its code, to switch to it. */
    public String other() {
        return kurdish ? ENGLISH : KURDISH;
    }

    /** The other language by its own name ("English" in Kurdish, "کوردی" in English). */
    public String otherName() {
        return in(other()).getString(R.string.language_name);
    }

    /** "Language: English · کوردی" */
    public String languageLabel() {
        return t(R.string.menu_language) + ": " + t(R.string.language_name) + "  ·  " + otherName();
    }

    public String count(int n) {
        return NumberFormat.getIntegerInstance(locale()).format(n);
    }

    public String date(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(new Date(millis));
    }

    private Locale locale() {
        return kurdish ? new Locale(KURDISH) : Locale.getDefault();
    }

    private Resources in(String language) {
        Configuration c = new Configuration(ctx.getResources().getConfiguration());
        c.setLocale(new Locale(language));
        return ctx.createConfigurationContext(c).getResources();
    }

    private boolean translated() {
        try {
            return !in(KURDISH).getString(R.string.language_name).equals(in(ENGLISH).getString(R.string.language_name));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
