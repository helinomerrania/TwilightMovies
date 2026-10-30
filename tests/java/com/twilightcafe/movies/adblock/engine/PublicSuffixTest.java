package com.twilightcafe.movies.adblock.engine;

import static com.twilightcafe.movies.test.Check.eq;

/** Which site a host belongs to, by the public suffix list (as the filter file carries it), and hosts of addresses. */
public final class PublicSuffixTest {
    private PublicSuffixTest() {
    }

    public static void run() throws Exception {
        FilterSet f = Lists.compile("");
        eq("a .com site", f.registrable("www.example.com"), "example.com");
        eq("a .co.uk site", f.registrable("a.b.example.co.uk"), "example.co.uk");
        eq("a .com.iq site", f.registrable("cdn.site.com.iq"), "site.com.iq");
        eq("a .krd site (KurdFilm's)", f.registrable("app.kurdfilm.krd"), "kurdfilm.krd");
        eq("a private suffix (blogspot.com)", f.registrable("a.myblog.blogspot.com"), "myblog.blogspot.com");
        eq("a wildcard rule (*.ck)", f.registrable("a.b.ck"), "a.b.ck");
        eq("an exception to it (!www.ck)", f.registrable("x.www.ck"), "www.ck");
        eq("an ending the list doesn't know", f.registrable("a.b.zzz"), "b.zzz");
        eq("a single name", f.registrable("localhost"), "localhost");
        eq("an IP address", f.registrable("192.168.1.10"), "192.168.1.10");
        eq("the host of an address: no user, port or case", Request.hostOf("https://user:pw@Sub.Example.COM:8080/x?y#z"), "sub.example.com");
    }
}
