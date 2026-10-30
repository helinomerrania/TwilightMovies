package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Lists;
import com.twilightcafe.movies.adblock.engine.Request;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static com.twilightcafe.movies.adblock.engine.Lists.blocks;
import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;

/**
 * Reading the lists ("!#if" lines, $badfilter, comments, rules left out) and the filter file itself: the same
 * lists always make the same file, and the app refuses a file that isn't one or is cut short.
 */
public final class CompilerTest {
    private CompilerTest() {
    }

    public static void run() throws Exception {
        conditionsAndBadfilter();
        theFile();
        checks();
    }

    static void conditionsAndBadfilter() throws Exception {
        FilterSet f = Lists.compile(String.join("\n",
                "!#if env_mobile",
                "||mobile-only.com^",
                "!#else",
                "||not-mobile.com^",
                "!#endif",
                "!#if !ext_ubol && env_chromium",
                "||chromium.com^",
                "!#endif",
                "!#if (env_firefox || env_safari)",
                "||firefox.com^",
                "!#endif",
                "||broken.com^",
                "||cdn.lib.com^$script,third-party",
                "||regex.com/x/$script"),
                String.join("\n",
                        "||broken.com^$badfilter",
                        "||cdn.lib.com^$third-party,script,badfilter",
                        "/ads[0-9]+/$script",
                        "! a comment",
                        "[Adblock Plus 2.0]"));
        eq("!#if env_mobile: not this app", blocks(f, "https://mobile-only.com/", "a.com", Request.SCRIPT), false);
        eq("!#else", blocks(f, "https://not-mobile.com/", "a.com", Request.SCRIPT), true);
        eq("!#if with && and !", blocks(f, "https://chromium.com/", "a.com", Request.SCRIPT), true);
        eq("!#if with ( || )", blocks(f, "https://firefox.com/", "a.com", Request.SCRIPT), false);
        eq("$badfilter cancels a rule in another list", blocks(f, "https://broken.com/", "a.com", Request.SCRIPT), false);
        eq("...with its options in another order", blocks(f, "https://cdn.lib.com/x.js", "a.com", Request.SCRIPT), false);
        eq("regular expressions are left out", blocks(f, "https://a.com/ads12/x.js", "b.com", Request.SCRIPT), false);
        eq("the other rules stay", blocks(f, "https://regex.com/x/1.js", "a.com", Request.SCRIPT), true);
    }

    static void theFile() throws Exception {
        String[] lists = {"||ads.example.com^\n##.ad-box\n/banner/*/img^$image", "#hosts\n0.0.0.0 bad.org"};
        byte[] one = Lists.file(lists);
        byte[] two = Lists.file(lists);
        ok("the same lists make the same file, byte for byte", Arrays.equals(one, two));
        FilterSet f = FilterSet.read(new ByteArrayInputStream(one));
        eq("when it was made is kept", f.created, 1234567890L);
        eq("...and read alone, from the first bytes", FilterSet.readCreated(new ByteArrayInputStream(one)), 1234567890L);
        eq("how many rules", f.rules, 4);
        eq("which lists, and how many from each", f.info, "list0 3, list1 1");
        eq("its blocked hosts", f.blockedHostCount(), 2);
        byte[] plain = new GZIPInputStream(new ByteArrayInputStream(one)).readAllBytes();
        eq("a file that isn't gzip reads too", FilterSet.read(new ByteArrayInputStream(plain)).rules, 4);
        ok("a file cut short is refused", refused(Arrays.copyOf(plain, plain.length - 6)));
        ok("a file that isn't one is refused", refused("not a filter file at all".getBytes("UTF-8")));
        byte[] newer = plain.clone();
        newer[7] = 99;   // the format, for a newer app
        ok("a file in a newer format is refused", refused(newer));
    }

    private static boolean refused(byte[] data) {
        try {
            FilterSet.read(new ByteArrayInputStream(data));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static void checks() throws Exception {
        FilterSet f = Lists.compile("||ads.example.com^\nnews.com##.promo");
        eq("checks.txt lines that hold", Checks.run(f, Arrays.asList("# a comment", "",
                "block script https://ads.example.com/x.js site.com", "allow script https://news.com/app.js",
                "hide news.com .promo", "show blog.com .promo")).size(), 0);
        eq("checks.txt lines that don't", Checks.run(f, Arrays.asList("allow script https://ads.example.com/x.js",
                "hide blog.com .promo")).size(), 2);
    }
}
