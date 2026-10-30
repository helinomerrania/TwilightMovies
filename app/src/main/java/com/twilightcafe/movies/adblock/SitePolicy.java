package com.twilightcafe.movies.adblock;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Request;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Where the ad blocker works, and where pages may go. The app's own websites are never blocked, and pages go
 * between them as they like; so do sites allowed from the menu (after a jump there was stopped). A page may
 * not jump to any other site. The blocker can be switched off for one site. A site is what can be registered
 * (www.example.co.uk: example.co.uk), by the filters' public suffix list once they're loaded. Plain Java.
 */
public final class SitePolicy {
    public static final int GO = 0;
    public static final int AD = 1;      // the lists say the page is an ad (or a harmful site)
    public static final int JUMP = 2;    // to another site, not one of the app's or allowed: stopped

    /** Where the choices are kept (the box's settings). */
    public interface Store {
        void save(String name, Set<String> sites);
    }

    private final Store store;
    private final Set<String> off = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> allowed = Collections.synchronizedSet(new HashSet<String>());
    private volatile String[] own = new String[0];   // hosts; their sites are worked out when asked
    private volatile FilterSet set;

    public SitePolicy(Set<String> off, Set<String> allowed, Store store) {
        this.off.addAll(off);
        this.allowed.addAll(allowed);
        this.store = store;
    }

    public void useFilters(FilterSet set) {
        this.set = set;
    }

    /** The app's websites, by their hosts and domains. */
    public void setOwn(String... hosts) {
        own = hosts.clone();
    }

    /** The site a host belongs to (www.example.co.uk: example.co.uk). */
    public String site(String host) {
        if (host == null || host.isEmpty()) return "";
        FilterSet s = set;
        if (s != null) return s.registrable(host);
        int last = host.lastIndexOf('.');
        int prev = last > 0 ? host.lastIndexOf('.', last - 1) : -1;
        return prev >= 0 ? host.substring(prev + 1) : host;
    }

    /** Whether the blocker works on pages of host's site (it does unless switched off there). */
    public boolean activeFor(String host) {
        return host == null || host.isEmpty() || !off.contains(site(host));
    }

    public void setActive(String host, boolean on) {
        String s = site(host);
        if (s.isEmpty()) return;
        if (on) off.remove(s);
        else off.add(s);
        save("off", off);
    }

    /** From now on, pages may go to host's site (and pop-ups may open there). */
    public void allow(String host) {
        String s = site(host);
        if (s.isEmpty()) return;
        allowed.add(s);
        save("allowed", allowed);
    }

    /** Whether url is on one of the app's websites, or a site allowed from the menu. */
    public boolean ownOrAllowed(String url) {
        String s = site(Request.hostOf(url));
        return !s.isEmpty() && (isOwn(s) || allowed.contains(s));
    }

    /** Whether a host is one of the app's websites' own: nothing it serves is blocked (its films, its pictures). */
    public boolean own(String host) {
        String s = site(host);
        return !s.isEmpty() && isOwn(s);
    }

    private boolean isOwn(String site) {
        for (String host : own) {
            if (site.equals(site(host))) return true;
        }
        return false;
    }

    /** Whether a page of pageHost may go to url (a link, a script's jump, a redirect): GO, AD or JUMP. */
    public int checkJump(String url, String pageHost) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (!u.startsWith("http:") && !u.startsWith("https:")) return GO;   // about:blank, javascript:...: the page itself
        if (Request.hostOf(url).isEmpty() || !activeFor(pageHost) || ownOrAllowed(url)) return GO;
        FilterSet s = set;
        return s != null && s.blocksPage(url) ? AD : JUMP;
    }

    private void save(String name, Set<String> sites) {
        HashSet<String> copy;
        synchronized (sites) {
            copy = new HashSet<String>(sites);
        }
        store.save(name, copy);
    }
}
