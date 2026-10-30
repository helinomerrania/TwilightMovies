package com.twilightcafe.movies.sites;

import com.twilightcafe.movies.settings.Settings;
import com.twilightcafe.movies.util.Urls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The app's websites (SiteCatalog) and the one shown now, with its address (its own, or one typed for it in
 * the menu) and its mouse pointer, each kept for it (Settings). The website shown last opens at the next start.
 */
public final class Sites {
    private final List<Site> all;
    private final Settings settings;
    private Site current;

    public Sites(List<Site> all, Settings settings) {
        this.all = new ArrayList<Site>(all);
        this.settings = settings;
        Site first = this.all.get(0);
        settings.takeOld(first.id, first.address);   // an older version's address and pointer: the first website's
        current = find(settings.site());
        if (current == null) current = first;
    }

    public List<Site> all() {
        return Collections.unmodifiableList(all);
    }

    public Site current() {
        return current;
    }

    public void choose(Site s) {
        current = s;
        settings.setSite(s.id);
    }

    /** Where the website shown opens. */
    public String address() {
        return address(current);
    }

    public String address(Site s) {
        String typed = settings.address(s.id);
        return typed != null ? typed : s.address;
    }

    /** An address typed for the website shown (its own address again: back to that). */
    public void setAddress(String url) {
        settings.setAddress(current.id, url.equals(current.address) ? null : url);
    }

    public void useOwnAddress() {
        settings.setAddress(current.id, null);
    }

    public boolean pointer() {
        return settings.pointer(current.id, current.pointer);
    }

    public void setPointer(boolean on) {
        settings.setPointer(current.id, on);
    }

    /** Whether url is on the website shown: on one of its domains, or the one of the address typed for it. */
    public boolean shows(String url) {
        String host = Urls.hostOf(url);
        return current.owns(host) || Site.under(host, new String[]{Site.domainOf(address())});
    }

    /** The hosts and domains of every website: the ad blocker never blocks them, and pages go between them. */
    public String[] ownHosts() {
        List<String> hosts = new ArrayList<String>();
        for (Site s : all) {
            hosts.add(Urls.hostOf(address(s)));
            Collections.addAll(hosts, s.domains());
        }
        return hosts.toArray(new String[0]);
    }

    public Site find(String id) {
        for (Site s : all) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }
}
