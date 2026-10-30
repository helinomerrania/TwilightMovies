package com.twilightcafe.movies.adblock.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Locale;

/**
 * Reads one list into the collection, by its format: "adblock" (Adblock Plus / uBlock Origin rules, with
 * "!#if" lines), "hosts" (a host a line, maybe after 0.0.0.0 or 127.0.0.1) or "psl" (the public suffix list).
 */
final class ListReader {
    private final RuleCollection to;
    private final NetworkRuleParser network;
    private final CosmeticRuleParser cosmetic;

    ListReader(RuleCollection to) {
        this.to = to;
        this.network = new NetworkRuleParser(to);
        this.cosmetic = new CosmeticRuleParser(to);
    }

    void read(String name, String format, BufferedReader in) throws IOException {
        int[] st = to.stats.computeIfAbsent(name, k -> new int[2]);
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
        Preprocessor pre = new Preprocessor();
        String raw;
        while ((raw = in.readLine()) != null) {
            String line = raw.trim();
            if (pre.directive(line)) continue;
            if (!pre.active() || line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue;
            if (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#")) continue;   // a comment
            boolean kept = line.startsWith("@@") || !CosmeticRuleParser.isCosmetic(line)
                    ? network.parse(line) : cosmetic.parse(line);
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
            String h = Domains.ascii((f.length >= 2 ? f[1] : f[0]).toLowerCase(Locale.ROOT));
            if (h == null || h.equals("localhost") || h.equals("0.0.0.0") || h.equals("broadcasthost")
                    || !Domains.HOST.matcher(h).matches() || h.endsWith("^")) {
                st[1]++;
                continue;
            }
            to.blockHosts.add(h);
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
                String h = Domains.ascii(line.substring(1));
                if (h != null) to.pslExceptions.add(h);
            } else if (line.startsWith("*.")) {
                String h = Domains.ascii(line.substring(2));
                if (h != null) to.pslWild.add(h);
            } else {
                String h = Domains.ascii(line);
                if (h != null) to.pslRules.add(h);
            }
            st[0]++;
        }
    }
}
