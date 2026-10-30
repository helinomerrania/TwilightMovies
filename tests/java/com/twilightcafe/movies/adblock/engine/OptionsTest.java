package com.twilightcafe.movies.adblock.engine;

import static com.twilightcafe.movies.adblock.engine.Lists.blocks;
import static com.twilightcafe.movies.test.Check.eq;

/** The options after a rule's $ (parties, kinds, domain=, to=, denyallow=, redirect=...), and exceptions (@@). */
public final class OptionsTest {
    private OptionsTest() {
    }

    public static void run() throws Exception {
        options();
        exceptions();
    }

    static void options() throws Exception {
        FilterSet f = Lists.compile(String.join("\n",
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
        eq("$third-party, from another site", blocks(f, "https://ads.net/a.js", "news.com", Request.SCRIPT), true);
        eq("$third-party, not on its own site", blocks(f, "https://www.ads.net/a.js", "ads.net", Request.SCRIPT), false);
        eq("$script: a script", blocks(f, "https://stats.org/s.js", "a.com", Request.SCRIPT), true);
        eq("$script: not a picture", blocks(f, "https://stats.org/s.gif", "a.com", Request.IMAGE), false);
        eq("$script: what's unknown may be one", blocks(f, "https://stats.org/s", "a.com", Request.UNKNOWN), true);
        eq("$~image: a script", blocks(f, "https://img.org/a.js", "a.com", Request.SCRIPT), true);
        eq("$~image: not a picture", blocks(f, "https://img.org/a.png", "a.com", Request.IMAGE), false);
        eq("domain=", blocks(f, "https://cdn.com/promo/1.jpg", "www.news.com", Request.IMAGE), true);
        eq("domain=~", blocks(f, "https://cdn.com/promo/1.jpg", "sports.news.com", Request.IMAGE), false);
        eq("domain=, on another site", blocks(f, "https://cdn.com/promo/1.jpg", "blog.com", Request.IMAGE), false);
        eq("denyallow=, on another page", blocks(f, "https://cdn.x.com/lib.js", "a.com", Request.SCRIPT), true);
        eq("to=~, the host left out", blocks(f, "https://cdn.strict.com/app.js", "strict.com", Request.SCRIPT), false);
        eq("to=~, another host", blocks(f, "https://tracker.io/t.js", "strict.com", Request.SCRIPT), true);
        eq("$doc blocks the page", f.blocksPage("https://www.badware.site/"), true);
        eq("$doc: not what other pages load from it", blocks(f, "https://badware.site/x.js", "a.com", Request.SCRIPT), false);
        eq("$popup rules are left out (the app stops pop-ups itself)", blocks(f, "https://popunder.com/x", "a.com", Request.SCRIPT), false);
        eq("$all: a page too", f.blocksPage("https://all.com/"), true);
        eq("$all: a script", blocks(f, "https://all.com/x.js", "a.com", Request.SCRIPT), true);
        Request r = new Request("https://vid.com/ad.mp4", "a.com", Request.MEDIA);
        eq("redirect=", f.match(r) + " " + r.redirect, "true noopmp4-1s");
        r = new Request("https://site.com/pre.js", "a.com", Request.SCRIPT);
        eq("redirect= with a priority", f.match(r) + " " + r.redirect, "true noopjs:10");
        eq("$csp is left out", blocks(f, "https://csp.com/", "a.com", Request.SCRIPT), false);
        eq("$removeparam is left out", blocks(f, "https://site.org/?utm=1", "a.com", Request.SCRIPT), false);
        eq("domain=example.* (an entity)", blocks(f, "https://entity.com/x", "www.example.co.uk", Request.SCRIPT), true);
        eq("domain=example.*, another name", blocks(f, "https://entity.com/x", "examples.com", Request.SCRIPT), false);
    }

    static void exceptions() throws Exception {
        FilterSet f = Lists.compile(String.join("\n",
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
        eq("a block", blocks(f, "https://ads.example.com/x.js", "a.com", Request.SCRIPT), true);
        eq("an exception within a blocked host", blocks(f, "https://ads.example.com/ok.js", "a.com", Request.SCRIPT), false);
        eq("an exception by its path", blocks(f, "https://good.com/adlib.js", "a.com", Request.SCRIPT), false);
        eq("...not for another host", blocks(f, "https://bad.com/adlib.js", "a.com", Request.SCRIPT), true);
        eq("...only for its kind", blocks(f, "https://good.com/adlib.js", "a.com", Request.IMAGE), true);
        eq("$important beats exceptions", blocks(f, "https://forced.com/x", "a.com", Request.SCRIPT), true);
        eq("an exception on a partner's pages", blocks(f, "https://ads.net/x", "partner.com", Request.SCRIPT), false);
        eq("...not elsewhere", blocks(f, "https://ads.net/x", "a.com", Request.SCRIPT), true);
        eq("$document: the site's pages are left alone", blocks(f, "https://ads.net/x", "www.whitelisted.com", Request.SCRIPT), false);
        eq("$document: pageAllowed", f.pageAllowed("whitelisted.com"), true);
        eq("$elemhide: no hiding there", f.pageCss("nohide.com"), "");
        eq("$generichide: none of the generic hiding there", f.genericCss("nogeneric.com", ".adbox"), "");
        eq("...but the generic hiding elsewhere", f.genericCss("a.com", ".adbox"), ".adbox{display:none!important}\n");
    }
}
