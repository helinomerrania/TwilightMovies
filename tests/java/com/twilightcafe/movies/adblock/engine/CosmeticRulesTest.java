package com.twilightcafe.movies.adblock.engine;

import static com.twilightcafe.movies.test.Check.eq;

/**
 * The rules that hide ad boxes: for every page, by class or id, for some sites, with exceptions; and the ones the
 * app can't apply (scriptlets, procedural, HTML filtering, CSS), left out.
 */
public final class CosmeticRulesTest {
    private CosmeticRulesTest() {
    }

    public static void run() throws Exception {
        FilterSet f = Lists.compile(String.join("\n",
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
        eq("highly generic: on every page", all.contains("a[href^=\"https://ads.\"]{display:none!important}"), true);
        eq("...another", all.contains("div[id^=\"div-gpt-ad\"]{"), true);
        eq("a list of selectors, as it is", all.contains(".a, .b{"), true);
        eq("rules under a class aren't on every page", all.contains(".ad-box{"), false);
        eq("...only for pages with the class", f.genericCss("blog.com", ".ad-box .other"), ".ad-box{display:none!important}\n");
        eq("...or the id", f.genericCss("blog.com", "#top-banner"), "#top-banner{display:none!important}\n");
        eq("...the class at the start of a longer selector", f.genericCss("blog.com", ".sponsor"), ".sponsor > div{display:none!important}\n");
        eq("...in the same letters", f.genericCss("blog.com", ".AD-BOX"), "");
        String news = f.pageCss("www.news.com");
        eq("a site's own rule", news.contains(".promo{"), true);
        eq("a rule for a list of sites", news.contains(".wide-ad{"), true);
        eq("...not for the one left out (~)", f.pageCss("sports.news.com").contains(".wide-ad{"), false);
        eq("...nor for other sites", f.pageCss("blog.com").contains(".promo{"), false);
        eq("an exception for one site", f.genericCss("news.com", ".ad-box"), "");
        eq("...and its subdomains", f.genericCss("m.news.com", ".ad-box"), "");
        eq("a generic rule with ~site", f.genericCss("blog.com", ".casino"), ".casino{display:none!important}\n");
        eq("...not on that site", f.genericCss("kids.com", ".casino"), "");
        eq("a generic exception takes the rule away", f.genericCss("blog.com", ".legit"), "");
        eq("an entity (example.*)", f.pageCss("www.example.co.uk").contains(".entity-ad{"), true);
        eq("...not another name", f.pageCss("examples.co.uk").contains(".entity-ad{"), false);
        String site = f.pageCss("site.com");
        eq("procedural hiding is left out", site.contains("has-text"), false);
        eq("scriptlets are left out", site.contains("nowoif"), false);
        eq("HTML filtering is left out", site.contains("^script"), false);
        eq("#?# is left out", site.contains("abp-has"), false);
        eq("CSS in a selector is left out", site.contains("background"), false);
        eq(":has() is kept", site.contains(".ok:has(> .ad){"), true);
        eq("the rules are counted", f.cosmetic.count() > 10, true);
    }
}
