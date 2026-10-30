package com.twilightcafe.movies.adblock.compiler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One hiding rule line ("example.com##.ad", "##.banner", "site.com#@#.box") into the collection. Kept: plain
 * CSS selectors, :has() too. Left out: scriptlets (##+js), HTML filtering (##^), procedural hiding (#?#,
 * :has-text, :xpath...), CSS injection (#$#), and selectors with braces or comments in them.
 */
final class CosmeticRuleParser {
    private static final Pattern COSMETIC = Pattern.compile("^([^\\s|@#$^]*?)(#@?\\$\\?#|#@?[?$%]?#)(.*)$");
    private static final Pattern PROCEDURAL = Pattern.compile(
            ":(has-text|-abp-[a-z-]+|xpath|upward|remove|style|matches-css(-before|-after)?|min-text-length"
            + "|watch-attr|others|matches-path|matches-attr|matches-prop|matches-media|if|if-not|nth-ancestor"
            + "|remove-attr|remove-class|contains|shadow)\\(");
    private static final Pattern KEY = Pattern.compile("^([.#][A-Za-z_][A-Za-z0-9_-]*)");

    private final RuleCollection to;

    CosmeticRuleParser(RuleCollection to) {
        this.to = to;
    }

    static boolean isCosmetic(String line) {
        return COSMETIC.matcher(line).matches();
    }

    /** false if the rule was left out */
    boolean parse(String line) {
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
                d = Domains.ascii(d);
                if (d == null) return false;
                (not ? neg : pos).add(d);
            }
        }
        if (sep.equals("##")) {
            if (pos.isEmpty()) {
                addGeneric(sel);
                for (String d : neg) to.unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
            } else {
                for (String d : pos) to.hide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
                for (String d : neg) to.unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
            }
        } else if (pos.isEmpty() && neg.isEmpty()) {
            to.genericUnhide.add(sel);
        } else {
            for (String d : pos) to.unhide.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(sel);
        }
        return true;
    }

    static boolean validSelector(String s) {
        if (s.isEmpty() || s.length() > 1000) return false;
        if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0 || s.contains("/*") || s.indexOf('\\') >= 0) return false;
        return !PROCEDURAL.matcher(s).find();
    }

    /** Filed under its first class or id when it starts with one (only pages with it get it), else for every page. */
    private void addGeneric(String sel) {
        Matcher k = KEY.matcher(sel);
        if (sel.indexOf(',') < 0 && k.find()) {
            to.keyed.computeIfAbsent(k.group(1), x -> new LinkedHashSet<>()).add(sel);
        } else {
            to.high.add(sel);
        }
    }
}
