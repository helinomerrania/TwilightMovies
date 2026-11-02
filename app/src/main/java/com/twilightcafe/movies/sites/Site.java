package com.twilightcafe.movies.sites;

import com.twilightcafe.movies.util.Urls;

import java.util.Locale;

/**
 * One of the app's websites (assets/sites.json): its names, the address it opens at, the domains that are its
 * own (pages and pop-ups go between them as they like, and the ad blocker never blocks them), and whether it
 * needs the mouse pointer (a website without a TV interface of its own). Or the Twilight screens (the address
 * "twilight:"), which show the films of the websites they read (their domains) in one look: in Firefox's engine
 * only (a page of the app's extension there, assets/gecko/ui/). Plain Java.
 */
public final class Site {
    public final String id;
    public final String address;
    /** Whether the mouse pointer is on at first (it can be switched in the menu, for each website). */
    public final boolean pointer;
    private final String name;
    private final String nameKurdish;
    private final String[] domains;

    /** domains: null or empty for the address's own (its host's last two labels: kurdfilm.krd). */
    public Site(String id, String name, String nameKurdish, String address, String[] domains, boolean pointer) {
        this.id = id;
        this.name = name;
        this.nameKurdish = nameKurdish;
        this.address = address;
        this.domains = domains != null && domains.length > 0 ? lower(domains) : new String[]{domainOf(address)};
        this.pointer = pointer;
    }

    /** Whether this is the Twilight screens (they need Firefox's engine). */
    public boolean twilight() {
        return Urls.isOwn(address);
    }

    /** The Twilight screens' page, in the app's language, saying the app's version ("twilight:" as sites.json has it). */
    public static String twilightPage(String address, boolean kurdish, String version) {
        if (!Urls.OWN.equals(address)) return address;
        StringBuilder v = new StringBuilder();
        for (char c : (version == null ? "" : version).toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '.' || c == '-' || c == '_') v.append(c);
        }
        return Urls.OWN + "ui/index.html?lang=" + (kurdish ? "ckb" : "en") + "&v=" + v;
    }

    public String name(boolean kurdish) {
        return kurdish && nameKurdish != null && !nameKurdish.isEmpty() ? nameKurdish : name;
    }

    public String[] domains() {
        return domains.clone();
    }

    /** Whether a host is the website's own: one of its domains, or under one (app.kurdfilm.krd, kurdfilm.krd). */
    public boolean owns(String host) {
        return under(host, domains);
    }

    /** The last two labels of an address's host (app.kurdfilm.krd: kurdfilm.krd), as the app always took them. */
    public static String domainOf(String address) {
        String host = Urls.hostOf(address);
        String[] parts = host.split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : host;
    }

    static boolean under(String host, String[] domains) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.US);
        for (String d : domains) {
            if (!d.isEmpty() && (h.equals(d) || h.endsWith("." + d))) return true;
        }
        return false;
    }

    private static String[] lower(String[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i].trim().toLowerCase(Locale.US);
        return out;
    }
}
