package com.twilightcafe.movies.adblock.engine;

/**
 * The rules about addresses: the sets of whole hosts (blocked, allowed, pages left alone) and the eight
 * groups of other rules (filed under a host, a word, nothing, or the page's site; blocks, then exceptions).
 * A request is blocked when a block matches and no exception does, unless the block is $important.
 */
public final class NetworkRules {
    /** host, word, nothing (blocks), the same three (exceptions), then the page's site (block, exception) */
    public static final int GROUPS = 8;

    final long[] blockHosts;
    final long[] allowHosts;
    final long[] pageAllowHosts;
    final RuleGroup[] groups;
    private final Hosts hosts;

    /** The host sets are sorted host hashes; groups has GROUPS groups, in the order above. */
    public NetworkRules(Hosts hosts, long[] blockHosts, long[] allowHosts, long[] pageAllowHosts, RuleGroup[] groups) {
        if (groups.length != GROUPS) throw new IllegalArgumentException(GROUPS + " groups");
        this.hosts = hosts;
        this.blockHosts = blockHosts;
        this.allowHosts = allowHosts;
        this.pageAllowHosts = pageAllowHosts;
        this.groups = groups;
    }

    public RuleGroup group(int i) {
        return groups[i];
    }

    /** Whether to block r. After a block, r.redirect names the stand-in to answer with, if a rule gave one. */
    public boolean match(Request r) {
        hosts.prepare(r);
        r.redirect = null;
        if (r.docHost != null && NetFilter.anyIn(pageAllowHosts, r.docHashes, r.docCount)) return false;
        NetFilter hit = null;
        boolean hostHit = NetFilter.anyIn(blockHosts, r.hostHashes, r.hostCount);
        if (!hostHit) {
            hit = groups[0].find(r.hostHashes, r.hostCount, r, false);
            if (hit == null) hit = groups[1].find(r.tokens, r.tokenCount, r, true);
            if (hit == null) hit = groups[6].find(r.docHashes, r.docCount, r, true);
            if (hit == null) hit = groups[2].any(r);
            if (hit == null) return false;
            if (hit.important()) {
                r.redirect = hit.redirect;
                return true;
            }
        }
        if (NetFilter.anyIn(allowHosts, r.hostHashes, r.hostCount)) return false;
        if (groups[3].find(r.hostHashes, r.hostCount, r, false) != null
                || groups[4].find(r.tokens, r.tokenCount, r, true) != null
                || groups[7].find(r.docHashes, r.docCount, r, true) != null
                || groups[5].any(r) != null) {
            return false;
        }
        r.redirect = hit != null ? hit.redirect : null;
        return true;
    }

    /** Whether a whole page at url is an ad or a known bad site (the page itself, not what it loads). */
    public boolean blocksPage(String url) {
        return match(new Request(url, Request.hostOf(url), Request.DOCUMENT));
    }

    /** Whether the lists say to leave pages of this host alone entirely (@@||host^$document). */
    public boolean pageAllowed(String host) {
        if (host == null || host.isEmpty()) return false;
        long[] h = hosts.hashes(host.toLowerCase(java.util.Locale.ROOT));
        return NetFilter.anyIn(pageAllowHosts, h, h.length);
    }
}
