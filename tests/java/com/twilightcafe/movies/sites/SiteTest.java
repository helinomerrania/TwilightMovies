package com.twilightcafe.movies.sites;

import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;

/** A website's names and its own domains (where its pages go freely, and the ad blocker never blocks). */
public final class SiteTest {
    private SiteTest() {
    }

    public static void run() {
        Site kurdfilm = new Site("kurdfilm", "KurdFilm", null, "https://app.kurdfilm.krd/tv", null, false);
        eq("its domain, from its address, as the app always took it", kurdfilm.domains()[0], "kurdfilm.krd");
        ok("its own host", kurdfilm.owns("app.kurdfilm.krd"));
        ok("...the domain itself, and other subdomains", kurdfilm.owns("kurdfilm.krd") && kurdfilm.owns("cdn.www.kurdfilm.krd"));
        ok("...in capitals too", kurdfilm.owns("APP.KURDFILM.KRD"));
        ok("not another site", !kurdfilm.owns("kurdfilm.krd.evil.com") && !kurdfilm.owns("notkurdfilm.krd"));
        ok("not nothing", !kurdfilm.owns("") && !kurdfilm.owns(null));
        eq("its name", kurdfilm.name(false), "KurdFilm");
        eq("...the same in Kurdish when it has no Kurdish one", kurdfilm.name(true), "KurdFilm");
        Site other = new Site("films", "Films", "فیلم", "https://tv.films.example/", new String[]{" Films.Example ", "films-cdn.net"}, true);
        eq("its name in Kurdish", other.name(true), "فیلم");
        ok("domains from sites.json, in small letters", other.owns("tv.films.example") && other.owns("img.films-cdn.net"));
        ok("...and only those", !other.owns("example"));
        ok("the mouse pointer from sites.json", other.pointer);
        eq("the domain of an address with no dot", Site.domainOf("http://localhost:8080/"), "localhost");
    }
}
