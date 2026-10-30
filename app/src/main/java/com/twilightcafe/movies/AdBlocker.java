package com.twilightcafe.movies;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.twilightcafe.movies.adblock.FilterSet;
import com.twilightcafe.movies.adblock.Request;
import com.twilightcafe.movies.adblock.Surrogates;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The ad blocker, between the website and the internet. Every address a page loads (in any of its frames)
 * is asked about here first ({@link #intercept}): ads, trackers, pop-under networks and harmful sites get an
 * empty answer or a stand-in (Surrogates) instead, so the page goes on without them. The page's ad boxes are
 * hidden by a script the app puts in the page ({@link #COSMETIC_JS}, with {@link Bridge}), and a page can't
 * jump to another site unless it's one of the app's websites or one allowed in the menu ({@link #checkJump}).
 *
 * The rules come from the big filter lists (EasyList, uBlock Origin's, HaGeZi's...), made into one compact
 * file on GitHub (tools/filters): a copy comes inside the app, and a newer one is downloaded from the
 * repository's "filters" release when the box's is two days old. The blocker can be switched off for one
 * website from the menu, if it breaks something there.
 */
final class AdBlocker {
    static final int GO = 0;
    static final int AD = 1;      // the lists say the page is an ad (or a harmful site)
    static final int JUMP = 2;    // another site, not one of the app's websites: stopped

    static final String FILE = "filters.bin";
    private static final long REFRESH_AFTER_MS = 36L * 3600 * 1000;   // a newer filter file is fetched once the box's is older than this
    private static final long CHECK_EVERY_MS = 12L * 3600 * 1000;
    private static final long MAX_FILE = 40L << 20;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Context ctx;
    private final SharedPreferences prefs;
    private final String repo;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final AtomicInteger blockedOnPage = new AtomicInteger();
    private final Set<String> offSites = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> allowedSites = Collections.synchronizedSet(new HashSet<String>());
    private volatile FilterSet set;
    private volatile String pageHost = "";
    private volatile String[] ownSites = new String[0];
    private volatile boolean busy;
    private volatile String blockedTitle = "This page is an ad";
    private volatile String blockedText = "";
    private volatile boolean rtl;

    /** The name the page's script finds the app under: different on each start, so pages can't look for it. */
    final String bridgeName;

    /**
     * Hides the page's ad boxes: its own rules and the generic ones for every page at once, then the generic
     * rules for the classes and ids the page has, asked as they appear (a MutationObserver), a few dozen at a
     * time. Plain old JavaScript: the café's box has a web engine from 2018. Called with the bridge's name.
     */
    static final String COSMETIC_JS =
            "(function(B){var w=window,d=document;if(!d||!d.documentElement||w.__twc===d)return;w.__twc=d;"
            + "var b=w[B];if(!b)return;var done={},q=[],t=0,h=location.hostname;"
            + "function add(c){if(!c)return;var s=d.createElement('style');s.textContent=c;"
            + "(d.head||d.documentElement).appendChild(s);}"
            + "function key(k){if(!done[k]){done[k]=1;q.push(k);}}"
            + "function one(e){var i,c=e.classList;if(e.id)key('#'+e.id);if(c)for(i=0;i<c.length;i++)key('.'+c[i]);}"
            + "function soon(){if(!t){t=1;setTimeout(flush,50);}}"
            + "function scan(n){if(!n||n.nodeType!==1)return;one(n);var l=n.getElementsByTagName('*');"
            + "for(var i=0;i<l.length;i++)one(l[i]);soon();}"
            + "function flush(){t=0;if(!q.length)return;var k=q.join(' ');q=[];try{add(b.generic(h,k));}catch(x){}}"
            + "try{add(b.page(h));}catch(x){}"
            + "scan(d.documentElement);"
            + "if(w.MutationObserver)new w.MutationObserver(function(m){for(var i=0;i<m.length;i++){var r=m[i];"
            + "if(r.type==='attributes'){if(r.target.nodeType===1){one(r.target);soon();}}"
            + "else for(var j=0;j<r.addedNodes.length;j++)scan(r.addedNodes[j]);}})"
            + ".observe(d.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','id']});"
            + "})";

    /**
     * Before the mouse pointer clicks: takes away see-through layers over the whole page that catch the first
     * click to open an ad (up to five, one over the other). Never a video, a frame, something with words or
     * pictures, or anything in a video player. Args: the pointer as a fraction of the screen. Returns how many.
     */
    static final String OVERLAY_JS =
            "(function(nx,ny){var d=document,w=window,v=w.visualViewport,vw=v?v.width:w.innerWidth,"
            + "vh=v?v.height:w.innerHeight,x=(v?v.offsetLeft:0)+nx*vw,y=(v?v.offsetTop:0)+ny*vh,n=0,k,e,s,r,p,see;"
            + "for(k=0;k<5;k++){e=d.elementFromPoint(x,y);if(!e||e===d.body||e===d.documentElement)break;"
            + "if(/^(VIDEO|IFRAME|EMBED|OBJECT|CANVAS|IMG|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(e.tagName))break;"
            + "s=w.getComputedStyle(e);r=e.getBoundingClientRect();"
            + "if(s.position!=='fixed'&&s.position!=='absolute')break;"
            + "if(r.width*r.height<vw*vh*0.4)break;"
            + "see=parseFloat(s.opacity)<0.1||((s.backgroundColor==='transparent'||/rgba\\(.*,\\s*0\\)$/.test(s.backgroundColor))"
            + "&&(!s.backgroundImage||s.backgroundImage==='none'));"
            + "if(!see)break;"
            + "if(e.querySelector&&e.querySelector('video,iframe,img,canvas,svg,button,input,select,textarea'))break;"
            + "if((e.textContent||'').replace(/\\s/g,'').length>0)break;"
            + "p=e.parentElement;while(p&&p!==d.body&&!(p.querySelector&&p.querySelector('video')))p=p.parentElement;"
            + "if(p&&p!==d.body)break;"
            + "e.parentNode.removeChild(e);n++;}"
            + "return n;})";

    AdBlocker(Context c, String repo) {
        this.ctx = c.getApplicationContext();
        this.prefs = c.getSharedPreferences("adblock", Context.MODE_PRIVATE);
        this.repo = repo == null ? "" : repo.trim();
        offSites.addAll(prefs.getStringSet("off", Collections.<String>emptySet()));
        allowedSites.addAll(prefs.getStringSet("allowed", Collections.<String>emptySet()));
        String letters = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder n = new StringBuilder("_");
        Random rnd = new Random();
        for (int i = 0; i < 10; i++) n.append(letters.charAt(rnd.nextInt(letters.length())));
        bridgeName = n.toString();
    }

    /** Loads the newer of the filter file inside the app and the one downloaded last (on a thread of its own). */
    void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                set = loadBest();
                ready.countDown();
            }
        }, "adblock-load").start();
    }

    private FilterSet loadBest() {
        File downloaded = new File(ctx.getFilesDir(), FILE);
        long inApp = created(true);
        long fetched = downloaded.isFile() ? created(false) : -1;
        boolean useDownloaded = fetched > inApp;
        for (int attempt = 0; attempt < 2; attempt++) {
            InputStream in = null;
            try {
                in = useDownloaded ? new FileInputStream(downloaded) : ctx.getAssets().open(FILE);
                return FilterSet.read(in);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                if (useDownloaded) {
                    downloaded.delete();   // damaged: the one inside the app instead
                    useDownloaded = false;
                } else {
                    return null;
                }
            } finally {
                close(in);
            }
        }
        return null;
    }

    /** When a filter file was made, from its first bytes (-1 if it can't be read). */
    private long created(boolean inApp) {
        InputStream in = null;
        try {
            in = inApp ? ctx.getAssets().open(FILE) : new FileInputStream(new File(ctx.getFilesDir(), FILE));
            return FilterSet.readCreated(in);
        } catch (IOException | RuntimeException e) {
            return -1;
        } finally {
            close(in);
        }
    }

    private FilterSet awaitSet() {
        FilterSet s = set;
        if (s != null) return s;
        try {
            ready.await(5, TimeUnit.SECONDS);   // the first page waits for the rules, so no ad slips through at the start
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return set;
    }

    FilterSet filters() {
        return set;
    }

    // ------------------------------------------------------------------ settings

    /** The app's websites (their addresses): their pages are never blocked, and jumps between them are fine. */
    void setSites(String... urls) {
        String[] out = new String[urls.length];
        for (int i = 0; i < urls.length; i++) out[i] = site(Request.hostOf(urls[i]));
        ownSites = out;
    }

    void setTexts(String title, String text, boolean rightToLeft) {
        blockedTitle = title;
        blockedText = text;
        rtl = rightToLeft;
    }

    /** The page now shown (for "blocked here" and the per-site switch). */
    void setPage(String url) {
        String h = Request.hostOf(url);
        if (!h.isEmpty() && !h.equals(pageHost)) {
            pageHost = h;
            blockedOnPage.set(0);
        }
    }

    String pageHost() {
        return pageHost;
    }

    int blockedOnPage() {
        return blockedOnPage.get();
    }

    /** Whether the blocker works on pages of host's site (it's on unless switched off there). */
    boolean activeFor(String host) {
        return host == null || host.isEmpty() || !offSites.contains(site(host));
    }

    void setActive(String host, boolean on) {
        String s = site(host);
        if (s.isEmpty()) return;
        if (on) offSites.remove(s);
        else offSites.add(s);
        save("off", offSites);
    }

    /** From now on, pages may jump to host's site (and pop-ups open there). */
    void allowSite(String host) {
        String s = site(host);
        if (s.isEmpty()) return;
        allowedSites.add(s);
        save("allowed", allowedSites);
    }

    private void save(String key, Set<String> values) {
        HashSet<String> copy;
        synchronized (values) {
            copy = new HashSet<String>(values);
        }
        prefs.edit().putStringSet(key, copy).apply();
    }

    /** The site a host belongs to (www.example.co.uk -> example.co.uk). */
    String site(String host) {
        if (host == null || host.isEmpty()) return "";
        FilterSet s = set;
        if (s != null) return s.registrable(host);
        int last = host.lastIndexOf('.');
        int prev = last > 0 ? host.lastIndexOf('.', last - 1) : -1;
        return prev >= 0 ? host.substring(prev + 1) : host;
    }

    boolean isOwnOrAllowed(String url) {
        String s = site(Request.hostOf(url));
        if (s.isEmpty()) return false;
        for (String own : ownSites) {
            if (s.equals(own)) return true;
        }
        return allowedSites.contains(s);
    }

    // ------------------------------------------------------------------ pages and what they load

    /** Whether the page may go to url (a link, a script's jump, a redirect): GO, AD or JUMP. */
    int checkJump(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (!u.startsWith("http:") && !u.startsWith("https:")) return GO;   // about:blank, javascript:... stay in the page
        String host = Request.hostOf(url);
        if (host.isEmpty() || !activeFor(pageHost)) return GO;
        if (isOwnOrAllowed(url)) return GO;
        FilterSet s = set;
        if (s != null && s.blocksPage(url)) return AD;
        return JUMP;
    }

    /** For WebViewClient.shouldInterceptRequest (on the engine's threads): null to load it, or what to answer instead. */
    WebResourceResponse intercept(WebResourceRequest req) {
        Uri uri = req.getUrl();
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
        String url = uri.toString();
        if (req.isForMainFrame()) {
            setPage(url);
            if (!activeFor(pageHost) || isOwnOrAllowed(url)) return null;
            FilterSet s = awaitSet();
            if (s == null || !s.blocksPage(url)) return null;
            blockedOnPage.incrementAndGet();
            return blockedPage(Request.hostOf(url));
        }
        String main = pageHost;
        if (!activeFor(main)) return null;
        FilterSet s = awaitSet();
        if (s == null) return null;
        Map<String, String> headers = req.getRequestHeaders();
        String referer = header(headers, "Referer");
        String doc = referer != null ? Request.hostOf(referer) : main;
        if (doc.isEmpty()) doc = main;
        int type = typeOf(headers, url);
        Request r = new Request(url, doc, type);
        if (!s.match(r)) return null;
        blockedOnPage.incrementAndGet();
        Surrogates.Body body = Surrogates.named(r.redirect);
        if (body == null) body = Surrogates.forBlocked(url, type);
        return response(body.mime, body.data);
    }

    private static String header(Map<String, String> h, String name) {
        if (h == null) return null;
        for (Map.Entry<String, String> e : h.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** What a request is for, from what the engine asked for (Accept), else from the address's ending. */
    static int typeOf(Map<String, String> h, String url) {
        String accept = header(h, "Accept");
        if (accept != null) {
            String a = accept.toLowerCase(Locale.ROOT);
            if (a.startsWith("text/html") || a.startsWith("application/xhtml")) return Request.SUBDOCUMENT;
            if (a.startsWith("image/")) return Request.IMAGE;
            if (a.startsWith("text/css")) return Request.STYLESHEET;
            if (a.startsWith("video/") || a.startsWith("audio/")) return Request.MEDIA;
            if (a.startsWith("font/") || a.startsWith("application/font")) return Request.FONT;
        }
        if (header(h, "Range") != null) return Request.MEDIA;
        String u = url.toLowerCase(Locale.ROOT);
        int cut = u.length();
        int q = u.indexOf('?');
        if (q >= 0) cut = q;
        int hash = u.indexOf('#');
        if (hash >= 0 && hash < cut) cut = hash;
        String p = u.substring(0, cut);
        if (p.endsWith(".js") || p.endsWith(".mjs")) return Request.SCRIPT;
        if (p.endsWith(".css")) return Request.STYLESHEET;
        if (p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".webp")
                || p.endsWith(".svg") || p.endsWith(".ico") || p.endsWith(".avif") || p.endsWith(".bmp")) {
            return Request.IMAGE;
        }
        if (p.endsWith(".woff2") || p.endsWith(".woff") || p.endsWith(".ttf") || p.endsWith(".otf") || p.endsWith(".eot")) {
            return Request.FONT;
        }
        if (p.endsWith(".mp4") || p.endsWith(".m3u8") || p.endsWith(".ts") || p.endsWith(".m4s") || p.endsWith(".mpd")
                || p.endsWith(".webm") || p.endsWith(".mp3") || p.endsWith(".m4a") || p.endsWith(".aac")) {
            return Request.MEDIA;
        }
        return Request.UNKNOWN;
    }

    private static WebResourceResponse response(String mime, byte[] data) {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-store");
        return new WebResourceResponse(mime, "UTF-8", 200, "OK", h, new ByteArrayInputStream(data));
    }

    private WebResourceResponse blockedPage(String host) {
        String page = "<!DOCTYPE html><html dir=\"" + (rtl ? "rtl" : "ltr") + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>"
                + "html,body{height:100%;margin:0;background:#0b0e2d;color:#eae6f2;font-family:sans-serif}"
                + "body{display:flex;align-items:center;justify-content:center;text-align:center}"
                + "h1{font-size:30px;margin:0 0 12px}p{font-size:19px;color:#b9b4cf;margin:0}"
                + "small{display:block;margin-top:16px;color:#8e8aa5;direction:ltr}</style></head><body><div>"
                + "<h1>" + escape(blockedTitle) + "</h1><p>" + escape(blockedText) + "</p><small>" + escape(host)
                + "</small></div></body></html>";
        return response("text/html", page.getBytes(UTF8));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** What the page's script asks (on the engine's JavaBridge thread). Only CSS comes back. */
    final class Bridge {
        @JavascriptInterface
        public String page(String host) {
            FilterSet s = set;
            return s == null || !activeFor(pageHost) ? "" : s.pageCss(host);
        }

        @JavascriptInterface
        public String generic(String host, String keys) {
            FilterSet s = set;
            return s == null || !activeFor(pageHost) ? "" : s.genericCss(host, keys);
        }
    }

    Bridge bridge() {
        return new Bridge();
    }

    String cosmeticScript() {
        return COSMETIC_JS + "('" + bridgeName + "')";
    }

    // ------------------------------------------------------------------ newer filters from GitHub

    interface Done {
        /** Called on the download's own thread: true if a newer filter file is now in use. */
        void updated(boolean newer);
    }

    /**
     * Looks at the repository's "filters" release and, when its file is newer (and the box's is two days old,
     * or someone asked), downloads it, checks it (size, SHA-256, and that it reads) and uses it from then on.
     */
    void checkForUpdate(final boolean asked, final Done done) {
        if (repo.isEmpty() || busy) return;
        long since = System.currentTimeMillis() - prefs.getLong("checked", 0);
        if (!asked && since >= 0 && since < CHECK_EVERY_MS) return;
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean newer = false;
                try {
                    newer = update(asked);
                } catch (Exception | OutOfMemoryError e) {
                    newer = false;   // no internet, GitHub busy, a damaged download: tried again later
                } finally {
                    busy = false;
                }
                if (done != null) done.updated(newer);
            }
        }, "adblock-update").start();
    }

    private boolean update(boolean asked) throws Exception {
        prefs.edit().putLong("checked", System.currentTimeMillis()).apply();
        String base = "https://github.com/" + repo + "/releases/download/filters/";
        JSONObject j = new JSONObject(new String(get(base + "filters.json", 1 << 16, null), UTF8));
        if (j.optInt("format") != 1) return false;   // a newer app reads it; this one keeps its own
        long created = j.getLong("created");
        long size = j.getLong("size");
        String sha = j.getString("sha256");
        awaitSet();
        FilterSet now = set;
        long have = now != null ? now.created : 0;
        if (created <= have) return false;
        if (!asked && now != null && created - have < REFRESH_AFTER_MS) return false;   // fewer downloads on mobile data
        if (size <= 0 || size > MAX_FILE) return false;
        File part = new File(ctx.getFilesDir(), FILE + ".part");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        OutputStream out = new FileOutputStream(part);
        try {
            get(base + "filters.bin", MAX_FILE, new Sink(out, md));
        } finally {
            out.close();
        }
        if (part.length() != size || !hex(md.digest()).equalsIgnoreCase(sha)) {
            part.delete();
            return false;
        }
        FilterSet fresh;
        InputStream in = new FileInputStream(part);
        try {
            fresh = FilterSet.read(in);
        } finally {
            in.close();
        }
        File file = new File(ctx.getFilesDir(), FILE);
        if (!part.renameTo(file)) {
            part.delete();
            return false;
        }
        set = fresh;
        return true;
    }

    /** Where a download goes: a file, counted into a digest. */
    private static final class Sink {
        final OutputStream out;
        final MessageDigest md;

        Sink(OutputStream out, MessageDigest md) {
            this.out = out;
            this.md = md;
        }
    }

    /** The bytes at an https address (into sink, if given; else returned), at most max. Throws unless 200. */
    private static byte[] get(String address, long max, Sink sink) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "TwilightMovies-app");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream mem = sink == null ? new ByteArrayOutputStream() : null;
            try {
                byte[] buf = new byte[65536];
                long got = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    got += n;
                    if (got > max) throw new IOException("too big");
                    if (sink != null) {
                        sink.out.write(buf, 0, n);
                        sink.md.update(buf, 0, n);
                    } else {
                        mem.write(buf, 0, n);
                    }
                }
            } finally {
                in.close();
            }
            return mem != null ? mem.toByteArray() : null;
        } finally {
            c.disconnect();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            s.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
        return s.toString();
    }

    private static void close(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
