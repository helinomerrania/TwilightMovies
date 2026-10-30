package com.twilightcafe.movies.adblock;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Lists;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;

/**
 * Where the ad blocker works and where pages may go: the app's own websites, sites allowed from the menu, jumps to
 * ad pages and to other sites, the switch for one site, and what's kept on the box.
 */
public final class SitePolicyTest {
    private SitePolicyTest() {
    }

    public static void run() throws Exception {
        final Map<String, Set<String>> kept = new HashMap<>();
        SitePolicy p = new SitePolicy(Collections.<String>emptySet(), Collections.<String>emptySet(), kept::put);
        p.setOwn("app.kurdfilm.krd", "kurdfilm.krd", "tv.films.example");
        eq("before the filters load: a site is a host's last two names", p.site("www.example.co.uk"), "co.uk");
        FilterSet f = Lists.compile("||ads.example.com^\n||popads.net^$doc");
        p.useFilters(f);
        eq("then by the public suffix list", p.site("www.example.co.uk"), "example.co.uk");
        ok("the app's own websites", p.ownOrAllowed("https://www.kurdfilm.krd/x") && p.ownOrAllowed("https://cdn.films.example/v.mp4"));
        ok("not another site", !p.ownOrAllowed("https://example.com/"));
        ok("what the websites serve is their own (never blocked)", p.own("video.kurdfilm.krd") && p.own("img.films.example"));
        ok("...not what other sites serve", !p.own("ads.example.com") && !p.own(""));
        eq("a jump within the website", p.checkJump("https://app.kurdfilm.krd/film/1", "app.kurdfilm.krd"), SitePolicy.GO);
        eq("a jump to another of the app's websites", p.checkJump("https://tv.films.example/", "app.kurdfilm.krd"), SitePolicy.GO);
        eq("a jump to an ad page", p.checkJump("https://popads.net/go", "app.kurdfilm.krd"), SitePolicy.AD);
        eq("a jump to any other site", p.checkJump("https://example.com/", "app.kurdfilm.krd"), SitePolicy.JUMP);
        eq("about:blank and javascript: stay in the page", p.checkJump("javascript:void(0)", "app.kurdfilm.krd"), SitePolicy.GO);
        p.allow("www.example.com");
        eq("a site allowed from the menu", p.checkJump("https://example.com/x", "app.kurdfilm.krd"), SitePolicy.GO);
        eq("...kept on the box", kept.get("allowed"), Collections.singleton("example.com"));
        ok("the blocker works everywhere at first", p.activeFor("app.kurdfilm.krd") && p.activeFor(""));
        p.setActive("cdn.kurdfilm.krd", false);
        ok("switched off for a site: all of it", !p.activeFor("app.kurdfilm.krd"));
        eq("...kept on the box", kept.get("off"), Collections.singleton("kurdfilm.krd"));
        eq("...and its pages go anywhere", p.checkJump("https://popads.net/go", "app.kurdfilm.krd"), SitePolicy.GO);
        p.setActive("app.kurdfilm.krd", true);
        ok("switched on again", p.activeFor("app.kurdfilm.krd") && kept.get("off").isEmpty());
        SitePolicy again = new SitePolicy(kept.get("off"), kept.get("allowed"), kept::put);
        again.useFilters(f);
        eq("what was kept, at the next start", again.checkJump("https://example.com/", "a.com"), SitePolicy.GO);
    }
}
