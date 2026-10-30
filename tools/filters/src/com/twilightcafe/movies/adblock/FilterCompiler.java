package com.twilightcafe.movies.adblock;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Makes the app's filter file from the filter lists (run on GitHub by tools/filters/build.sh; never on the TV
 * box). It reads Adblock Plus / uBlock Origin lists, hosts lists and the public suffix list, keeps every rule
 * the app can apply (network rules with their options, exceptions, $badfilter, ad-hiding rules) and leaves out
 * the ones it can't (scriptlets, HTML filtering, procedural hiding, regular expressions, $csp, $removeparam...),
 * then writes the compact file FilterSet reads, a .json beside it, and checks the result (tools/filters/checks.txt).
 *
 * java ... FilterCompiler --out DIR [--checks FILE] name=format:file ...   (format: adblock, hosts or psl)
 */
public final class FilterCompiler {

    // ------------------------------------------------------------------ what's collected

    static final class Spec {
        boolean exception;
        boolean badfilter;
        int flags;
        int types;
        int special;            // DOC_ALLOW, NO_COSMETIC, NO_GENERIC (for exceptions)
        String pattern;
        String host;            // for ||host^ with options
        String redirect;
        final Set<String> domIn = new LinkedHashSet<>();
        final Set<String> domOut = new LinkedHashSet<>();
        final Set<String> toIn = new LinkedHashSet<>();
        final Set<String> toOut = new LinkedHashSet<>();
        final Set<String> deny = new LinkedHashSet<>();
    }

    static final int DOC_ALLOW = 1;
    static final int NO_COSMETIC = 2;
    static final int NO_GENERIC = 4;
    private static final int POPUP = 1 << 20;   // popup rules: the app blocks pop-ups to other sites by itself

    final Set<String> blockHosts = new LinkedHashSet<>();
    final Set<String> allowHosts = new LinkedHashSet<>();
    final Set<String> pageAllowHosts = new LinkedHashSet<>();
    final Set<String> noCosmeticHosts = new LinkedHashSet<>();
    final Set<String> noGenericHosts = new LinkedHashSet<>();
    final Map<String, Spec> specs = new LinkedHashMap<>();
    final Set<String> badfilters = new HashSet<>();
    final Map<String, Set<String>> keyed = new LinkedHashMap<>();
    final Set<String> high = new LinkedHashSet<>();
    final Map<String, Set<String>> hide = new LinkedHashMap<>();
    final Map<String, Set<String>> unhide = new LinkedHashMap<>();
    final Set<String> genericUnhide = new HashSet<>();
    final Set<String> pslRules = new LinkedHashSet<>();
    final Set<String> pslWild = new LinkedHashSet<>();
    final Set<String> pslExceptions = new LinkedHashSet<>();
    final Map<String, int[]> stats = new LinkedHashMap<>();   // list -> kept, left out

    /** What the app is, for "!#if" in the lists: a Chromium web engine; uBlock Origin's syntax. */
    static final Set<String> ENV = new HashSet<>(Arrays.asList("env_chromium", "ext_ublock"));

    private static final Pattern COSMETIC = Pattern.compile("^([^\\s|@#$^]*?)(#@?\\$\\?#|#@?[?$%]?#)(.*)$");
    private static final Pattern PROCEDURAL = Pattern.compile(
            ":(has-text|-abp-[a-z-]+|xpath|upward|remove|style|matches-css(-before|-after)?|min-text-length"
            + "|watch-attr|others|matches-path|matches-attr|matches-prop|matches-media|if|if-not|nth-ancestor"
            + "|remove-attr|remove-class|contains|shadow)\\(");
    private static final Pattern KEY = Pattern.compile("^([.#][A-Za-z_][A-Za-z0-9_-]*)");
    private static final Pattern HOST = Pattern.compile("^[a-z0-9_][a-z0-9_.-]*\\^?$");
    private static final Map<String, Integer> TYPES = new HashMap<>();

    static {
        TYPES.put("script", Request.SCRIPT);
        TYPES.put("image", Request.IMAGE);
        TYPES.put("stylesheet", Request.STYLESHEET);
        TYPES.put("css", Request.STYLESHEET);
        TYPES.put("object", Request.OBJECT);
        TYPES.put("object-subrequest", Request.OBJECT);
        TYPES.put("xmlhttprequest", Request.XHR);
        TYPES.put("xhr", Request.XHR);
        TYPES.put("subdocument", Request.SUBDOCUMENT);
        TYPES.put("frame", Request.SUBDOCUMENT);
        TYPES.put("ping", Request.PING);
        TYPES.put("beacon", Request.PING);
        TYPES.put("media", Request.MEDIA);
        TYPES.put("font", Request.FONT);
        TYPES.put("other", Request.OTHER);
        TYPES.put("websocket", Request.WEBSOCKET);
    }

    // ------------------------------------------------------------------ reading lists

    void addList(String name, String format, BufferedReader in) throws IOException {
        int[] st = stats.computeIfAbsent(name, k -> new int[2]);
        switch (format) {
            case "adblock":
                adblock(in, st);
                break;
            case "hosts":
                hosts(in, st);
                break;
            case "psl":
                psl(in, st);
                break;
            default:
                throw new IOException("unknown list format " + format);
        }
    }

    private void adblock(BufferedReader in, int[] st) throws IOException {
        Deque<boolean[]> conditions = new ArrayDeque<>();   // {parent active, condition}
        boolean active = true;
        String raw;
        while ((raw = in.readLine()) != null) {
            String line = raw.trim();
            if (line.startsWith("!#if")) {
                boolean c = Condition.eval(line.substring(4).trim(), ENV);
                conditions.push(new boolean[]{active, c});
                active = active && c;
                continue;
            }
            if (line.startsWith("!#else")) {
                boolean[] top = conditions.peek();
                if (top != null) active = top[0] && !top[1];
                continue;
            }
            if (line.startsWith("!#endif")) {
                boolean[] top = conditions.poll();
                if (top != null) active = top[0];
                continue;
            }
            if (!active || line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue;
            if (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#")) continue;   // a comment
            boolean kept = line.startsWith("@@") || !isCosmetic(line) ? network(line) : cosmetic(line);
            st[kept ? 0 : 1]++;
        }
    }

    private void hosts(BufferedReader in, int[] st) throws IOException {
        String raw;
        while ((raw = in.readLine()) != null) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            String[] f = line.split("\\s+");
            String h = f.length >= 2 ? f[1] : f[0];
            h = ascii(h.toLowerCase(Locale.ROOT));
            if (h == null || h.equals("localhost") || h.equals("0.0.0.0") || h.equals("broadcasthost")
                    || !HOST.matcher(h).matches() || h.endsWith("^")) {
                st[1]++;
                continue;
            }
            blockHosts.add(h);
            st[0]++;
        }
    }

    private void psl(BufferedReader in, int[] st) throws IOException {
        String raw;
        while ((raw = in.readLine()) != null) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            line = line.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (line.startsWith("!")) {
                String h = ascii(line.substring(1));
                if (h != null) pslExceptions.add(h);
            } else if (line.startsWith("*.")) {
                String h = ascii(line.substring(2));
                if (h != null) pslWild.add(h);
            } else {
                String h = ascii(line);
                if (h != null) pslRules.add(h);
            }
            st[0]++;
        }
    }

    // ------------------------------------------------------------------ hiding rules

    static boolean isCosmetic(String line) {
        return COSMETIC.matcher(line).matches();
    }

    private boolean cosmetic(String line) {
        Matcher m = COSMETIC.matcher(line);
        if (!m.matches()) return false;
        String domains = m.group(1);
        String sep = m.group(2);
        String sel = m.group(3).trim();
        if (!sep.equals("##") && !sep.equals("#@#")) return false;   // #?# #$# #%#: procedural, CSS, scripts
        if (sel.startsWith("+js(") || sel.startsWith("^")) return false;   // scriptlets, HTML filtering
        if (!validSelector(sel)) return false;
        List<String> pos = new ArrayList<>();
        List<String> neg = new ArrayList<>();
        if (!domains.isEmpty()) {
            for (String d : domains.split(",")) {
                d = d.trim().toLowerCase(Locale.ROOT);
                if (d.isEmpty()) continue;
                boolean not = d.startsWith("~");
                if (not) d = d.substring(1);
                if (d.startsWith("/")) return false;   // regular-expression sites
                d = ascii(d);
                if (d == null) return false;
                (not ? neg : pos).add(d);
            }
        }
        if (sep.equals("##")) {
            if (pos.isEmpty()) {
                addGeneric(sel);
                for (String d : neg) unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
            } else {
                for (String d : pos) hide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
                for (String d : neg) unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
            }
        } else if (pos.isEmpty() && neg.isEmpty()) {
            genericUnhide.add(sel);
        } else {
            for (String d : pos) unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
        }
        return true;
    }

    static boolean validSelector(String s) {
        if (s.isEmpty() || s.length() > 1000) return false;
        if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0 || s.contains("/*") || s.indexOf('\\') >= 0) return false;
        return !PROCEDURAL.matcher(s).find();
    }

    private void addGeneric(String sel) {
        Matcher k = KEY.matcher(sel);
        if (sel.indexOf(',') < 0 && k.find()) {
            keyed.computeIfAbsent(k.group(1), x -> new LinkedHashSet<>()).add(sel);
        } else {
            high.add(sel);
        }
    }

    // ------------------------------------------------------------------ network rules

    private boolean network(String line) {
        boolean exc = line.startsWith("@@");
        String rule = exc ? line.substring(2) : line;
        if (rule.isEmpty() || rule.indexOf(' ') >= 0 || rule.indexOf('\t') >= 0) return false;
        String pattern = rule;
        String opts = "";
        int d = rule.lastIndexOf('$');
        if (d >= 0) {
            pattern = rule.substring(0, d);
            opts = rule.substring(d + 1);
        }
        if (pattern.length() >= 2 && pattern.startsWith("/") && pattern.endsWith("/")) return false;   // a regular expression
        Spec s = new Spec();
        s.exception = exc;
        if (!options(opts, s)) return false;
        if (s.badfilter) {
            badfilters.add(key(exc, pattern, opts));
            return true;
        }
        if (!normalize(pattern, s)) return false;
        if (exc && s.special != 0) {
            if (s.host == null || !s.domIn.isEmpty() || !s.domOut.isEmpty() || !s.toIn.isEmpty()) return false;
            if ((s.special & DOC_ALLOW) != 0) {
                pageAllowHosts.add(s.host);
                noCosmeticHosts.add(s.host);
            }
            if ((s.special & NO_COSMETIC) != 0) noCosmeticHosts.add(s.host);
            if ((s.special & NO_GENERIC) != 0) noGenericHosts.add(s.host);
            return true;
        }
        if (s.special != 0 || s.types == 0) return false;
        if (exc) s.flags |= NetFilter.EXCEPTION;
        boolean plain = opts.isEmpty();
        if (s.host != null && plain) {
            (exc ? allowHosts : blockHosts).add(s.host);
            return true;
        }
        specs.put(key(exc, pattern, opts), s);
        return true;
    }

    /** The same rule always gets the same key, whatever the order of its options: for $badfilter. */
    static String key(boolean exc, String pattern, String opts) {
        List<String> o = new ArrayList<>();
        for (String x : opts.toLowerCase(Locale.ROOT).split(",")) {
            if (!x.isEmpty() && !x.equals("badfilter")) o.add(x);
        }
        Collections.sort(o);
        String p = pattern.toLowerCase(Locale.ROOT);
        if (p.startsWith("||") && !p.endsWith("^") && HOST.matcher(p.substring(2)).matches()) p += "^";
        return (exc ? "@@" : "") + p + "$" + String.join(",", o);
    }

    private boolean options(String opts, Spec s) {
        int pos = 0;
        int neg = 0;
        if (!opts.isEmpty()) {
            for (String o : opts.split(",")) {
                if (o.isEmpty()) return false;
                boolean not = o.startsWith("~");
                String name = not ? o.substring(1) : o;
                String value = null;
                int eq = name.indexOf('=');
                if (eq >= 0) {
                    value = name.substring(eq + 1);
                    name = name.substring(0, eq);
                }
                name = name.toLowerCase(Locale.ROOT);
                Integer t = TYPES.get(name);
                if (t != null) {
                    if (not) neg |= t;
                    else pos |= t;
                    continue;
                }
                switch (name) {
                    case "third-party":
                    case "3p":
                        s.flags |= not ? NetFilter.FIRST_PARTY : NetFilter.THIRD_PARTY;
                        break;
                    case "first-party":
                    case "1p":
                        s.flags |= not ? NetFilter.THIRD_PARTY : NetFilter.FIRST_PARTY;
                        break;
                    case "strict1p":
                        s.flags |= NetFilter.FIRST_PARTY;
                        break;
                    case "strict3p":
                        s.flags |= NetFilter.THIRD_PARTY;
                        break;
                    case "match-case":
                        s.flags |= NetFilter.MATCH_CASE;
                        break;
                    case "important":
                        if (!s.exception) s.flags |= NetFilter.IMPORTANT;
                        break;
                    case "domain":
                    case "from":
                        if (!domains(value, s.domIn, s.domOut)) return false;
                        break;
                    case "to":
                        if (!domains(value, s.toIn, s.toOut)) return false;
                        break;
                    case "denyallow":
                        if (!domains(value, s.deny, null)) return false;
                        break;
                    case "redirect":
                        if (value == null || value.isEmpty()) return false;
                        s.redirect = value;
                        break;
                    case "empty":
                        s.redirect = "empty";
                        break;
                    case "mp4":
                        s.redirect = "noopmp4-1s";
                        pos |= Request.MEDIA;
                        break;
                    case "badfilter":
                        s.badfilter = true;
                        break;
                    case "document":
                    case "doc":
                        if (s.exception) s.special |= DOC_ALLOW;
                        else pos |= Request.DOCUMENT;
                        break;
                    case "elemhide":
                    case "ehide":
                        s.special |= NO_COSMETIC;
                        break;
                    case "generichide":
                    case "ghide":
                        s.special |= NO_GENERIC;
                        break;
                    case "popup":
                        if (!not) pos |= POPUP;
                        break;
                    case "all":
                        pos |= Request.ALL | POPUP;
                        break;
                    case "reason":
                        break;   // a note for people
                    default:
                        // redirect-rule, csp, removeparam, header, replace, method, cname, popunder, urlskip,
                        // specifichide, genericblock, inline-script... : not something this app can do
                        return false;
                }
            }
        }
        if (s.badfilter) return true;
        if (pos == POPUP) return s.special != 0;   // only for pop-ups
        pos &= ~POPUP;
        int base = pos == 0 ? Request.DEFAULT : pos;
        s.types = base & ~neg;
        if (s.special != 0 && pos == 0 && neg == 0) s.types = 0;
        return s.types != 0 || s.special != 0;
    }

    private static boolean domains(String value, Set<String> in, Set<String> out) {
        if (value == null || value.isEmpty()) return false;
        for (String d : value.split("\\|")) {
            d = d.trim().toLowerCase(Locale.ROOT);
            if (d.isEmpty()) continue;
            boolean not = d.startsWith("~");
            if (not) d = d.substring(1);
            if (d.startsWith("/")) return false;   // regular expressions
            d = ascii(d);
            if (d == null) return false;
            if (not) {
                if (out == null) return false;
                out.add(d);
            } else {
                in.add(d);
            }
        }
        return true;
    }

    private static boolean normalize(String pattern, Spec s) {
        String p = pattern;
        if (p.startsWith("||")) {
            s.flags |= NetFilter.HOST_ANCHOR;
            p = p.substring(2);
        } else if (p.startsWith("|")) {
            s.flags |= NetFilter.START_ANCHOR;
            p = p.substring(1);
        }
        if (p.endsWith("|")) {
            s.flags |= NetFilter.END_ANCHOR;
            p = p.substring(0, p.length() - 1);
        }
        if ((s.flags & NetFilter.MATCH_CASE) == 0) p = p.toLowerCase(Locale.ROOT);
        while (p.contains("**")) p = p.replace("**", "*");
        if (p.startsWith("*")) {
            p = p.substring(1);
            s.flags &= ~(NetFilter.HOST_ANCHOR | NetFilter.START_ANCHOR);
        }
        if (p.endsWith("*")) {
            p = p.substring(0, p.length() - 1);
            s.flags &= ~NetFilter.END_ANCHOR;
        }
        if (p.isEmpty() || p.equals("^")) {
            // every address: only with options that narrow it down to some pages, hosts or parties
            boolean narrowed = !s.domIn.isEmpty() || !s.toIn.isEmpty()
                    || (s.flags & (NetFilter.THIRD_PARTY | NetFilter.FIRST_PARTY)) != 0 && !s.domOut.isEmpty();
            if (!narrowed && s.special == 0) return false;
            p = "";
            s.flags &= ~(NetFilter.HOST_ANCHOR | NetFilter.START_ANCHOR | NetFilter.END_ANCHOR);
        }
        if ((s.flags & NetFilter.HOST_ANCHOR) != 0 && (s.flags & NetFilter.END_ANCHOR) == 0
                && HOST.matcher(p).matches() && !p.startsWith(".") && !p.startsWith("-")) {
            String h = ascii(p.endsWith("^") ? p.substring(0, p.length() - 1) : p);
            if (h == null || h.isEmpty() || h.endsWith(".")) return false;
            s.host = h;
            p = h + "^";
        }
        s.pattern = p;
        return true;
    }

    /** A host in ASCII (punycode), lower-case; "example.*" stays an entity. Null if it can't be one. */
    static String ascii(String h) {
        if (h == null || h.isEmpty()) return null;
        try {
            if (h.endsWith(".*")) return ascii(h.substring(0, h.length() - 2)) + ".*";
            boolean plain = true;
            for (int i = 0; i < h.length(); i++) {
                if (h.charAt(i) >= 128) {
                    plain = false;
                    break;
                }
            }
            return plain ? h : IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ writing the file

    int ruleCount;

    void finish() {
        specs.keySet().removeIf(badfilters::contains);
        blockHosts.removeIf(h -> badfilters.contains("||" + h + "^$"));
        allowHosts.removeIf(h -> badfilters.contains("@@||" + h + "^$"));
        for (Set<String> sels : keyed.values()) sels.removeAll(genericUnhide);
        keyed.values().removeIf(Set::isEmpty);
        high.removeAll(genericUnhide);
        int cos = high.size();
        for (Set<String> v : keyed.values()) cos += v.size();
        for (Set<String> v : hide.values()) cos += v.size();
        for (Set<String> v : unhide.values()) cos += v.size();
        ruleCount = blockHosts.size() + allowHosts.size() + pageAllowHosts.size() + noCosmeticHosts.size()
                + noGenericHosts.size() + specs.size() + cos;
    }

    String info() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            if (e.getKey().equals("psl")) continue;   // not rules
            if (b.length() > 0) b.append(", ");
            b.append(e.getKey()).append(' ').append(e.getValue()[0]);
        }
        return b.toString();
    }

    void write(OutputStream os, long created) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(os, 65536), 65536));
        out.writeInt(FilterSet.MAGIC);
        out.writeInt(FilterSet.FORMAT);
        out.writeLong(created);
        out.writeInt(ruleCount);
        writeString(out, info());
        for (Set<String> set : Arrays.asList(blockHosts, allowHosts, pageAllowHosts, noCosmeticHosts, noGenericHosts,
                pslRules, pslWild, pslExceptions)) {
            writeLongs(out, hashes(set));
        }
        // FilterSet.GROUPS: under a host, a word or nothing (blocks, then exceptions), then under the page's site
        List<List<Object[]>> groups = new ArrayList<>();
        for (int g = 0; g < FilterSet.GROUPS; g++) groups.add(new ArrayList<>());
        for (Spec s : specs.values()) {
            long key = 0;
            int g;
            if (s.host != null) {
                key = Hash.of(s.host);
                g = 0;
            } else {
                key = NetFilter.indexToken(s.pattern, s.flags);
                g = key != 0 ? 1 : 2;
            }
            if (g == 2 && !s.domIn.isEmpty()) {
                // no word to file it under, but only for some sites: filed under each of them
                for (String d : s.domIn) groups.get(s.exception ? 7 : 6).add(new Object[]{Hash.of(d), s});
                continue;
            }
            if (s.exception) g += 3;
            groups.get(g).add(new Object[]{key, s});
        }
        for (List<Object[]> group : groups) {
            group.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            writeVar(out, group.size());
            for (Object[] e : group) {
                Spec s = (Spec) e[1];
                out.writeLong((Long) e[0]);
                writeVar(out, s.flags);
                writeVar(out, s.types);
                writeString(out, s.pattern);
                writeString(out, s.redirect == null ? "" : s.redirect);
                writeLongs(out, hashes(s.domIn));
                writeLongs(out, hashes(s.domOut));
                writeLongs(out, hashes(s.toIn));
                writeLongs(out, hashes(s.toOut));
                writeLongs(out, hashes(s.deny));
            }
        }
        List<Object[]> k = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : keyed.entrySet()) {
            long h = Hash.of(e.getKey());
            for (String sel : e.getValue()) k.add(new Object[]{h, sel});
        }
        k.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
        writeVar(out, k.size());
        for (Object[] e : k) {
            out.writeLong((Long) e[0]);
            writeString(out, (String) e[1]);
        }
        writeVar(out, high.size());
        for (String sel : high) writeString(out, sel);
        List<Object[]> sp = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : hide.entrySet()) {
            long h = Hash.of(e.getKey());
            for (String sel : e.getValue()) sp.add(new Object[]{h, FilterSet.HIDE, sel});
        }
        for (Map.Entry<String, Set<String>> e : unhide.entrySet()) {
            long h = Hash.of(e.getKey());
            for (String sel : e.getValue()) sp.add(new Object[]{h, FilterSet.UNHIDE, sel});
        }
        sp.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
        writeVar(out, sp.size());
        for (Object[] e : sp) {
            out.writeLong((Long) e[0]);
            out.writeByte((Byte) e[1]);
            writeString(out, (String) e[2]);
        }
        out.writeInt(FilterSet.END);
        out.close();
    }

    private static long[] hashes(Set<String> set) {
        long[] a = new long[set.size()];
        int i = 0;
        for (String s : set) a[i++] = Hash.of(s);
        Arrays.sort(a);
        int n = 0;
        for (int j = 0; j < a.length; j++) {
            if (j == 0 || a[j] != a[j - 1]) a[n++] = a[j];
        }
        return Arrays.copyOf(a, n);
    }

    static void writeVar(DataOutputStream out, long v) throws IOException {
        while ((v & ~0x7fL) != 0) {
            out.writeByte((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    static void writeLongs(DataOutputStream out, long[] sorted) throws IOException {
        writeVar(out, sorted.length);
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0) out.writeLong(sorted[0]);
            else writeVar(out, sorted[i] - sorted[i - 1]);
        }
    }

    static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVar(out, b.length);
        out.write(b);
    }

    // ------------------------------------------------------------------ checks on the result

    /**
     * Each line of checks.txt: "block|allow TYPE ADDRESS [PAGE]" (TYPE: script, image, stylesheet, xhr, frame,
     * media, font, page, other, unknown; PAGE: the host the page is on, else the address's own), or
     * "hide|show HOST SELECTOR" for the hiding rules on a site. Returns the failures.
     */
    static List<String> check(FilterSet set, List<String> lines) {
        List<String> failed = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+", 4);
            if (f[0].equals("hide") || f[0].equals("show")) {
                String css = set.pageCss(f[1]);
                boolean hidden = css.contains(f[2] + "{");
                if (hidden != f[0].equals("hide")) failed.add(line);
                continue;
            }
            int type = typeOf(f[1]);
            String page = f.length >= 4 ? f[3] : Request.hostOf(f[2]);
            boolean blocked = set.match(new Request(f[2], page, type));
            if (blocked != f[0].equals("block")) failed.add(line);
        }
        return failed;
    }

    static int typeOf(String t) {
        switch (t) {
            case "script": return Request.SCRIPT;
            case "image": return Request.IMAGE;
            case "stylesheet": return Request.STYLESHEET;
            case "xhr": return Request.XHR;
            case "frame": return Request.SUBDOCUMENT;
            case "media": return Request.MEDIA;
            case "font": return Request.FONT;
            case "page": return Request.DOCUMENT;
            case "other": return Request.OTHER;
            default: return Request.UNKNOWN;
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        File outDir = null;
        File checks = null;
        FilterCompiler c = new FilterCompiler();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--out")) {
                outDir = new File(args[++i]);
            } else if (a.equals("--checks")) {
                checks = new File(args[++i]);
            } else {
                int eq = a.indexOf('=');
                int colon = a.indexOf(':', eq + 1);
                if (eq < 0 || colon < 0) throw new IllegalArgumentException("not name=format:file: " + a);
                String name = a.substring(0, eq);
                String format = a.substring(eq + 1, colon);
                File f = new File(a.substring(colon + 1));
                try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
                    c.addList(name, format, in);
                }
                int[] st = c.stats.get(name);
                System.out.printf(Locale.ROOT, "  %-20s %8d rules kept, %7d left out%n", name, st[0], st[1]);
                names.add(name);
            }
        }
        if (outDir == null) throw new IllegalArgumentException("--out DIR is needed");
        if (c.pslRules.isEmpty()) throw new IllegalStateException("the public suffix list is needed (name=psl:file)");
        c.finish();
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException("can't make " + outDir);
        File bin = new File(outDir, "filters.bin");
        long created = System.currentTimeMillis();
        try (OutputStream os = new FileOutputStream(bin)) {
            c.write(os, created);
        }
        long t0 = System.nanoTime();
        FilterSet set;
        try (FileInputStream in = new FileInputStream(bin)) {
            set = FilterSet.read(in);
        }
        long readMs = (System.nanoTime() - t0) / 1000000;
        byte[] data = Files.readAllBytes(bin.toPath());
        String sha = hex(MessageDigest.getInstance("SHA-256").digest(data));
        System.out.printf(Locale.ROOT, "  filters.bin: %,d bytes, %,d rules (%,d blocked hosts, %,d other network rules, "
                        + "%,d hiding rules), read back in %d ms%n", data.length, c.ruleCount, c.blockHosts.size(),
                c.specs.size() + c.allowHosts.size(), c.ruleCount - c.blockHosts.size() - c.specs.size() - c.allowHosts.size(),
                readMs);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(outDir, "filters.json")), StandardCharsets.UTF_8)) {
            w.write("{\"format\":" + FilterSet.FORMAT + ",\"created\":" + created + ",\"size\":" + data.length
                    + ",\"sha256\":\"" + sha + "\",\"rules\":" + c.ruleCount + ",\"hosts\":" + c.blockHosts.size()
                    + ",\"lists\":\"" + c.info().replace("\"", "'") + "\"}\n");
        }
        if (checks != null) {
            List<String> failed = check(set, Files.readAllLines(checks.toPath(), StandardCharsets.UTF_8));
            if (!failed.isEmpty()) {
                System.out.println("  CHECKS FAILED:");
                for (String f : failed) System.out.println("    " + f);
                System.exit(2);
            }
            System.out.println("  every check in " + checks.getName() + " passed");
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return s.toString();
    }

    // ------------------------------------------------------------------ "!#if" conditions

    /** The conditions of "!#if" lines: names, !, &&, || and brackets. Names not in env are false. */
    static final class Condition {
        private final String s;
        private final Set<String> env;
        private int i;

        private Condition(String s, Set<String> env) {
            this.s = s;
            this.env = env;
        }

        static boolean eval(String expr, Set<String> env) {
            Condition c = new Condition(expr, env);
            boolean v = c.or();
            return v;
        }

        private void space() {
            while (i < s.length() && s.charAt(i) == ' ') i++;
        }

        private boolean or() {
            boolean v = and();
            space();
            while (s.startsWith("||", i)) {
                i += 2;
                boolean r = and();
                v = v || r;
                space();
            }
            return v;
        }

        private boolean and() {
            boolean v = not();
            space();
            while (s.startsWith("&&", i)) {
                i += 2;
                boolean r = not();
                v = v && r;
                space();
            }
            return v;
        }

        private boolean not() {
            space();
            if (i < s.length() && s.charAt(i) == '!') {
                i++;
                return !not();
            }
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                boolean v = or();
                space();
                if (i < s.length() && s.charAt(i) == ')') i++;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
            return env.contains(s.substring(start, i));
        }
    }
}
