package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.CosmeticRules;
import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Hash;
import com.twilightcafe.movies.adblock.engine.Hosts;
import com.twilightcafe.movies.adblock.engine.NetFilter;
import com.twilightcafe.movies.adblock.engine.NetworkRules;
import com.twilightcafe.movies.adblock.engine.PublicSuffix;
import com.twilightcafe.movies.adblock.engine.RuleGroup;
import com.twilightcafe.movies.adblock.engine.UrlPattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every rule read from every list, in the order read (so the same lists always make the same file), then
 * finished ($badfilter applied, generic exceptions taken out) and made into a FilterSet.
 */
final class RuleCollection {
    final Set<String> blockHosts = new LinkedHashSet<>();
    final Set<String> allowHosts = new LinkedHashSet<>();
    final Set<String> pageAllowHosts = new LinkedHashSet<>();
    final Set<String> noCosmeticHosts = new LinkedHashSet<>();
    final Set<String> noGenericHosts = new LinkedHashSet<>();
    final Map<String, RuleSpec> specs = new LinkedHashMap<>();   // by NetworkRuleParser.key
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

    int ruleCount;

    /** $badfilter and generic exceptions applied; the rules counted. */
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

    /** The lists and how many rules each gave ("easylist 72422, ..."). */
    String info() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            if (e.getKey().equals("psl")) continue;   // not rules
            if (b.length() > 0) b.append(", ");
            b.append(e.getKey()).append(' ').append(e.getValue()[0]);
        }
        return b.toString();
    }

    FilterSet toFilterSet(long created) {
        Hosts hosts = new Hosts(new PublicSuffix(RuleSpec.hashes(pslRules), RuleSpec.hashes(pslWild),
                RuleSpec.hashes(pslExceptions)));
        NetworkRules network = new NetworkRules(hosts, RuleSpec.hashes(blockHosts), RuleSpec.hashes(allowHosts),
                RuleSpec.hashes(pageAllowHosts), groups());
        Keyed k = new Keyed();
        for (Map.Entry<String, Set<String>> e : keyed.entrySet()) {
            for (String sel : e.getValue()) k.add(Hash.of(e.getKey()), CosmeticRules.HIDE, sel);
        }
        Keyed sp = new Keyed();
        for (Map.Entry<String, Set<String>> e : hide.entrySet()) {
            for (String sel : e.getValue()) sp.add(Hash.of(e.getKey()), CosmeticRules.HIDE, sel);
        }
        for (Map.Entry<String, Set<String>> e : unhide.entrySet()) {
            for (String sel : e.getValue()) sp.add(Hash.of(e.getKey()), CosmeticRules.UNHIDE, sel);
        }
        k.sort();
        sp.sort();
        CosmeticRules cosmetic = new CosmeticRules(hosts, RuleSpec.hashes(noCosmeticHosts), RuleSpec.hashes(noGenericHosts),
                k.keys(), k.selectors(), high.toArray(new String[0]), sp.keys(), sp.kinds(), sp.selectors());
        return new FilterSet(created, ruleCount, info(), hosts, network, cosmetic);
    }

    /** The network rules into NetworkRules.GROUPS groups: under a host, a word, nothing, or each site of domain=. */
    private RuleGroup[] groups() {
        List<List<Object[]>> groups = new ArrayList<>();
        for (int g = 0; g < NetworkRules.GROUPS; g++) groups.add(new ArrayList<>());
        for (RuleSpec s : specs.values()) {
            NetFilter f = s.toFilter();
            long key;
            int g;
            if (s.host != null) {
                key = Hash.of(s.host);
                g = 0;
            } else {
                key = UrlPattern.indexToken(s.pattern, s.flags);
                g = key != 0 ? 1 : 2;
            }
            if (g == 2 && !s.domIn.isEmpty()) {
                for (String d : s.domIn) groups.get(s.exception ? 7 : 6).add(new Object[]{Hash.of(d), f});
                continue;
            }
            if (s.exception) g += 3;
            groups.get(g).add(new Object[]{key, f});
        }
        RuleGroup[] out = new RuleGroup[NetworkRules.GROUPS];
        for (int g = 0; g < out.length; g++) {
            List<Object[]> list = groups.get(g);
            list.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));   // stable: the lists' order kept
            long[] keys = new long[list.size()];
            NetFilter[] filters = new NetFilter[list.size()];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = (Long) list.get(i)[0];
                filters[i] = (NetFilter) list.get(i)[1];
            }
            out[g] = new RuleGroup(keys, filters);
        }
        return out;
    }

    /** Hiding rules under a key, sorted by it (stable). */
    private static final class Keyed {
        private final List<Object[]> entries = new ArrayList<>();

        void add(long key, byte kind, String sel) {
            entries.add(new Object[]{key, kind, sel});
        }

        void sort() {
            entries.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
        }

        long[] keys() {
            long[] k = new long[entries.size()];
            for (int i = 0; i < k.length; i++) k[i] = (Long) entries.get(i)[0];
            return k;
        }

        byte[] kinds() {
            byte[] k = new byte[entries.size()];
            for (int i = 0; i < k.length; i++) k[i] = (Byte) entries.get(i)[1];
            return k;
        }

        String[] selectors() {
            String[] s = new String[entries.size()];
            for (int i = 0; i < s.length; i++) s[i] = (String) entries.get(i)[2];
            return s;
        }
    }
}
