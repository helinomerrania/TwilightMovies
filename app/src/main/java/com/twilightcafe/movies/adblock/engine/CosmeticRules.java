package com.twilightcafe.movies.adblock.engine;

import java.util.HashSet;
import java.util.Locale;

/**
 * The rules that hide ad boxes: CSS selectors for some sites, for every page ("highly generic"), and generic
 * ones filed under a class or an id (".ad-box", "#banner"), asked for only when a page has that class or id,
 * so a page gets a few dozen instead of thousands. Exceptions (#@#) take a selector away on a site.
 */
public final class CosmeticRules {
    public static final byte HIDE = 0;
    public static final byte UNHIDE = 1;
    private static final String HIDDEN = "{display:none!important}\n";

    final long[] noCosmeticHosts;     // @@||host^$elemhide: no hiding there
    final long[] noGenericHosts;      // @@||host^$generichide: only the site's own rules there
    final long[] keyedHashes;         // Hash.of(".class" or "#id"), sorted
    final String[] keyedSelectors;
    final String[] highSelectors;
    final long[] specificHashes;      // Hash.of(site or "name.*"), sorted
    final byte[] specificKinds;       // HIDE or UNHIDE
    final String[] specificSelectors;
    private final Hosts hosts;

    // the last page's exceptions, for genericCss (asked again and again for one page)
    private String unhideHost;
    private HashSet<String> unhideCache;

    public CosmeticRules(Hosts hosts, long[] noCosmeticHosts, long[] noGenericHosts,
                         long[] keyedHashes, String[] keyedSelectors, String[] highSelectors,
                         long[] specificHashes, byte[] specificKinds, String[] specificSelectors) {
        this.hosts = hosts;
        this.noCosmeticHosts = noCosmeticHosts;
        this.noGenericHosts = noGenericHosts;
        this.keyedHashes = keyedHashes;
        this.keyedSelectors = keyedSelectors;
        this.highSelectors = highSelectors;
        this.specificHashes = specificHashes;
        this.specificKinds = specificKinds;
        this.specificSelectors = specificSelectors;
    }

    public int count() {
        return keyedSelectors.length + highSelectors.length + specificSelectors.length;
    }

    /** The CSS for a page of host: its own hiding rules, and (unless the lists say not) the generic ones for every page. */
    public String pageCss(String host) {
        if (host == null || host.isEmpty()) return "";
        host = host.toLowerCase(Locale.ROOT);
        long[] h = hosts.hashes(host);
        if (NetFilter.anyIn(noCosmeticHosts, h, h.length)) return "";
        HashSet<String> unhide = unhides(h);
        StringBuilder css = new StringBuilder();
        HashSet<String> done = new HashSet<String>();
        for (long k : h) {
            for (int j = RuleGroup.first(specificHashes, k); j >= 0 && j < specificHashes.length && specificHashes[j] == k; j++) {
                String sel = specificSelectors[j];
                if (specificKinds[j] == HIDE && !unhide.contains(sel) && done.add(sel)) css.append(sel).append(HIDDEN);
            }
        }
        if (!NetFilter.anyIn(noGenericHosts, h, h.length)) {
            for (String sel : highSelectors) {
                if (!unhide.contains(sel) && done.add(sel)) css.append(sel).append(HIDDEN);
            }
        }
        return css.toString();
    }

    /** The CSS of the generic rules filed under the page's classes and ids ("keys": ".ad-box #banner"). */
    public synchronized String genericCss(String host, String keys) {
        if (host == null || keys == null || keys.isEmpty()) return "";
        host = host.toLowerCase(Locale.ROOT);
        HashSet<String> unhide;
        if (host.equals(unhideHost)) {
            unhide = unhideCache;
        } else {
            long[] h = hosts.hashes(host);
            boolean none = NetFilter.anyIn(noCosmeticHosts, h, h.length) || NetFilter.anyIn(noGenericHosts, h, h.length);
            unhide = none ? null : unhides(h);
            unhideHost = host;
            unhideCache = unhide;
        }
        if (unhide == null) return "";
        StringBuilder css = new StringBuilder();
        int n = keys.length();
        int i = 0;
        while (i < n) {
            while (i < n && keys.charAt(i) == ' ') i++;
            int s = i;
            while (i < n && keys.charAt(i) != ' ') i++;
            if (i - s < 2) continue;
            long k = Hash.of(keys, s, i);
            for (int j = RuleGroup.first(keyedHashes, k); j >= 0 && j < keyedHashes.length && keyedHashes[j] == k; j++) {
                String sel = keyedSelectors[j];
                if (!unhide.contains(sel)) css.append(sel).append(HIDDEN);
            }
        }
        return css.toString();
    }

    private HashSet<String> unhides(long[] h) {
        HashSet<String> out = new HashSet<String>();
        for (long k : h) {
            for (int j = RuleGroup.first(specificHashes, k); j >= 0 && j < specificHashes.length && specificHashes[j] == k; j++) {
                if (specificKinds[j] == UNHIDE) out.add(specificSelectors[j]);
            }
        }
        return out;
    }
}
