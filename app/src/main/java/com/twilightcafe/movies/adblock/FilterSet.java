package com.twilightcafe.movies.adblock;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

/**
 * The ad blocker's rules, ready to use. Read once from a filter file (tools/filters makes it on GitHub from
 * the filter lists), then asked about every address a page loads ({@link #match}) and for the CSS that hides
 * the ad boxes on a page ({@link #pageCss}, {@link #genericCss}). It never changes once read, so any thread
 * may use it at any time.
 *
 * The file (gzip): "TWAB", the format, when it was made, the number of rules, a line about the lists, then
 * sorted host-hash sets (blocked, allowed, pages allowed, no hiding, no generic hiding), the public suffix
 * list, eight groups of network rules (filed under a host, under a word of the address, under nothing, or
 * under the sites of the pages they are for; blocks then exceptions), and the hiding rules (generic by class
 * or id, generic for every page, and per site), then "END!".
 */
public final class FilterSet {
    static final int MAGIC = 0x54574142;   // "TWAB"
    static final int FORMAT = 1;
    static final int END = 0x454E4421;     // "END!"
    static final byte HIDE = 0;
    static final byte UNHIDE = 1;
    /** host, word, nothing (blocks), the same three (exceptions), then page site (block, exception) */
    static final int GROUPS = 8;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String HIDDEN = "{display:none!important}\n";

    /** When the lists were compiled (ms since 1970), how many rules there are, and which lists. */
    public final long created;
    public final int rules;
    public final String info;

    final long[] blockHosts;
    final long[] allowHosts;
    final long[] pageAllowHosts;
    final long[] noCosmeticHosts;
    final long[] noGenericHosts;
    final PublicSuffix psl;
    final Group hostBlock;
    final Group tokenBlock;
    final Group otherBlock;
    final Group hostAllow;
    final Group tokenAllow;
    final Group otherAllow;
    final Group pageBlock;    // rules for some sites' pages only, with no word to file them under
    final Group pageAllow;
    final long[] keyedHashes;
    final String[] keyedSelectors;
    final String[] highSelectors;
    final long[] specificHashes;
    final byte[] specificKinds;
    final String[] specificSelectors;

    // the last page's exceptions to hiding, for genericCss (called again and again for one page)
    private String unhideHost;
    private HashSet<String> unhideCache;

    /** Rules filed under a key (a host's or a word's hash), sorted by key. */
    static final class Group {
        final long[] keys;
        final NetFilter[] filters;

        Group(long[] keys, NetFilter[] filters) {
            this.keys = keys;
            this.filters = filters;
        }

        int size() {
            return filters.length;
        }
    }

    FilterSet(long created, int rules, String info, long[][] hostSets, PublicSuffix psl, Group[] groups,
              long[] keyedHashes, String[] keyedSelectors, String[] highSelectors,
              long[] specificHashes, byte[] specificKinds, String[] specificSelectors) {
        this.created = created;
        this.rules = rules;
        this.info = info;
        this.blockHosts = hostSets[0];
        this.allowHosts = hostSets[1];
        this.pageAllowHosts = hostSets[2];
        this.noCosmeticHosts = hostSets[3];
        this.noGenericHosts = hostSets[4];
        this.psl = psl;
        this.hostBlock = groups[0];
        this.tokenBlock = groups[1];
        this.otherBlock = groups[2];
        this.hostAllow = groups[3];
        this.tokenAllow = groups[4];
        this.otherAllow = groups[5];
        this.pageBlock = groups[6];
        this.pageAllow = groups[7];
        this.keyedHashes = keyedHashes;
        this.keyedSelectors = keyedSelectors;
        this.highSelectors = highSelectors;
        this.specificHashes = specificHashes;
        this.specificKinds = specificKinds;
        this.specificSelectors = specificSelectors;
    }

    public int blockedHostCount() {
        return blockHosts.length;
    }

    // ------------------------------------------------------------------ network rules

    /** Whether to block r. After a block, r.redirect names the surrogate to answer with, if a rule gave one. */
    public boolean match(Request r) {
        prepare(r);
        r.redirect = null;
        if (r.docHost != null && NetFilter.anyIn(pageAllowHosts, r.docHashes, r.docCount)) return false;
        NetFilter hit = null;
        boolean hostHit = NetFilter.anyIn(blockHosts, r.hostHashes, r.hostCount);
        if (!hostHit) {
            hit = byHost(hostBlock, r);
            if (hit == null) hit = byToken(tokenBlock, r);
            if (hit == null) hit = byPage(pageBlock, r);
            if (hit == null) hit = byNothing(otherBlock, r);
            if (hit == null) return false;
            if ((hit.flags & NetFilter.IMPORTANT) != 0) {
                r.redirect = hit.redirect;
                return true;
            }
        }
        if (NetFilter.anyIn(allowHosts, r.hostHashes, r.hostCount)) return false;
        if (byHost(hostAllow, r) != null || byToken(tokenAllow, r) != null || byPage(pageAllow, r) != null
                || byNothing(otherAllow, r) != null) {
            return false;
        }
        r.redirect = hit != null ? hit.redirect : null;
        return true;
    }

    /** Whether a whole page at host is an ad or a known bad site (for the page itself, not what it loads). */
    public boolean blocksPage(String url) {
        return match(new Request(url, Request.hostOf(url), Request.DOCUMENT));
    }

    /** Whether the lists say to leave pages of this host alone entirely (@@||host^$document). */
    public boolean pageAllowed(String host) {
        if (host == null || host.isEmpty()) return false;
        long[] h = hostHashes(host.toLowerCase(java.util.Locale.ROOT));
        return NetFilter.anyIn(pageAllowHosts, h, h.length);
    }

    private static NetFilter byHost(Group g, Request r) {
        if (g.size() == 0) return null;
        for (int i = 0; i < r.hostCount; i++) {
            long k = r.hostHashes[i];
            for (int j = first(g.keys, k); j >= 0 && j < g.keys.length && g.keys[j] == k; j++) {
                if (g.filters[j].optionsMatch(r)) return g.filters[j];
            }
        }
        return null;
    }

    private static NetFilter byToken(Group g, Request r) {
        if (g.size() == 0) return null;
        for (int i = 0; i < r.tokenCount; i++) {
            long k = r.tokens[i];
            for (int j = first(g.keys, k); j >= 0 && j < g.keys.length && g.keys[j] == k; j++) {
                NetFilter f = g.filters[j];
                if (f.optionsMatch(r) && f.matchesUrl(r)) return f;
            }
        }
        return null;
    }

    /** Rules filed under the sites they are for (domain=): looked up by the page's site. */
    private static NetFilter byPage(Group g, Request r) {
        if (g.size() == 0) return null;
        for (int i = 0; i < r.docCount; i++) {
            long k = r.docHashes[i];
            for (int j = first(g.keys, k); j >= 0 && j < g.keys.length && g.keys[j] == k; j++) {
                NetFilter f = g.filters[j];
                if (f.optionsMatch(r) && f.matchesUrl(r)) return f;
            }
        }
        return null;
    }

    private static NetFilter byNothing(Group g, Request r) {
        for (NetFilter f : g.filters) {
            if (f.optionsMatch(r) && f.matchesUrl(r)) return f;
        }
        return null;
    }

    /** The first index of key in sorted keys, or -1. */
    static int first(long[] keys, long key) {
        int i = Arrays.binarySearch(keys, key);
        if (i < 0) return -1;
        while (i > 0 && keys[i - 1] == key) i--;
        return i;
    }

    void prepare(Request r) {
        if (r.prepared) return;
        r.prepared = true;
        r.hostHashes = hostHashes(r.host);
        r.hostCount = r.hostHashes.length;
        if (r.docHost != null) {
            r.docHashes = hostHashes(r.docHost);
            r.docCount = r.docHashes.length;
            r.thirdParty = !registrable(r.host).equals(registrable(r.docHost));
        }
        tokenize(r);
    }

    /** The hashes of host and of each host it is under (a.b.com, b.com, com), and of its "entity" names (a.b.*, b.*). */
    long[] hostHashes(String host) {
        int n = host.length();
        if (n == 0) return new long[0];
        int suffix = psl.suffixStart(host);
        int labels = 1;
        for (int i = 0; i < n; i++) {
            if (host.charAt(i) == '.') labels++;
        }
        long[] out = new long[labels * 2];
        int c = 0;
        int nameEnd = suffix > 0 ? suffix - 1 : -1;   // "a.example" in a.example.co.uk
        for (int i = 0; i < n; i = host.indexOf('.', i) + 1) {
            out[c++] = Hash.of(host, i, n);
            if (nameEnd > i) out[c++] = Hash.entity(host, i, nameEnd);
            if (host.indexOf('.', i) < 0) break;
        }
        return c == out.length ? out : Arrays.copyOf(out, c);
    }

    public String registrable(String host) {
        return psl.registrable(host);
    }

    private static void tokenize(Request r) {
        String u = r.lower;
        int n = Math.min(u.length(), 2048);
        long[] t = new long[32];
        int c = 0;
        int i = 0;
        while (i < n && c < 64) {
            if (!NetFilter.tokenChar(u.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && NetFilter.tokenChar(u.charAt(i))) i++;
            if (i - s < 2) continue;
            if (c == t.length) t = Arrays.copyOf(t, c * 2);
            t[c++] = Hash.of(u, s, i);
        }
        r.tokens = t;
        r.tokenCount = c;
    }

    // ------------------------------------------------------------------ hiding ad boxes

    /** The CSS for a page of host: its own hiding rules, and (unless the lists say not) the generic ones for every page. */
    public String pageCss(String host) {
        if (host == null || host.isEmpty()) return "";
        host = host.toLowerCase(java.util.Locale.ROOT);
        long[] h = hostHashes(host);
        if (NetFilter.anyIn(noCosmeticHosts, h, h.length)) return "";
        HashSet<String> unhide = unhides(host, h);
        StringBuilder css = new StringBuilder();
        HashSet<String> done = new HashSet<String>();
        for (long k : h) {
            for (int j = first(specificHashes, k); j >= 0 && j < specificHashes.length && specificHashes[j] == k; j++) {
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

    /**
     * The CSS of the generic hiding rules filed under the page's classes and ids ("keys": ".ad-box #banner"),
     * as the page finds them. Only rules for what is on the page, so a page gets a few dozen, not thousands.
     */
    public synchronized String genericCss(String host, String keys) {
        if (host == null || keys == null || keys.isEmpty()) return "";
        host = host.toLowerCase(java.util.Locale.ROOT);
        HashSet<String> unhide;
        long[] h = hostHashes(host);
        if (host.equals(unhideHost)) {
            unhide = unhideCache;
        } else {
            if (NetFilter.anyIn(noCosmeticHosts, h, h.length) || NetFilter.anyIn(noGenericHosts, h, h.length)) {
                unhide = null;
            } else {
                unhide = unhides(host, h);
            }
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
            for (int j = first(keyedHashes, k); j >= 0 && j < keyedHashes.length && keyedHashes[j] == k; j++) {
                String sel = keyedSelectors[j];
                if (!unhide.contains(sel)) css.append(sel).append(HIDDEN);
            }
        }
        return css.toString();
    }

    private HashSet<String> unhides(String host, long[] h) {
        HashSet<String> out = new HashSet<String>();
        for (long k : h) {
            for (int j = first(specificHashes, k); j >= 0 && j < specificHashes.length && specificHashes[j] == k; j++) {
                if (specificKinds[j] == UNHIDE) out.add(specificSelectors[j]);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ reading the file

    private static DataInputStream open(InputStream raw) throws IOException {
        BufferedInputStream b = new BufferedInputStream(raw, 65536);
        b.mark(2);
        int m0 = b.read();
        int m1 = b.read();
        b.reset();
        InputStream src = m0 == 0x1f && m1 == 0x8b ? new GZIPInputStream(b, 65536) : b;
        DataInputStream in = new DataInputStream(new BufferedInputStream(src, 65536));
        if (in.readInt() != MAGIC) throw new IOException("not a filter file");
        int format = in.readInt();
        if (format != FORMAT) throw new IOException("filter file format " + format + ", this app reads " + FORMAT);
        return in;
    }

    /** When a filter file was made, from its first bytes only (to choose between two without reading both). */
    public static long readCreated(InputStream raw) throws IOException {
        return open(raw).readLong();
    }

    /** Reads a filter file (gzip or not). Throws if it isn't one, or is cut short. */
    public static FilterSet read(InputStream raw) throws IOException {
        DataInputStream in = open(raw);
        long created = in.readLong();
        int rules = in.readInt();
        String info = readString(in);
        long[][] hostSets = new long[5][];
        for (int i = 0; i < 5; i++) hostSets[i] = readLongs(in);
        PublicSuffix psl = new PublicSuffix(readLongs(in), readLongs(in), readLongs(in));
        Group[] groups = new Group[GROUPS];
        for (int g = 0; g < GROUPS; g++) {
            int n = (int) readVar(in);
            long[] keys = new long[n];
            NetFilter[] filters = new NetFilter[n];
            for (int i = 0; i < n; i++) {
                keys[i] = in.readLong();
                int flags = (int) readVar(in);
                int types = (int) readVar(in);
                String pattern = readString(in);
                String redirect = readString(in);
                filters[i] = new NetFilter(flags, types, pattern, redirect,
                        orNull(readLongs(in)), orNull(readLongs(in)), orNull(readLongs(in)),
                        orNull(readLongs(in)), orNull(readLongs(in)));
            }
            groups[g] = new Group(keys, filters);
        }
        int nk = (int) readVar(in);
        long[] keyedHashes = new long[nk];
        String[] keyedSelectors = new String[nk];
        for (int i = 0; i < nk; i++) {
            keyedHashes[i] = in.readLong();
            keyedSelectors[i] = readString(in);
        }
        int nh = (int) readVar(in);
        String[] high = new String[nh];
        for (int i = 0; i < nh; i++) high[i] = readString(in);
        int ns = (int) readVar(in);
        long[] specificHashes = new long[ns];
        byte[] kinds = new byte[ns];
        String[] specificSelectors = new String[ns];
        for (int i = 0; i < ns; i++) {
            specificHashes[i] = in.readLong();
            kinds[i] = in.readByte();
            specificSelectors[i] = readString(in);
        }
        if (in.readInt() != END) throw new IOException("filter file damaged");
        return new FilterSet(created, rules, info, hostSets, psl, groups, keyedHashes, keyedSelectors, high,
                specificHashes, kinds, specificSelectors);
    }

    private static long[] orNull(long[] a) {
        return a.length == 0 ? null : a;
    }

    static long readVar(DataInputStream in) throws IOException {
        long v = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            v |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift > 63) throw new IOException("bad number");
        }
    }

    /** A sorted set of hashes: the count, the first, then each one's distance from the one before. */
    static long[] readLongs(DataInputStream in) throws IOException {
        int n = (int) readVar(in);
        long[] out = new long[n];
        long prev = 0;
        for (int i = 0; i < n; i++) {
            prev = i == 0 ? in.readLong() : prev + readVar(in);
            out[i] = prev;
        }
        return out;
    }

    static String readString(DataInputStream in) throws IOException {
        int n = (int) readVar(in);
        if (n == 0) return "";
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, UTF8);
    }
}
