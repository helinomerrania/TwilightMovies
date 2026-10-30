package com.twilightcafe.movies.adblock.engine;

import java.io.IOException;
import java.io.InputStream;

/**
 * The ad blocker's rules, ready to use: the network rules (which addresses to block) and the hiding rules
 * (which ad boxes to hide), with the public suffix list they share. Made on GitHub from the filter lists
 * (tools/filters) and read from the filter file (FilterFile). It never changes once made, so any thread may
 * use it at any time.
 */
public final class FilterSet {
    /** When the lists were compiled (ms since 1970), how many rules there are, and which lists. */
    public final long created;
    public final int rules;
    public final String info;
    public final Hosts hosts;
    public final NetworkRules network;
    public final CosmeticRules cosmetic;

    public FilterSet(long created, int rules, String info, Hosts hosts, NetworkRules network, CosmeticRules cosmetic) {
        this.created = created;
        this.rules = rules;
        this.info = info;
        this.hosts = hosts;
        this.network = network;
        this.cosmetic = cosmetic;
    }

    /** Reads a filter file (gzip or not). Throws if it isn't one, or is cut short. */
    public static FilterSet read(InputStream in) throws IOException {
        return FilterFile.read(in);
    }

    /** When a filter file was made, from its first bytes only (to choose between two without reading both). */
    public static long readCreated(InputStream in) throws IOException {
        return FilterFile.readCreated(in);
    }

    public int blockedHostCount() {
        return network.blockHosts.length;
    }

    /** Whether to block r. After a block, r.redirect names the stand-in to answer with, if a rule gave one. */
    public boolean match(Request r) {
        return network.match(r);
    }

    public boolean blocksPage(String url) {
        return network.blocksPage(url);
    }

    public boolean pageAllowed(String host) {
        return network.pageAllowed(host);
    }

    public String pageCss(String host) {
        return cosmetic.pageCss(host);
    }

    public String genericCss(String host, String keys) {
        return cosmetic.genericCss(host, keys);
    }

    public String registrable(String host) {
        return hosts.registrable(host);
    }
}
