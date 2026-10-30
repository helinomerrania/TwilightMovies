package com.twilightcafe.movies.adblock.surrogates;

import com.twilightcafe.movies.adblock.engine.Request;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * What a blocked address gets instead: nothing of the ad, but an answer that keeps the page working. The
 * lists name the stand-in they want ($redirect=noopjs...: {@link #named}); for the best-known ad scripts,
 * and otherwise by the kind of request, the app picks one by itself ({@link #forBlocked}). The stand-ins
 * themselves are in Stubs (scripts) and MediaClips (pictures, clips, pages, "no ads" for video players).
 */
public final class Surrogates {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String JS = "application/javascript";

    private static Body cachedMp3;
    private static Body cachedMp4;

    private Surrogates() {
    }

    /** A stand-in's type and content. */
    public static final class Body {
        public final String mime;
        public final byte[] data;

        public Body(String mime, byte[] data) {
            this.mime = mime;
            this.data = data;
        }

        public Body(String mime, String text) {
            this(mime, text.getBytes(UTF8));
        }
    }

    /** The stand-in a rule named, or null for a name the app doesn't have. */
    public static Body named(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        if (colon > 0) n = n.substring(0, colon);   // "noopjs:10": a priority, for uBlock Origin
        if (n.equals("googlesyndication_adsbygoogle.js") || n.equals("googlesyndication.com/adsbygoogle.js")) {
            return new Body(JS, Stubs.ADSBYGOOGLE);
        }
        if (n.equals("googletagservices_gpt.js") || n.equals("googletagservices.com/gpt.js")) return new Body(JS, Stubs.GPT);
        if (n.equals("google-analytics_analytics.js") || n.equals("google-analytics.com/analytics.js")
                || n.equals("google-analytics_ga.js") || n.equals("google-analytics.com/ga.js")) {
            return new Body(JS, Stubs.ANALYTICS);
        }
        if (n.equals("googletagmanager_gtm.js") || n.equals("googletagmanager.com/gtm.js")) return new Body(JS, Stubs.GTM);
        if (n.equals("amazon_apstag.js") || n.equals("amazon-adsystem.com/aax2/amzn_ads.js")) return new Body(JS, Stubs.APSTAG);
        if (n.equals("prebid-ads.js") || n.startsWith("fuckadblock.js") || n.startsWith("nobab")
                || n.startsWith("nofab") || n.equals("sniffadblock.js")) {
            return new Body(JS, Stubs.NO_DETECTOR);
        }
        if (n.equals("noopframe") || n.equals("noop.html") || n.equals("click2load.html")) {
            return new Body("text/html", MediaClips.BLANK_HTML);
        }
        if (n.equals("nooptext") || n.equals("noop.txt") || n.equals("empty")) return new Body("text/plain", "");
        if (n.equals("noop.css")) return new Body("text/css", "");
        if (n.equals("noopjson") || n.equals("noop.json")) return new Body("application/json", "{}");
        if (n.endsWith(".gif") || n.endsWith(".png")) return new Body("image/gif", MediaClips.GIF);
        if (n.startsWith("noopmp3") || n.endsWith(".mp3")) return mp3();
        if (n.startsWith("noopmp4") || n.endsWith(".mp4") || n.equals("mp4")) return mp4();
        if (n.startsWith("noopvmap") || n.startsWith("noop-vmap")) return new Body("application/xml", MediaClips.VMAP);
        if (n.startsWith("noopvast") || n.startsWith("noop-vast")) {
            return new Body("application/xml", MediaClips.vast(n.contains("4") ? "4.0" : n.contains("2") ? "2.0" : "3.0"));
        }
        if (n.endsWith(".js") || n.startsWith("noopjs") || n.equals("none")) return new Body(JS, "");
        return null;
    }

    /** The stand-in for a blocked address no rule named one for: by the best-known scripts, then by its kind. */
    public static Body forBlocked(String url, int type) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        if (path.endsWith("/adsbygoogle.js")) return new Body(JS, Stubs.ADSBYGOOGLE);
        if (path.endsWith("/gpt.js") || path.endsWith("/pubads_impl.js")) return new Body(JS, Stubs.GPT);
        if (path.endsWith("google-analytics.com/analytics.js") || path.endsWith("google-analytics.com/ga.js")) {
            return new Body(JS, Stubs.ANALYTICS);
        }
        if (path.endsWith("googletagmanager.com/gtm.js") || path.contains("googletagmanager.com/gtag/js")) {
            return new Body(JS, Stubs.GTM);
        }
        if (path.endsWith("/apstag.js")) return new Body(JS, Stubs.APSTAG);
        if (path.endsWith("/prebid-ads.js") || path.endsWith("/advertisement.js") || path.endsWith("/ads.js")
                || path.endsWith("/adframe.js") || path.endsWith("/fuckadblock.js") || path.endsWith("/blockadblock.js")) {
            return new Body(JS, Stubs.NO_DETECTOR);
        }
        if (path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac")) return mp3();
        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")) return mp4();
        if (path.endsWith(".xml") && (u.contains("vast") || u.contains("vmap"))) {
            return new Body("application/xml", u.contains("vmap") ? MediaClips.VMAP : MediaClips.vast("3.0"));
        }
        if (type == Request.IMAGE) return new Body("image/gif", MediaClips.GIF);
        if (type == Request.STYLESHEET) return new Body("text/css", "");
        if (type == Request.SUBDOCUMENT || type == Request.DOCUMENT) return new Body("text/html", MediaClips.BLANK_HTML);
        if (type == Request.MEDIA) return mp4();
        if (type == Request.FONT) return new Body("font/woff2", new byte[0]);
        if (type == Request.SCRIPT) return new Body(JS, "");
        return new Body("text/plain", "");   // a fetch, a beacon, or a script whose kind wasn't told
    }

    private static synchronized Body mp3() {
        if (cachedMp3 == null) cachedMp3 = new Body("audio/mpeg", Base64.decode(MediaClips.MP3));
        return cachedMp3;
    }

    private static synchronized Body mp4() {
        if (cachedMp4 == null) cachedMp4 = new Body("video/mp4", Base64.decode(MediaClips.MP4));
        return cachedMp4;
    }
}
