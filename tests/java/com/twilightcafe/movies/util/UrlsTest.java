package com.twilightcafe.movies.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;

/** Web addresses (hosts, short forms, what's typed), and downloads counted (Streams). */
public final class UrlsTest {
    private UrlsTest() {
    }

    public static void run() throws Exception {
        eq("the host of an address", Urls.hostOf("https://app.kurdfilm.krd/tv"), "app.kurdfilm.krd");
        eq("...in small letters, without user, password and port", Urls.hostOf("http://me:pw@App.KurdFilm.KRD:8080/x"), "app.kurdfilm.krd");
        eq("...up to a query", Urls.hostOf("https://a.com?x=/y"), "a.com");
        eq("...an IPv6 address", Urls.hostOf("http://[::1]:8080/"), "[::1]");
        eq("an address without a host, as it is", Urls.hostOf("about:blank"), "about:blank");
        eq("no address, no host", Urls.hostOf(null), "");
        eq("the short form", Urls.shortUrl("https://app.kurdfilm.krd/tv/"), "app.kurdfilm.krd/tv");
        ok("same host", Urls.sameHost("https://a.com/x", "http://A.com:81/y"));
        ok("not the same host", !Urls.sameHost("https://a.com/x", "https://www.a.com/"));
        ok("web addresses stay in the page", Urls.isWebScheme("https://a.com") && Urls.isWebScheme("about:blank")
                && Urls.isWebScheme("blob:https://a.com/1") && Urls.isWebScheme("javascript:void(0)"));
        ok("intent: and market: never do", !Urls.isWebScheme("intent://x#Intent;end") && !Urls.isWebScheme("market://details?id=x"));
        eq("typed without https://", Urls.normalize(" kurdfilm.krd/tv "), "https://kurdfilm.krd/tv");
        eq("typed with spaces in it", Urls.normalize("app. kurdfilm .krd"), "https://app.kurdfilm.krd");
        eq("typed with http://", Urls.normalize("http://a.com"), "http://a.com");
        eq("typed in capitals", Urls.normalize("HTTPS://A.COM"), "HTTPS://A.COM");
        eq("not a website: no dot", Urls.normalize("kurdfilm"), null);
        eq("not a website: ftp", Urls.normalize("ftp://a.com"), null);
        eq("not a website: a dot at the end", Urls.normalize("a.com."), null);
        eq("not a website: nothing", Urls.normalize("  "), null);
        streams();
    }

    static void streams() throws Exception {
        byte[] data = new byte[100000];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        eq("a download as long as it should be", Streams.copy(new ByteArrayInputStream(data), out, data.length, 1 << 20), 100000L);
        ok("a download cut short is refused", fails(data, data.length + 1, 1 << 20));
        ok("a download longer than it should be is refused", fails(data, data.length - 1, 1 << 20));
        ok("a download bigger than the limit is refused", fails(data, -1, 50000));
        eq("a download of unknown length, under the limit", Streams.read(new ByteArrayInputStream(data), 1 << 20).length, 100000);
    }

    private static boolean fails(byte[] data, long expected, long max) {
        try {
            Streams.copy(new ByteArrayInputStream(data), new ByteArrayOutputStream(), expected, max);
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
