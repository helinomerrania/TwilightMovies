package com.twilightcafe.movies.web.gecko;

import com.twilightcafe.movies.util.Urls;
import com.twilightcafe.movies.web.PageListener;
import com.twilightcafe.movies.web.RequestFilter;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;

import java.util.List;

/**
 * Where the page goes, in Firefox's engine. Each page it opens by itself is asked about first: PageListener
 * .mayGo, then the ad blocker, which shows its own page in place of an ad page (the app's own pages, never). A window the page opens goes
 * to PageListener.popup and never opens. A page that can't load says why (failed, or insecure); the app's
 * error screen shows it, not the engine's.
 */
final class GeckoNavigation implements GeckoSession.NavigationDelegate {
    private final GeckoPage page;
    private final PageListener l;

    GeckoNavigation(GeckoPage page, PageListener l) {
        this.page = page;
        this.l = l;
    }

    @Override
    public void onLocationChange(GeckoSession s, String url, List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                                 Boolean hasUserGesture) {
        page.location = url;
    }

    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest r) {
        String url = r.uri;
        if (r.target == TARGET_WINDOW_NEW) {
            l.popup(url, r.hasUserGesture);
            return GeckoResult.deny();
        }
        if (page.own(url)) return null;                                             // the Twilight screens
        if (!Urls.isWebScheme(url) || !l.mayGo(url)) return GeckoResult.deny();   // intent:, market:...: never
        return url.startsWith("http:") || url.startsWith("https:") ? adPage(url) : null;
    }

    /** Asked on a thread of the app's (the ad blocker may still be loading its rules). */
    private GeckoResult<AllowOrDeny> adPage(final String url) {
        final GeckoResult<AllowOrDeny> verdict = new GeckoResult<AllowOrDeny>();
        Extension.WORK.execute(new Runnable() {
            @Override
            public void run() {
                final RequestFilter.Answer instead = page.filter.check(url, RequestFilter.PAGE, null, url, null);
                verdict.complete(instead != null ? AllowOrDeny.DENY : AllowOrDeny.ALLOW);
                if (instead != null) {
                    page.main.post(new Runnable() {
                        @Override
                        public void run() {
                            page.showInstead(instead);
                        }
                    });
                }
            }
        });
        return verdict;
    }

    /** Never a window of its own (onLoadRequest gave its address to the app). */
    @Override
    public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
        return null;
    }

    /** No error page of the engine's: the load stops, and the app's error screen says why. */
    @Override
    public GeckoResult<String> onLoadError(GeckoSession s, String uri, WebRequestError e) {
        if (e.category == WebRequestError.ERROR_CATEGORY_SECURITY) l.insecure(uri, page.location, e.code);
        else l.failed(uri, describe(e));
        return null;
    }

    /** The engine's words for the error screen's small print. */
    static String describe(WebRequestError e) {
        String name;
        switch (e.code) {
            case WebRequestError.ERROR_UNKNOWN_HOST:
                name = "UNKNOWN_HOST";
                break;
            case WebRequestError.ERROR_CONNECTION_REFUSED:
                name = "CONNECTION_REFUSED";
                break;
            case WebRequestError.ERROR_NET_TIMEOUT:
                name = "NET_TIMEOUT";
                break;
            case WebRequestError.ERROR_NET_RESET:
                name = "NET_RESET";
                break;
            case WebRequestError.ERROR_NET_INTERRUPT:
                name = "NET_INTERRUPT";
                break;
            case WebRequestError.ERROR_OFFLINE:
                name = "OFFLINE";
                break;
            case WebRequestError.ERROR_REDIRECT_LOOP:
                name = "REDIRECT_LOOP";
                break;
            case WebRequestError.ERROR_CORRUPTED_CONTENT:
                name = "CORRUPTED_CONTENT";
                break;
            case WebRequestError.ERROR_CONTENT_CRASHED:
                name = "CONTENT_CRASHED";
                break;
            default:
                name = String.valueOf(e.code);
        }
        return "Firefox: ERROR_" + name;
    }
}
