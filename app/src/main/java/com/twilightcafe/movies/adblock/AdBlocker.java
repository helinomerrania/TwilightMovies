package com.twilightcafe.movies.adblock;

import android.content.Context;
import android.content.SharedPreferences;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Request;
import com.twilightcafe.movies.adblock.surrogates.Surrogates;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The ad blocker, between the website and the internet. Every address a page loads, in any of its frames, is
 * asked about here first ({@link #check}), in either web engine: ads, trackers, pop-under networks and harmful
 * sites get an empty answer or a stand-in (surrogates/) instead, so the page goes on without them. The page's
 * ad boxes are hidden by a script the app puts in the page (assets/scripts/hide-ads.js), which asks
 * {@link #cosmetic}. Where it works and where pages may go: {@link SitePolicy}.
 *
 * The rules (engine/) come from the big filter lists, made into one compact file on GitHub (tools/filters): a
 * copy comes inside the app ({@link FilterFiles}) and a newer one is downloaded ({@link FilterUpdate}).
 */
public final class AdBlocker {
    /** Called on the download's own thread: true if a newer filter file is now in use. */
    public interface Done {
        void updated(boolean newer);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** The name the page's script finds the app under: different on each start, so pages can't look for it. */
    public final String bridgeName = randomName();
    public final SitePolicy sites;

    private final Context ctx;
    private final FilterUpdate update;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final AtomicInteger blockedOnPage = new AtomicInteger();
    private volatile FilterSet set;
    private volatile String pageHost = "";
    private volatile boolean busy;
    private volatile String blockedTitle = "This page is an ad";
    private volatile String blockedText = "";
    private volatile boolean rtl;

    public AdBlocker(Context c, String repo) {
        ctx = c.getApplicationContext();
        final SharedPreferences prefs = ctx.getSharedPreferences("adblock", Context.MODE_PRIVATE);
        sites = new SitePolicy(prefs.getStringSet("off", Collections.<String>emptySet()),
                prefs.getStringSet("allowed", Collections.<String>emptySet()), new SitePolicy.Store() {
                    @Override
                    public void save(String name, Set<String> values) {
                        prefs.edit().putStringSet(name, values).apply();
                    }
                });
        update = new FilterUpdate(ctx, prefs, repo);
    }

    /** Loads the filters, on a thread of its own: the first page waits for them (5 seconds at most). */
    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                use(FilterFiles.loadBest(ctx));
                ready.countDown();
            }
        }, "adblock-load").start();
    }

    /** The filters in use (null until loaded, or if none could be). */
    public FilterSet filters() {
        return set;
    }

    private void use(FilterSet s) {
        set = s;
        sites.useFilters(s);
    }

    private FilterSet await() {
        FilterSet s = set;
        if (s != null) return s;
        try {
            ready.await(5, TimeUnit.SECONDS);   // so no ad slips through at the start
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return set;
    }

    /** The page shown instead of an ad page: its words, in the app's language. */
    public void setTexts(String title, String text, boolean rightToLeft) {
        blockedTitle = title;
        blockedText = text;
        rtl = rightToLeft;
    }

    /** The page now shown (for "blocked here" and the switch for its site). */
    public void setPage(String url) {
        String h = Request.hostOf(url);
        if (!h.isEmpty() && !h.equals(pageHost)) {
            pageHost = h;
            blockedOnPage.set(0);
        }
    }

    public String pageHost() {
        return pageHost;
    }

    public int blockedOnPage() {
        return blockedOnPage.get();
    }

    /** Whether the blocker works on the page now shown. */
    public boolean activeHere() {
        return sites.activeFor(pageHost);
    }

    /** Whether the page may go to url: SitePolicy.GO, AD or JUMP. */
    public int checkJump(String url) {
        return sites.checkJump(url, pageHost);
    }

    /**
     * Every address a page loads, in any of its frames, on the web engine's threads (or the app's): null to load
     * it, or the stand-in to answer instead. kind: what it's for, as Firefox's engine says it ("main_frame" for
     * the page itself: RequestKind.PAGE), or null to work it out from the headers (Android's WebView). frameUrl,
     * pageUrl: the frame and the page it's for, if the engine says (else the Referer, and the page now loading).
     */
    public Surrogates.Body check(String url, String kind, String frameUrl, String pageUrl, Map<String, String> headers) {
        if (url == null || !(url.regionMatches(true, 0, "http:", 0, 5) || url.regionMatches(true, 0, "https:", 0, 6))) {
            return null;
        }
        if (RequestKind.PAGE.equals(kind)) return checkPage(url);
        String main = pageUrl != null ? Request.hostOf(pageUrl) : "";
        if (main.isEmpty()) main = pageHost;
        if (!sites.activeFor(main) || sites.own(Request.hostOf(url))) return null;   // the website's own: its films too
        FilterSet s = await();
        if (s == null) return null;
        String frame = frameUrl != null ? frameUrl : RequestKind.header(headers, "Referer");
        String doc = frame != null ? Request.hostOf(frame) : main;
        int type = kind != null ? RequestKind.named(kind, url) : RequestKind.of(headers, url);
        Request r = new Request(url, doc.isEmpty() ? main : doc, type);
        if (!s.match(r)) return null;
        blockedOnPage.incrementAndGet();
        Surrogates.Body body = Surrogates.named(r.redirect);
        return body != null ? body : Surrogates.forBlocked(url, type);
    }

    /** The page itself: an ad page (the lists say) gets the app's page instead, which says so. */
    private Surrogates.Body checkPage(String url) {
        setPage(url);
        if (!activeHere() || sites.ownOrAllowed(url)) return null;
        FilterSet s = await();
        if (s == null || !s.blocksPage(url)) return null;
        blockedOnPage.incrementAndGet();
        return new Surrogates.Body("text/html", BlockedPage.html(blockedTitle, blockedText, Request.hostOf(url), rtl).getBytes(UTF8));
    }

    /**
     * What the page's ad-hiding script asks (assets/scripts/hide-ads.js), on the engine's threads; only CSS
     * comes back. "page": the rules that hide the site's ad boxes; "generic": those for the classes and ids
     * (keys) the page has.
     */
    public String cosmetic(String question, String host, String keys) {
        FilterSet s = set;
        if (s == null || !activeHere()) return "";
        if ("page".equals(question)) return s.pageCss(host);
        if ("generic".equals(question)) return s.genericCss(host, keys);
        return "";
    }

    /** Looks for newer filters on GitHub (if it's time, or someone asked) and uses them at once, on a thread of its own. */
    public void checkForUpdate(final boolean asked, final Done done) {
        if (busy || !update.due(asked)) return;
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                FilterSet fresh = null;
                try {
                    fresh = update.fetch(asked, await());
                    if (fresh != null) use(fresh);
                } catch (Exception | OutOfMemoryError e) {
                    fresh = null;   // no internet, GitHub busy, a damaged download: tried again later
                } finally {
                    busy = false;
                }
                if (done != null) done.updated(fresh != null);
            }
        }, "adblock-update").start();
    }

    private static String randomName() {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder n = new StringBuilder("_");
        Random rnd = new Random();
        for (int i = 0; i < 10; i++) n.append(letters.charAt(rnd.nextInt(letters.length())));
        return n.toString();
    }
}
