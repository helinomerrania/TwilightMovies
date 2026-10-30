package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.FilterFile;
import com.twilightcafe.movies.adblock.engine.FilterSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * Makes the app's filter file from the filter lists (run on GitHub by tools/filters/build.sh; never on the TV
 * box): reads them (ListReader), finishes the collection, writes filters.bin (FilterFile) and filters.json
 * beside it, reads the file back as the app will, and runs the checks (tools/filters/checks.txt).
 *
 * java ... FilterCompiler --out DIR [--checks FILE] [--created MS] name=format:file ...   (adblock, hosts, psl)
 */
public final class FilterCompiler {
    private final RuleCollection rules = new RuleCollection();
    private final ListReader reader = new ListReader(rules);

    /** Adds one list (format: adblock, hosts or psl). */
    public void add(String name, String format, Reader in) throws IOException {
        reader.read(name, format, in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in, 1 << 16));
    }

    /** The rules of every list added, made into a FilterSet marked as made at created. */
    public FilterSet build(long created) {
        if (rules.pslRules.isEmpty()) throw new IllegalStateException("the public suffix list is needed (name=psl:file)");
        rules.finish();
        return rules.toFilterSet(created);
    }

    int[] stats(String name) {
        return rules.stats.get(name);
    }

    public static void main(String[] args) throws Exception {
        File outDir = null;
        File checks = null;
        long created = System.currentTimeMillis();
        FilterCompiler c = new FilterCompiler();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--out")) {
                outDir = new File(args[++i]);
            } else if (a.equals("--checks")) {
                checks = new File(args[++i]);
            } else if (a.equals("--created")) {
                created = Long.parseLong(args[++i]);   // the same lists then make the same file, byte for byte
            } else {
                int eq = a.indexOf('=');
                int colon = a.indexOf(':', eq + 1);
                if (eq < 0 || colon < 0) throw new IllegalArgumentException("not name=format:file: " + a);
                String name = a.substring(0, eq);
                try (Reader in = new InputStreamReader(new FileInputStream(a.substring(colon + 1)), StandardCharsets.UTF_8)) {
                    c.add(name, a.substring(eq + 1, colon), in);
                }
                int[] st = c.stats(name);
                System.out.printf(Locale.ROOT, "  %-20s %8d rules kept, %7d left out%n", name, st[0], st[1]);
            }
        }
        if (outDir == null) throw new IllegalArgumentException("--out DIR is needed");
        FilterSet set = c.build(created);
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException("can't make " + outDir);
        File bin = new File(outDir, "filters.bin");
        try (OutputStream os = new FileOutputStream(bin)) {
            FilterFile.write(set, os);
        }
        long t0 = System.nanoTime();
        FilterSet back;
        try (FileInputStream in = new FileInputStream(bin)) {
            back = FilterSet.read(in);
        }
        long readMs = (System.nanoTime() - t0) / 1000000;
        byte[] data = Files.readAllBytes(bin.toPath());
        RuleCollection r = c.rules;
        int hosts = r.blockHosts.size();
        int network = r.specs.size() + r.allowHosts.size();
        System.out.printf(Locale.ROOT, "  filters.bin: %,d bytes, %,d rules (%,d blocked hosts, %,d other network rules, "
                + "%,d hiding rules), read back in %d ms%n", data.length, r.ruleCount, hosts, network,
                r.ruleCount - hosts - network, readMs);
        writeJson(new File(outDir, "filters.json"), set, data, hosts);
        if (checks != null) {
            List<String> failed = Checks.run(back, Files.readAllLines(checks.toPath(), StandardCharsets.UTF_8));
            if (!failed.isEmpty()) {
                System.out.println("  CHECKS FAILED:");
                for (String f : failed) System.out.println("    " + f);
                System.exit(2);
            }
            System.out.println("  every check in " + checks.getName() + " passed");
        }
    }

    /** filters.json: what the app reads before downloading filters.bin (its date, size and SHA-256). */
    private static void writeJson(File f, FilterSet set, byte[] data, int hosts) throws Exception {
        StringBuilder sha = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(data)) sha.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write("{\"format\":" + FilterFile.FORMAT + ",\"created\":" + set.created + ",\"size\":" + data.length
                    + ",\"sha256\":\"" + sha + "\",\"rules\":" + set.rules + ",\"hosts\":" + hosts
                    + ",\"lists\":\"" + set.info.replace("\"", "'") + "\"}\n");
        }
    }
}
