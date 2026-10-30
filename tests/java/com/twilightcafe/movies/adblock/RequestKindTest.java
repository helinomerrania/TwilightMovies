package com.twilightcafe.movies.adblock;

import com.twilightcafe.movies.adblock.engine.Request;

import java.util.HashMap;
import java.util.Map;

import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;

/** What a request of the page is for (as the rules ask), and the page shown instead of an ad page. */
public final class RequestKindTest {
    private RequestKindTest() {
    }

    private static Map<String, String> headers(String... nameValue) {
        Map<String, String> h = new HashMap<>();
        for (int i = 0; i + 1 < nameValue.length; i += 2) h.put(nameValue[i], nameValue[i + 1]);
        return h;
    }

    public static void run() {
        eq("a frame, by what the engine asked for", RequestKind.of(headers("Accept", "text/html,application/xhtml+xml"), "https://a.com/x"), Request.SUBDOCUMENT);
        eq("a picture", RequestKind.of(headers("accept", "image/webp,*/*"), "https://a.com/x"), Request.IMAGE);
        eq("a style sheet", RequestKind.of(headers("Accept", "text/css,*/*;q=0.1"), "https://a.com/x"), Request.STYLESHEET);
        eq("a video, by Range", RequestKind.of(headers("Range", "bytes=0-"), "https://a.com/x"), Request.MEDIA);
        eq("a script, by its ending", RequestKind.of(headers("Accept", "*/*"), "https://a.com/app.js?v=2"), Request.SCRIPT);
        eq("a picture, by its ending", RequestKind.of(null, "https://a.com/ad.GIF#x"), Request.IMAGE);
        eq("a font", RequestKind.of(null, "https://a.com/f.woff2"), Request.FONT);
        eq("a video stream", RequestKind.of(null, "https://a.com/live.m3u8"), Request.MEDIA);
        eq("anything else: unknown", RequestKind.of(null, "https://a.com/api/track"), Request.UNKNOWN);
        eq("a header in any letters", RequestKind.header(headers("REFERER", "https://a.com/"), "Referer"), "https://a.com/");
        String page = BlockedPage.html("<An ad>", "Stopped & \"gone\"", "popads.net", true);
        ok("the blocked page: right to left in Kurdish", page.contains("dir=\"rtl\""));
        ok("...its words, safely", page.contains("&lt;An ad&gt;") && page.contains("Stopped &amp; &quot;gone&quot;"));
        ok("...and the site", page.contains("<small>popads.net</small>"));
    }
}
