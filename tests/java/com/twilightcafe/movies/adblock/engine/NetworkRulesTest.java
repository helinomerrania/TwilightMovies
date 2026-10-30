package com.twilightcafe.movies.adblock.engine;

import static com.twilightcafe.movies.adblock.engine.Lists.blocks;
import static com.twilightcafe.movies.test.Check.eq;

/** Blocking whole hosts (from rules and hosts lists), and address patterns: wildcards, ^, anchors, match-case. */
public final class NetworkRulesTest {
    private NetworkRulesTest() {
    }

    public static void run() throws Exception {
        hosts();
        patterns();
    }

    static void hosts() throws Exception {
        FilterSet f = Lists.compile("||ads.example.com^\n||tracker.net^", "#hosts\n0.0.0.0 bad.org\n127.0.0.1 evil.co.uk # x\nplain.io");
        eq("a blocked host", blocks(f, "https://ads.example.com/x.js", "site.com", Request.SCRIPT), true);
        eq("...and its subdomains", blocks(f, "https://a.b.ads.example.com/", "site.com", Request.IMAGE), true);
        eq("...but not the host above it", blocks(f, "https://example.com/ads.js", "site.com", Request.SCRIPT), false);
        eq("...nor a host with a similar name", blocks(f, "https://notads.example.com/", "site.com", Request.SCRIPT), false);
        eq("a port and a query don't matter", blocks(f, "http://tracker.net:8080?a=1", "site.com", Request.XHR), true);
        eq("a hosts list: 0.0.0.0 host", blocks(f, "https://bad.org/", "site.com", Request.SCRIPT), true);
        eq("a hosts list: 127.0.0.1 host # comment", blocks(f, "https://cdn.evil.co.uk/", "site.com", Request.SCRIPT), true);
        eq("a hosts list: a host alone", blocks(f, "https://plain.io/", "site.com", Request.SCRIPT), true);
        eq("a blocked host's page is an ad page", f.blocksPage("https://ads.example.com/"), true);
        eq("the website's own page isn't", f.blocksPage("https://app.kurdfilm.krd/tv"), false);
    }

    static void patterns() throws Exception {
        FilterSet f = Lists.compile(String.join("\n",
                "/banner/*/img^",
                "||cdn.site.com/ads/",
                "|https://pop.",
                ".swf|",
                "-ad-300x250.",
                "||example.org^*/track?",
                "&adtype=",
                "/AdServe/*$match-case"));
        String page = "page.com";
        eq("a wildcard and ^", blocks(f, "https://x.com/banner/123/img?x", page, Request.IMAGE), true);
        eq("^ at the very end", blocks(f, "https://x.com/banner/123/img", page, Request.IMAGE), true);
        eq("^ is not a letter", blocks(f, "https://x.com/banner/123/imgs", page, Request.IMAGE), false);
        eq("|| with a path", blocks(f, "https://cdn.site.com/ads/1.png", page, Request.IMAGE), true);
        eq("|| at a label of the host", blocks(f, "https://www.cdn.site.com/ads/1.png", page, Request.IMAGE), true);
        eq("|| not in the middle of a label", blocks(f, "https://xcdn.site.com/ads/1.png", page, Request.IMAGE), false);
        eq("|| not in the path", blocks(f, "https://other.com/cdn.site.com/ads/", page, Request.IMAGE), false);
        eq("| at the start", blocks(f, "https://pop.example.net/x", page, Request.SCRIPT), true);
        eq("| at the start, not later", blocks(f, "https://a.com/?u=https://pop.x", page, Request.SCRIPT), false);
        eq("| at the end", blocks(f, "https://a.com/movie.swf", page, Request.OBJECT), true);
        eq("| at the end, not before it", blocks(f, "https://a.com/movie.swf?x", page, Request.OBJECT), false);
        eq("plain words anywhere", blocks(f, "https://a.com/img/top-ad-300x250.jpg", page, Request.IMAGE), true);
        eq("||, ^ and a wildcard", blocks(f, "https://example.org/api/v1/track?e=1", page, Request.XHR), true);
        eq("a word in the query", blocks(f, "https://a.com/x?id=1&adtype=pre", page, Request.XHR), true);
        eq("$match-case", blocks(f, "https://a.com/AdServe/x", page, Request.SCRIPT), true);
        eq("$match-case, in other letters", blocks(f, "https://a.com/adserve/x", page, Request.SCRIPT), false);
        eq("nothing matches the website's page", blocks(f, "https://app.kurdfilm.krd/tv/home", page, Request.SUBDOCUMENT), false);
    }
}
