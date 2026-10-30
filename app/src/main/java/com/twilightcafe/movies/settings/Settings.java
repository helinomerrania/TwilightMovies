package com.twilightcafe.movies.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The app's own settings, kept on the box: the language, the website shown last, each website's address (when
 * one was typed for it) and its mouse pointer, and whether the remote guide was shown. The ad blocker and the
 * updates keep their own (AdBlocker, Updater).
 */
public final class Settings {
    private static final String FILE = "settings";
    private static final String LANGUAGE = "language";
    private static final String GUIDE = "guide_shown";
    private static final String SITE = "site";
    private static final String ADDRESS = "address.";   // + the website's id
    private static final String POINTER = "pointer.";   // + the website's id
    // Before there were several websites: the one website's address and pointer
    private static final String OLD_ADDRESS = "home_url";
    private static final String OLD_POINTER = "pointer_mode";

    private final SharedPreferences prefs;

    public Settings(Context c) {
        prefs = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** "ckb", "en", or "" (never chosen). */
    public String language() {
        return prefs.getString(LANGUAGE, "");
    }

    public void setLanguage(String code) {
        prefs.edit().putString(LANGUAGE, code).apply();
    }

    public boolean guideShown() {
        return prefs.getBoolean(GUIDE, false);
    }

    public void setGuideShown(boolean shown) {
        prefs.edit().putBoolean(GUIDE, shown).apply();
    }

    /** The id of the website shown last ("" on a new box). */
    public String site() {
        return prefs.getString(SITE, "");
    }

    public void setSite(String id) {
        prefs.edit().putString(SITE, id).apply();
    }

    /** The address typed for a website, or null for its own. */
    public String address(String site) {
        return prefs.getString(ADDRESS + site, null);
    }

    public void setAddress(String site, String url) {
        if (url == null) prefs.edit().remove(ADDRESS + site).apply();
        else prefs.edit().putString(ADDRESS + site, url).apply();
    }

    /** Whether the mouse pointer is on for a website; dflt until it's switched there. */
    public boolean pointer(String site, boolean dflt) {
        return prefs.getBoolean(POINTER + site, dflt);
    }

    public void setPointer(String site, boolean on) {
        prefs.edit().putBoolean(POINTER + site, on).apply();
    }

    /** The address and pointer an older version kept for its only website, given to that website (once). */
    public void takeOld(String site, String ownAddress) {
        if (!prefs.contains(OLD_ADDRESS) && !prefs.contains(OLD_POINTER)) return;
        SharedPreferences.Editor e = prefs.edit();
        String old = prefs.getString(OLD_ADDRESS, null);
        if (old != null && !old.equals(ownAddress) && !prefs.contains(ADDRESS + site)) e.putString(ADDRESS + site, old);
        if (prefs.contains(OLD_POINTER) && !prefs.contains(POINTER + site)) {
            e.putBoolean(POINTER + site, prefs.getBoolean(OLD_POINTER, false));
        }
        e.remove(OLD_ADDRESS).remove(OLD_POINTER).apply();
    }
}
