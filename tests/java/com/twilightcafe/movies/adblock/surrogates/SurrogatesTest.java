package com.twilightcafe.movies.adblock.surrogates;

import com.twilightcafe.movies.adblock.engine.Request;

import java.nio.charset.StandardCharsets;

import static com.twilightcafe.movies.test.Check.eq;

/** The stand-ins answered instead of what's blocked: by the rule's redirect=, by the address, or by its kind. */
public final class SurrogatesTest {
    private SurrogatesTest() {
    }

    private static String text(Surrogates.Body b) {
        return new String(b.data, StandardCharsets.UTF_8);
    }

    public static void run() {
        eq("noopjs", Surrogates.named("noopjs").mime, "application/javascript");
        eq("noopjs:10 (with a priority)", Surrogates.named("noopjs:10").data.length, 0);
        eq("1x1.gif", Surrogates.named("1x1.gif").data.length, 43);
        eq("noopmp4-1s", Surrogates.named("noopmp4-1s").data.length, 1436);
        eq("noopmp3-0.1s", Surrogates.named("noopmp3-0.1s").data.length, 157);
        eq("the MP4 is one (ftyp)", new String(Surrogates.named("noop-1s.mp4").data, 4, 4, StandardCharsets.US_ASCII), "ftyp");
        eq("noopvast-3.0", text(Surrogates.named("noopvast-3.0")).contains("<VAST version=\"3.0\">"), true);
        eq("Google's adsbygoogle.js stand-in", Surrogates.named("googlesyndication_adsbygoogle.js").data.length > 100, true);
        eq("a name it doesn't know", Surrogates.named("nothing-like-this"), null);
        eq("by the address: adsbygoogle", text(Surrogates.forBlocked(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=x", Request.SCRIPT)).contains("adsbygoogle"), true);
        eq("by the address: Google's gpt.js", text(Surrogates.forBlocked(
                "https://securepubads.g.doubleclick.net/tag/js/gpt.js", Request.UNKNOWN)).contains("googletag"), true);
        eq("by its kind: a picture", Surrogates.forBlocked("https://a.com/x", Request.IMAGE).mime, "image/gif");
        eq("by its kind: a frame", Surrogates.forBlocked("https://a.com/x", Request.SUBDOCUMENT).mime, "text/html");
        eq("by its kind: unknown", Surrogates.forBlocked("https://a.com/x", Request.UNKNOWN).mime, "text/plain");
        eq("base64 (Android 5 has none of its own)", new String(Base64.decode("aGVsbG8gd29ybGQ="), StandardCharsets.US_ASCII), "hello world");
    }
}
