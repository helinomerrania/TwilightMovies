package com.twilightcafe.movies.adblock;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * What a blocked address gets instead: nothing of the ad, but an answer that keeps the page working. An empty
 * script for a script, a see-through pixel for a picture, a blank page for a frame, a silent clip for an ad's
 * video or sound, "no ads" for a video player's ad request (VAST, VMAP), and small stand-ins for the ad
 * scripts pages check for (Google's ads, tags and analytics, Amazon's, the ad-blocker detectors), so a page
 * that waits for them goes on instead of breaking. The lists name the stand-in they want ($redirect=...); for
 * the best-known ad scripts the app picks it by itself. The stand-ins are the app's own, written for it.
 */
public final class Surrogates {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** A stand-in's type and content. */
    public static final class Body {
        public final String mime;
        public final byte[] data;

        Body(String mime, byte[] data) {
            this.mime = mime;
            this.data = data;
        }

        Body(String mime, String text) {
            this(mime, text.getBytes(UTF8));
        }
    }

    private static final String JS = "application/javascript";

    static final String ADSBYGOOGLE_JS =
            "(function(){var w=window,a=w.adsbygoogle;if(!a||typeof a.push!=='function')a=[];"
            + "a.loaded=true;a.push=function(){};w.adsbygoogle=a;"
            + "var e=document.querySelectorAll('ins.adsbygoogle');"
            + "for(var i=0;i<e.length;i++){e[i].setAttribute('data-ad-status','unfilled');e[i].style.display='none';}})();";

    static final String GPT_JS =
            "(function(){var w=window,n=function(){},s=function(){return this;},e=function(){return [];},"
            + "z=function(){return null;},slot={addService:s,clearCategoryExclusions:s,clearTargeting:s,"
            + "defineSizeMapping:s,get:z,getAdUnitPath:function(){return '';},getAttributeKeys:e,getTargeting:e,"
            + "getTargetingKeys:e,getSlotElementId:function(){return '';},set:s,setCategoryExclusion:s,"
            + "setClickUrl:s,setCollapseEmptyDiv:s,setTargeting:s,updateTargetingFromMap:s},"
            + "ads={addEventListener:s,removeEventListener:s,clear:n,clearCategoryExclusions:s,clearTargeting:s,"
            + "collapseEmptyDivs:s,defineOutOfPagePassback:function(){return slot;},"
            + "definePassback:function(){return slot;},disableInitialLoad:n,display:n,enableAsyncRendering:n,"
            + "enableLazyLoad:n,enableSingleRequest:n,enableSyncRendering:n,enableVideoAds:n,get:z,"
            + "getAttributeKeys:e,getTargeting:e,getTargetingKeys:e,getSlots:e,isInitialLoadDisabled:function(){return true;},"
            + "refresh:n,set:s,setCategoryExclusion:s,setCentering:n,setCookieOptions:s,setForceSafeFrame:s,"
            + "setLocation:s,setPrivacySettings:s,setPublisherProvidedId:s,setRequestNonPersonalizedAds:s,"
            + "setSafeFrameConfig:s,setTagForChildDirectedTreatment:s,setTargeting:s,setVideoContent:s,updateCorrelator:n},"
            + "g=w.googletag||{},q=g.cmd||[];g.apiReady=true;g.pubadsReady=true;"
            + "g.cmd={push:function(f){try{f();}catch(x){}return 1;}};"
            + "g.pubads=function(){return ads;};g.companionAds=function(){return ads;};g.content=function(){return ads;};"
            + "g.defineSlot=function(){return slot;};g.defineOutOfPageSlot=function(){return slot;};g.destroySlots=n;"
            + "g.disablePublisherConsole=n;g.display=n;g.enableServices=n;g.getVersion=function(){return '';};"
            + "g.setAdIframeTitle=n;g.sizeMapping=function(){var m={addSize:function(){return m;},build:e};return m;};"
            + "w.googletag=g;for(var i=0;i<q.length;i++){try{q[i]();}catch(x){}}})();";

    static final String ANALYTICS_JS =
            "(function(){var w=window,n=w.GoogleAnalyticsObject||'ga',q=(w[n]&&w[n].q)||[];"
            + "var t={get:function(){},set:function(){},send:function(){}};"
            + "function ga(){var a=arguments,o=a.length?a[a.length-1]:null;"
            + "if(o&&typeof o==='object'&&typeof o.hitCallback==='function'){try{o.hitCallback();}catch(x){}}}"
            + "ga.create=function(){return t;};ga.getByName=function(){return t;};ga.getAll=function(){return [t];};"
            + "ga.remove=function(){};ga.loaded=true;w[n]=ga;"
            + "for(var i=0;i<q.length;i++){try{ga.apply(null,q[i]);}catch(x){}}})();";

    static final String GTM_JS =
            "(function(){var w=window,d=w.dataLayer;function cb(o){if(o&&typeof o.eventCallback==='function'){"
            + "var f=o.eventCallback;setTimeout(function(){try{f();}catch(x){}},1);}}"
            + "if(d&&typeof d.push==='function'){for(var i=0;i<d.length;i++)cb(d[i]);"
            + "var p=d.push;d.push=function(o){cb(o);return p.apply(d,arguments);};}"
            + "w.google_tag_manager=w.google_tag_manager||{};})();";

    static final String NO_DETECTOR_JS =
            "(function(){var w=window;w.canRunAds=true;w.isAdBlockActive=false;w.adblock=false;"
            + "function F(){}F.prototype={check:function(){return false;},clearEvent:function(){return this;},"
            + "emitEvent:function(){return this;},on:function(d,f){if(!d&&typeof f==='function')setTimeout(f,1);return this;},"
            + "onDetected:function(){return this;},onNotDetected:function(f){if(typeof f==='function')setTimeout(f,1);return this;},"
            + "setOption:function(){return this;}};"
            + "w.FuckAdBlock=w.BlockAdBlock=w.SniffAdBlock=F;w.fuckAdBlock=w.blockAdBlock=w.sniffAdBlock=new F();})();";

    static final String APSTAG_JS =
            "(function(){var w=window;w.apstag={_Q:[],fetchBids:function(o,c){if(typeof c==='function')"
            + "setTimeout(function(){try{c([]);}catch(x){}},1);},init:function(){},setDisplayBids:function(){},"
            + "targetingKeys:function(){return [];}};})();";

    static final byte[] GIF = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x21, (byte) 0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b};

    /** 0.16 s of silence (made with ffmpeg) */
    private static final String MP3 = "//MQxAAAAANIAAAAAExBTUUzLjEwMFVVVVX/8xLEDQAAA0gAAAAAVVVVVVVVVVVVVVVVVVX/8xDEGwAAA0gAAAAAVVVVVVVVVVVV"
            + "VVVVVf/zEMQoAAADSAAAAABVVVVVVVVVVVVVVVVV//MQxDUAAANIAAAAAFVVVVVVVVVVVVVVVVX/8xDEQgAAA0gAAAAAVVVVVVVV"
            + "VVVVVVVVVQ==";

    /** 1 s of black with silent sound, 32x18 (made with ffmpeg) */
    private static final String MP4 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAUzbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAA+gAAQAAAQAA"
            + "AAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAA"
            + "Ajl0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAA+gAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAA"
            + "AAAAAAAAAAAAAABAAAAAACAAAAASAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAPoAAAAAAABAAAAAAGxbWRpYQAAACBtZGhk"
            + "AAAAAAAAAAAAAAAAAABAAAAAQABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABXG1p"
            + "bmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAARxzdGJsAAAAuHN0c2QA"
            + "AAAAAAAAAQAAAKhhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAACAAEgBIAAAASAAAAAAAAAABDExhdmMgbGlieDI2NAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAGP//AAAALmF2Y0MBQsAK/+EAF2dCwAraJfiMBEAAAAMAQAAAAwCDxImoAQAEaM4PyAAAABBwYXNwAAAA"
            + "AQAAAAEAAAAUYnRydAAAAAAAAACoAAAAqAAAABhzdHRzAAAAAAAAAAEAAAABAABAAAAAABxzdHNjAAAAAAAAAAEAAAABAAAAAQAA"
            + "AAEAAAAUc3RzegAAAAAAAAAVAAAAAQAAABRzdGNvAAAAAAAAAAEAAAVnAAACSXRyYWsAAABcdGtoZAAAAAMAAAAAAAAAAAAAAAIA"
            + "AAAAAAAD6AAAAAAAAAAAAAAAAQEAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAACRlZHRz"
            + "AAAAHGVsc3QAAAAAAAAAAQAAA+gAAAQAAAEAAAAAAcFtZGlhAAAAIG1kaGQAAAAAAAAAAAAAAAAAAB9AAAAjQFXEAAAAAAAtaGRs"
            + "cgAAAAAAAAAAc291bgAAAAAAAAAAAAAAAFNvdW5kSGFuZGxlcgAAAAFsbWluZgAAABBzbWhkAAAAAAAAAAAAAAAkZGluZgAAABxk"
            + "cmVmAAAAAAAAAAEAAAAMdXJsIAAAAAEAAAEwc3RibAAAAH5zdHNkAAAAAAAAAAEAAABubXA0YQAAAAAAAAABAAAAAAAAAAAAAQAQ"
            + "AAAAAB9AAAAAAAA2ZXNkcwAAAAADgICAJQACAASAgIAXQBUAAAAAAB9AAAAA/wWAgIAFFYhW5QAGgICAAQIAAAAUYnRydAAAAAAA"
            + "AB9AAAAA/wAAACBzdHRzAAAAAAAAAAIAAAAIAAAEAAAAAAEAAANAAAAAKHN0c2MAAAAAAAAAAgAAAAEAAAABAAAAAQAAAAIAAAAI"
            + "AAAAAQAAABRzdHN6AAAAAAAAAAQAAAAJAAAAGHN0Y28AAAAAAAAAAgAABWMAAAV8AAAAGnNncGQBAAAAcm9sbAAAAAIAAAAB//8A"
            + "AAAcc2JncAAAAAByb2xsAAAAAQAAAAkAAAABAAAAPXVkdGEAAAA1bWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAA"
            + "AAAAAAAAAAAIaWxzdAAAAAhmcmVlAAAAQW1kYXQBGCAHAAAAEWWIhAXzn///D0UAAVefJ114ARggBwEYIAcBGCAHARggBwEYIAcB"
            + "GCAHARggBwEYIAc=";

    private static final String BLANK_HTML = "<!DOCTYPE html><html><head></head><body></body></html>";
    private static final String VMAP = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<vmap:VMAP xmlns:vmap=\"http://www.iab.net/videosuite/vmap\" version=\"1.0\"></vmap:VMAP>";

    private static Body cachedMp3;
    private static Body cachedMp4;

    private Surrogates() {
    }

    /** The stand-in a rule named ($redirect=noopjs...), or null for a name the app doesn't have. */
    public static Body named(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        if (colon > 0) n = n.substring(0, colon);   // "noopjs:10": a priority, for uBlock Origin
        if (n.equals("googlesyndication_adsbygoogle.js") || n.equals("googlesyndication.com/adsbygoogle.js")) {
            return new Body(JS, ADSBYGOOGLE_JS);
        }
        if (n.equals("googletagservices_gpt.js") || n.equals("googletagservices.com/gpt.js")) return new Body(JS, GPT_JS);
        if (n.equals("google-analytics_analytics.js") || n.equals("google-analytics.com/analytics.js")
                || n.equals("google-analytics_ga.js") || n.equals("google-analytics.com/ga.js")) {
            return new Body(JS, ANALYTICS_JS);
        }
        if (n.equals("googletagmanager_gtm.js") || n.equals("googletagmanager.com/gtm.js")) return new Body(JS, GTM_JS);
        if (n.equals("amazon_apstag.js") || n.equals("amazon-adsystem.com/aax2/amzn_ads.js")) return new Body(JS, APSTAG_JS);
        if (n.equals("prebid-ads.js") || n.startsWith("fuckadblock.js") || n.startsWith("nobab")
                || n.startsWith("nofab") || n.equals("sniffadblock.js")) {
            return new Body(JS, NO_DETECTOR_JS);
        }
        if (n.equals("noopframe") || n.equals("noop.html") || n.equals("click2load.html")) {
            return new Body("text/html", BLANK_HTML);
        }
        if (n.equals("nooptext") || n.equals("noop.txt") || n.equals("empty")) return new Body("text/plain", "");
        if (n.equals("noop.css")) return new Body("text/css", "");
        if (n.equals("noopjson") || n.equals("noop.json")) return new Body("application/json", "{}");
        if (n.endsWith(".gif") || n.endsWith(".png")) return new Body("image/gif", GIF);
        if (n.startsWith("noopmp3") || n.endsWith(".mp3")) return mp3();
        if (n.startsWith("noopmp4") || n.endsWith(".mp4") || n.equals("mp4")) return mp4();
        if (n.startsWith("noopvmap") || n.startsWith("noop-vmap")) return new Body("application/xml", VMAP);
        if (n.startsWith("noopvast") || n.startsWith("noop-vast")) {
            String v = n.contains("4") ? "4.0" : n.contains("2") ? "2.0" : "3.0";
            return new Body("application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"" + v + "\"></VAST>");
        }
        if (n.endsWith(".js") || n.startsWith("noopjs") || n.equals("none")) return new Body(JS, "");
        return null;
    }

    /** The stand-in for a blocked address no rule named one for: by the best-known scripts, then by its kind. */
    public static Body forBlocked(String url, int type) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        if (path.endsWith("/adsbygoogle.js")) return new Body(JS, ADSBYGOOGLE_JS);
        if (path.endsWith("/gpt.js") || path.endsWith("/pubads_impl.js")) return new Body(JS, GPT_JS);
        if (path.endsWith("google-analytics.com/analytics.js") || path.endsWith("google-analytics.com/ga.js")) {
            return new Body(JS, ANALYTICS_JS);
        }
        if (path.endsWith("googletagmanager.com/gtm.js") || path.contains("googletagmanager.com/gtag/js")) {
            return new Body(JS, GTM_JS);
        }
        if (path.endsWith("/apstag.js")) return new Body(JS, APSTAG_JS);
        if (path.endsWith("/prebid-ads.js") || path.endsWith("/advertisement.js") || path.endsWith("/ads.js")
                || path.endsWith("/adframe.js") || path.endsWith("/fuckadblock.js") || path.endsWith("/blockadblock.js")) {
            return new Body(JS, NO_DETECTOR_JS);
        }
        if (path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac")) return mp3();
        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")) return mp4();
        if (path.endsWith(".xml") && (u.contains("vast") || u.contains("vmap"))) {
            return u.contains("vmap") ? new Body("application/xml", VMAP)
                    : new Body("application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"></VAST>");
        }
        if ((type & Request.IMAGE) != 0 && type == Request.IMAGE) return new Body("image/gif", GIF);
        if (type == Request.STYLESHEET) return new Body("text/css", "");
        if (type == Request.SUBDOCUMENT || type == Request.DOCUMENT) return new Body("text/html", BLANK_HTML);
        if (type == Request.MEDIA) return mp4();
        if (type == Request.FONT) return new Body("font/woff2", new byte[0]);
        if (type == Request.SCRIPT) return new Body(JS, "");
        return new Body("text/plain", "");   // a fetch, a beacon, or a script whose kind wasn't told
    }

    private static synchronized Body mp3() {
        if (cachedMp3 == null) cachedMp3 = new Body("audio/mpeg", base64(MP3));
        return cachedMp3;
    }

    private static synchronized Body mp4() {
        if (cachedMp4 == null) cachedMp4 = new Body("video/mp4", base64(MP4));
        return cachedMp4;
    }

    /** Base64 to bytes (java.util.Base64 needs Android 8.0, this app runs on 5.0). */
    static byte[] base64(String s) {
        int[] v = new int[128];
        String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < 128; i++) v[i] = -1;
        for (int i = 0; i < 64; i++) v[a.charAt(i)] = i;
        byte[] out = new byte[s.length() * 3 / 4];
        int n = 0;
        int bits = 0;
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || v[c] < 0) continue;   // '=' and anything else
            acc = (acc << 6) | v[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (byte) (acc >> bits);
            }
        }
        byte[] r = new byte[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }
}
