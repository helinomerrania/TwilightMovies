package com.twilightcafe.movies.adblock;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Objects;

/**
 * Tests of the ad blocker's engine: small lists made into a filter file by FilterCompiler, read back by
 * FilterSet exactly as the app reads its own, then asked about addresses and pages. Plain Java, nothing to
 * install: tools/filters/test.sh compiles and runs it (GitHub does on every build).
 */
public final class AdBlockTest {
    private static int pass;
    private static int fail;

    private static final String PSL = String.join("\n",
            "// a few rules of the real list", "com", "net", "org", "uk", "co.uk", "iq", "com.iq", "krd",
            "*.ck", "!www.ck", "blogspot.com", "github.io", "io");

    private static void check(String name, Object got, Object want) {
        if (Objects.equals(got, want)) {
            pass++;
        } else {
            fail++;
            System.out.println("  FAIL " + name + ": got " + got + ", expected " + want);
        }
    }

    static FilterSet compile(String... lists) throws Exception {
        FilterCompiler c = new FilterCompiler();
        c.addList("psl", "psl", new BufferedReader(new StringReader(PSL)));
        int i = 0;
        for (String l : lists) {
            String format = l.startsWith("#hosts\n") ? "hosts" : "adblock";
            c.addList("list" + (i++), format, new BufferedReader(new StringReader(l)));
        }
        c.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        c.write(out, 1234567890L);
        return FilterSet.read(new ByteArrayInputStream(out.toByteArray()));
    }

    static boolean blocks(FilterSet f, String url, String page, int type) {
        return f.match(new Request(url, page, type));
    }

    public static void main(String[] args) throws Exception {
        publicSuffix();
        hosts();
        patterns();
        options();
        exceptions();
        conditionsAndBadfilter();
        hiding();
        surrogates();
        System.out.println("The ad blocker tests: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void publicSuffix() throws Exception {
        FilterSet f = compile("");
        check("registrable com", f.registrable("www.example.com"), "example.com");
        check("registrable co.uk", f.registrable("a.b.example.co.uk"), "example.co.uk");
        check("registrable com.iq", f.registrable("cdn.site.com.iq"), "site.com.iq");
        check("registrable krd", f.registrable("app.kurdfilm.krd"), "kurdfilm.krd");
        check("registrable private suffix", f.registrable("a.myblog.blogspot.com"), "myblog.blogspot.com");
        check("registrable wildcard", f.registrable("a.b.ck"), "a.b.ck");
        check("registrable exception", f.registrable("x.www.ck"), "www.ck");
        check("registrable unknown TLD", f.registrable("a.b.zzz"), "b.zzz");
        check("registrable single label", f.registrable("localhost"), "localhost");
        check("registrable IP", f.registrable("192.168.1.10"), "192.168.1.10");
        check("host of url", Request.hostOf("https://user:pw@Sub.Example.COM:8080/x?y#z"), "sub.example.com");
    }

    static void hosts() throws Exception {
        FilterSet f = compile("||ads.example.com^\n||tracker.net^", "#hosts\n0.0.0.0 bad.org\n127.0.0.1 evil.co.uk # x\nplain.io");
        check("host blocked", blocks(f, "https://ads.example.com/x.js", "site.com", Request.SCRIPT), true);
        check("subdomain blocked", blocks(f, "https://a.b.ads.example.com/", "site.com", Request.IMAGE), true);
        check("parent not blocked", blocks(f, "https://example.com/ads.js", "site.com", Request.SCRIPT), false);
        check("similar not blocked", blocks(f, "https://notads.example.com/", "site.com", Request.SCRIPT), false);
        check("port and query", blocks(f, "http://tracker.net:8080?a=1", "site.com", Request.XHR), true);
        check("hosts list 0.0.0.0", blocks(f, "https://bad.org/", "site.com", Request.SCRIPT), true);
        check("hosts list 127.0.0.1", blocks(f, "https://cdn.evil.co.uk/", "site.com", Request.SCRIPT), true);
        check("hosts list plain", blocks(f, "https://plain.io/", "site.com", Request.SCRIPT), true);
        check("blocked host as a page", f.blocksPage("https://ads.example.com/"), true);
        check("other page", f.blocksPage("https://app.kurdfilm.krd/tv"), false);
    }

    static void patterns() throws Exception {
        FilterSet f = compile(String.join("\n",
                "/banner/*/img^",
                "||cdn.site.com/ads/",
                "|https://pop.",
                ".swf|",
                "-ad-300x250.",
                "||example.org^*/track?",
                "&adtype=",
                "/AdServe/*$match-case"));
        String page = "page.com";
        check("wildcard and ^", blocks(f, "https://x.com/banner/123/img?x", page, Request.IMAGE), true);
        check("^ at the end", blocks(f, "https://x.com/banner/123/img", page, Request.IMAGE), true);
        check("^ is not a letter", blocks(f, "https://x.com/banner/123/imgs", page, Request.IMAGE), false);
        check("host anchor with path", blocks(f, "https://cdn.site.com/ads/1.png", page, Request.IMAGE), true);
        check("host anchor on a label", blocks(f, "https://www.cdn.site.com/ads/1.png", page, Request.IMAGE), true);
        check("host anchor, not mid-label", blocks(f, "https://xcdn.site.com/ads/1.png", page, Request.IMAGE), false);
        check("host anchor, not in the path", blocks(f, "https://other.com/cdn.site.com/ads/", page, Request.IMAGE), false);
        check("start anchor", blocks(f, "https://pop.example.net/x", page, Request.SCRIPT), true);
        check("start anchor, not later", blocks(f, "https://a.com/?u=https://pop.x", page, Request.SCRIPT), false);
        check("end anchor", blocks(f, "https://a.com/movie.swf", page, Request.OBJECT), true);
        check("end anchor, not before the end", blocks(f, "https://a.com/movie.swf?x", page, Request.OBJECT), false);
        check("plain substring", blocks(f, "https://a.com/img/top-ad-300x250.jpg", page, Request.IMAGE), true);
        check("host anchor, ^ and wildcard", blocks(f, "https://example.org/api/v1/track?e=1", page, Request.XHR), true);
        check("query word", blocks(f, "https://a.com/x?id=1&adtype=pre", page, Request.XHR), true);
        check("match-case", blocks(f, "https://a.com/AdServe/x", page, Request.SCRIPT), true);
        check("match-case, other case", blocks(f, "https://a.com/adserve/x", page, Request.SCRIPT), false);
        check("nothing matches", blocks(f, "https://app.kurdfilm.krd/tv/home", page, Request.SUBDOCUMENT), false);
    }

    static void options() throws Exception {
        FilterSet f = compile(String.join("\n",
                "||ads.net^$third-party",
                "||stats.org^$script",
                "||img.org^$~image",
                "/promo/*$domain=news.com|~sports.news.com",
                "||cdn.x.com/*.js$script,3p,denyallow=good.com",
                "*$script,3p,domain=strict.com,to=~cdn.strict.com",
                "||badware.site^$doc",
                "||popunder.com^$popup",
                "||all.com^$all",
                "||vid.com/ad.mp4$media,redirect=noopmp4-1s",
                "||site.com/pre.js$redirect=noopjs:10",
                "||csp.com^$csp=script-src 'none'",
                "||site.org^$removeparam=utm",
                "||entity.com/x$domain=example.*"));
        check("third-party from another site", blocks(f, "https://ads.net/a.js", "news.com", Request.SCRIPT), true);
        check("third-party: not on its own site", blocks(f, "https://www.ads.net/a.js", "ads.net", Request.SCRIPT), false);
        check("type: script", blocks(f, "https://stats.org/s.js", "a.com", Request.SCRIPT), true);
        check("type: not an image", blocks(f, "https://stats.org/s.gif", "a.com", Request.IMAGE), false);
        check("type: unknown may be a script", blocks(f, "https://stats.org/s", "a.com", Request.UNKNOWN), true);
        check("~image: a script", blocks(f, "https://img.org/a.js", "a.com", Request.SCRIPT), true);
        check("~image: not an image", blocks(f, "https://img.org/a.png", "a.com", Request.IMAGE), false);
        check("domain=", blocks(f, "https://cdn.com/promo/1.jpg", "www.news.com", Request.IMAGE), true);
        check("domain=~", blocks(f, "https://cdn.com/promo/1.jpg", "sports.news.com", Request.IMAGE), false);
        check("domain= other site", blocks(f, "https://cdn.com/promo/1.jpg", "blog.com", Request.IMAGE), false);
        check("denyallow: other page", blocks(f, "https://cdn.x.com/lib.js", "a.com", Request.SCRIPT), true);
        check("to=~ excluded host", blocks(f, "https://cdn.strict.com/app.js", "strict.com", Request.SCRIPT), false);
        check("to=~ other host", blocks(f, "https://tracker.io/t.js", "strict.com", Request.SCRIPT), true);
        check("$doc blocks the page", f.blocksPage("https://www.badware.site/"), true);
        check("$doc: not what pages load", blocks(f, "https://badware.site/x.js", "a.com", Request.SCRIPT), false);
        check("$popup rules are left out", blocks(f, "https://popunder.com/x", "a.com", Request.SCRIPT), false);
        check("$all: a page too", f.blocksPage("https://all.com/"), true);
        check("$all: a script", blocks(f, "https://all.com/x.js", "a.com", Request.SCRIPT), true);
        Request r = new Request("https://vid.com/ad.mp4", "a.com", Request.MEDIA);
        check("redirect=", f.match(r) + " " + r.redirect, "true noopmp4-1s");
        r = new Request("https://site.com/pre.js", "a.com", Request.SCRIPT);
        check("redirect= with a priority", f.match(r) + " " + r.redirect, "true noopjs:10");
        check("$csp left out", blocks(f, "https://csp.com/", "a.com", Request.SCRIPT), false);
        check("$removeparam left out", blocks(f, "https://site.org/?utm=1", "a.com", Request.SCRIPT), false);
        check("domain=entity.*", blocks(f, "https://entity.com/x", "www.example.co.uk", Request.SCRIPT), true);
        check("domain=entity.*, other", blocks(f, "https://entity.com/x", "examples.com", Request.SCRIPT), false);
    }

    static void exceptions() throws Exception {
        FilterSet f = compile(String.join("\n",
                "||ads.example.com^",
                "@@||ads.example.com/ok.js",
                "/adlib.js",
                "@@||good.com/adlib.js$script",
                "||forced.com^$important",
                "@@||forced.com^",
                "||ads.net^",
                "@@||ads.net^$domain=partner.com",
                "@@||whitelisted.com^$document",
                "@@||nohide.com^$elemhide",
                "@@||nogeneric.com^$generichide",
                "##.adbox",
                "nohide.com##.mine"));
        check("block", blocks(f, "https://ads.example.com/x.js", "a.com", Request.SCRIPT), true);
        check("exception over a host", blocks(f, "https://ads.example.com/ok.js", "a.com", Request.SCRIPT), false);
        check("exception by path", blocks(f, "https://good.com/adlib.js", "a.com", Request.SCRIPT), false);
        check("exception: other host", blocks(f, "https://bad.com/adlib.js", "a.com", Request.SCRIPT), true);
        check("exception: its type only", blocks(f, "https://good.com/adlib.js", "a.com", Request.IMAGE), true);
        check("$important beats exceptions", blocks(f, "https://forced.com/x", "a.com", Request.SCRIPT), true);
        check("exception on a partner", blocks(f, "https://ads.net/x", "partner.com", Request.SCRIPT), false);
        check("no exception elsewhere", blocks(f, "https://ads.net/x", "a.com", Request.SCRIPT), true);
        check("$document: page left alone", blocks(f, "https://ads.net/x", "www.whitelisted.com", Request.SCRIPT), false);
        check("$document: pageAllowed", f.pageAllowed("whitelisted.com"), true);
        check("$elemhide: no hiding", f.pageCss("nohide.com"), "");
        check("$generichide: no generic", f.genericCss("nogeneric.com", ".adbox"), "");
        check("generic elsewhere", f.genericCss("a.com", ".adbox"), ".adbox{display:none!important}\n");
    }

    static void conditionsAndBadfilter() throws Exception {
        FilterSet f = compile(String.join("\n",
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
        check("!#if env_mobile: no", blocks(f, "https://mobile-only.com/", "a.com", Request.SCRIPT), false);
        check("!#else", blocks(f, "https://not-mobile.com/", "a.com", Request.SCRIPT), true);
        check("!#if && !", blocks(f, "https://chromium.com/", "a.com", Request.SCRIPT), true);
        check("!#if ( || )", blocks(f, "https://firefox.com/", "a.com", Request.SCRIPT), false);
        check("$badfilter cancels a host", blocks(f, "https://broken.com/", "a.com", Request.SCRIPT), false);
        check("$badfilter, options in another order", blocks(f, "https://cdn.lib.com/x.js", "a.com", Request.SCRIPT), false);
        check("regular expressions left out", blocks(f, "https://a.com/ads12/x.js", "b.com", Request.SCRIPT), false);
        check("other rules stay", blocks(f, "https://regex.com/x/1.js", "a.com", Request.SCRIPT), true);
    }

    static void hiding() throws Exception {
        FilterSet f = compile(String.join("\n",
                "##.ad-box",
                "###top-banner",
                "##.sponsor > div",
                "##a[href^=\"https://ads.\"]",
                "##div[id^=\"div-gpt-ad\"]",
                "##.a, .b",
                "news.com##.promo",
                "news.com,~sports.news.com##.wide-ad",
                "~kids.com##.casino",
                "news.com#@#.ad-box",
                "#@#.legit",
                "##.legit",
                "example.*##.entity-ad",
                "site.com##.x:has-text(Ad)",
                "site.com##+js(nowoif)",
                "site.com##^script:has-text(ad)",
                "site.com#?#.y:-abp-has(.z)",
                "site.com##body{background:red}",
                "site.com##.ok:has(> .ad)"));
        String all = f.pageCss("blog.com");
        check("highly generic on every page", all.contains("a[href^=\"https://ads.\"]{display:none!important}"), true);
        check("highly generic 2", all.contains("div[id^=\"div-gpt-ad\"]{"), true);
        check("a selector list is applied whole", all.contains(".a, .b{"), true);
        check("keyed are not on every page", all.contains(".ad-box{"), false);
        check("keyed by class", f.genericCss("blog.com", ".ad-box .other"), ".ad-box{display:none!important}\n");
        check("keyed by id", f.genericCss("blog.com", "#top-banner"), "#top-banner{display:none!important}\n");
        check("keyed complex", f.genericCss("blog.com", ".sponsor"), ".sponsor > div{display:none!important}\n");
        check("keyed: case matters", f.genericCss("blog.com", ".AD-BOX"), "");
        String news = f.pageCss("www.news.com");
        check("specific", news.contains(".promo{"), true);
        check("specific with a domain list", news.contains(".wide-ad{"), true);
        check("specific, not its ~sub", f.pageCss("sports.news.com").contains(".wide-ad{"), false);
        check("specific, not elsewhere", f.pageCss("blog.com").contains(".promo{"), false);
        check("exception for one site", f.genericCss("news.com", ".ad-box"), "");
        check("exception for its subdomain", f.genericCss("m.news.com", ".ad-box"), "");
        check("generic with ~site", f.genericCss("blog.com", ".casino"), ".casino{display:none!important}\n");
        check("generic with ~site: not there", f.genericCss("kids.com", ".casino"), "");
        check("generic exception removes it", f.genericCss("blog.com", ".legit"), "");
        check("entity", f.pageCss("www.example.co.uk").contains(".entity-ad{"), true);
        check("entity, other name", f.pageCss("examples.co.uk").contains(".entity-ad{"), false);
        String site = f.pageCss("site.com");
        check("procedural left out", site.contains("has-text"), false);
        check("scriptlets left out", site.contains("nowoif"), false);
        check("HTML filtering left out", site.contains("^script"), false);
        check("#?# left out", site.contains("abp-has"), false);
        check("CSS in a selector left out", site.contains("background"), false);
        check(":has() kept", site.contains(".ok:has(> .ad){"), true);
    }

    static void surrogates() {
        check("noopjs", Surrogates.named("noopjs").mime, "application/javascript");
        check("noopjs:10", Surrogates.named("noopjs:10").data.length, 0);
        check("1x1.gif", Surrogates.named("1x1.gif").data.length, 43);
        check("noopmp4", Surrogates.named("noopmp4-1s").data.length, 1436);
        check("noopmp3", Surrogates.named("noopmp3-0.1s").data.length, 157);
        check("mp4 starts with ftyp", new String(Surrogates.named("noop-1s.mp4").data, 4, 4, java.nio.charset.StandardCharsets.US_ASCII), "ftyp");
        check("noopvast-3.0", new String(Surrogates.named("noopvast-3.0").data, java.nio.charset.StandardCharsets.UTF_8).contains("<VAST version=\"3.0\">"), true);
        check("adsbygoogle stand-in", Surrogates.named("googlesyndication_adsbygoogle.js").data.length > 100, true);
        check("unknown name", Surrogates.named("nothing-like-this"), null);
        check("by address: adsbygoogle", new String(Surrogates.forBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=x", Request.SCRIPT).data,
                java.nio.charset.StandardCharsets.UTF_8).contains("adsbygoogle"), true);
        check("by address: gpt", new String(Surrogates.forBlocked("https://securepubads.g.doubleclick.net/tag/js/gpt.js", Request.UNKNOWN).data,
                java.nio.charset.StandardCharsets.UTF_8).contains("googletag"), true);
        check("by kind: image", Surrogates.forBlocked("https://a.com/x", Request.IMAGE).mime, "image/gif");
        check("by kind: frame", Surrogates.forBlocked("https://a.com/x", Request.SUBDOCUMENT).mime, "text/html");
        check("by kind: unknown", Surrogates.forBlocked("https://a.com/x", Request.UNKNOWN).mime, "text/plain");
        check("base64", new String(Surrogates.base64("aGVsbG8gd29ybGQ="), java.nio.charset.StandardCharsets.US_ASCII), "hello world");
    }
}
