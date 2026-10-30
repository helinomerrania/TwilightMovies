package com.twilightcafe.movies.adblock.engine;

import com.twilightcafe.movies.adblock.compiler.FilterCompiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;

/**
 * For the ad blocker's tests: small lists made into a filter file by the compiler (tools/filters), and read back
 * exactly as the app reads its own. A list that starts "#hosts" is a hosts list; the others Adblock Plus rules.
 */
public final class Lists {
    /** A few rules of the real public suffix list. */
    static final String PSL = String.join("\n",
            "// a few rules of the real list", "com", "net", "org", "uk", "co.uk", "iq", "com.iq", "krd",
            "*.ck", "!www.ck", "blogspot.com", "github.io", "io");

    private Lists() {
    }

    public static FilterSet compile(String... lists) throws Exception {
        return FilterSet.read(new ByteArrayInputStream(file(lists)));
    }

    /** The filter file the lists make (gzip), marked as made at a fixed time. */
    public static byte[] file(String... lists) throws Exception {
        FilterCompiler c = new FilterCompiler();
        c.add("psl", "psl", new StringReader(PSL));
        int i = 0;
        for (String l : lists) c.add("list" + (i++), l.startsWith("#hosts\n") ? "hosts" : "adblock", new StringReader(l));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FilterFile.write(c.build(1234567890L), out);
        return out.toByteArray();
    }

    public static boolean blocks(FilterSet f, String url, String page, int type) {
        return f.match(new Request(url, page, type));
    }
}
