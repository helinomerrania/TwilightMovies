package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.NetFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One network rule line ("||ads.example.com^$third-party", "@@||good.com/x.js") into the collection: a whole
 * host, an exception for a whole page, or a rule with a pattern. Regular expressions are left out.
 */
final class NetworkRuleParser {
    private final RuleCollection to;

    NetworkRuleParser(RuleCollection to) {
        this.to = to;
    }

    /** false if the rule was left out */
    boolean parse(String line) {
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
        RuleSpec s = new RuleSpec();
        s.exception = exc;
        if (!RuleOptions.read(opts, s)) return false;
        if (s.badfilter) {
            to.badfilters.add(key(exc, pattern, opts));
            return true;
        }
        if (!normalize(pattern, s)) return false;
        if (exc && s.special != 0) return pageException(s);
        if (s.special != 0 || s.types == 0) return false;
        if (exc) s.flags |= NetFilter.EXCEPTION;
        if (s.host != null && opts.isEmpty()) {
            (exc ? to.allowHosts : to.blockHosts).add(s.host);
            return true;
        }
        to.specs.put(key(exc, pattern, opts), s);
        return true;
    }

    /** @@||host^$document, $elemhide, $generichide: for whole hosts only. */
    private boolean pageException(RuleSpec s) {
        if (s.host == null || !s.domIn.isEmpty() || !s.domOut.isEmpty() || !s.toIn.isEmpty()) return false;
        if ((s.special & RuleSpec.DOC_ALLOW) != 0) {
            to.pageAllowHosts.add(s.host);
            to.noCosmeticHosts.add(s.host);
        }
        if ((s.special & RuleSpec.NO_COSMETIC) != 0) to.noCosmeticHosts.add(s.host);
        if ((s.special & RuleSpec.NO_GENERIC) != 0) to.noGenericHosts.add(s.host);
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
        if (p.startsWith("||") && !p.endsWith("^") && Domains.HOST.matcher(p.substring(2)).matches()) p += "^";
        return (exc ? "@@" : "") + p + "$" + String.join(",", o);
    }

    /** The anchors into flags, the pattern tidied (lower-case unless $match-case), and a whole host found. */
    private static boolean normalize(String pattern, RuleSpec s) {
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
                && Domains.HOST.matcher(p).matches() && !p.startsWith(".") && !p.startsWith("-")) {
            String h = Domains.ascii(p.endsWith("^") ? p.substring(0, p.length() - 1) : p);
            if (h == null || h.isEmpty() || h.endsWith(".")) return false;
            s.host = h;
            p = h + "^";
        }
        s.pattern = p;
        return true;
    }
}
