package com.twilightcafe.movies.web.gecko;

import com.twilightcafe.movies.web.PageListener;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * What Firefox's engine says about the page, passed on to the app's PageListener: its loading, its first
 * paint, full screen, crashes and its history; and what it asks to do: videos may play at once, with sound,
 * and protected (DRM) ones too; the camera, the microphone, the location and notifications never.
 */
final class GeckoEvents implements GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate,
        GeckoSession.HistoryDelegate, GeckoSession.PermissionDelegate {
    private final GeckoPage page;
    private final PageListener l;
    private String started;   // the address the page now loading started with
    private int loads;        // pages started, so a late "finished" is never for a newer one
    private boolean blank;    // it's the engine's own empty page (about:blank), not one to tell the app about

    GeckoEvents(GeckoPage page, PageListener l) {
        this.page = page;
        this.l = l;
    }

    // ProgressDelegate

    @Override
    public void onPageStart(GeckoSession s, String url) {
        blank = url == null || url.startsWith("about:");
        if (blank) return;
        started = url;
        loads++;
        l.started(url);
    }

    /** A page that failed is finished after its error comes (GeckoNavigation.onLoadError), as in Android's WebView. */
    @Override
    public void onPageStop(GeckoSession s, boolean success) {
        if (blank) return;
        if (success) {
            l.finished(page.location);
            return;
        }
        final String url = started;
        final int load = loads;
        page.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (load == loads && page.alive()) l.finished(url);
            }
        }, 400);
    }

    @Override
    public void onProgressChange(GeckoSession s, int progress) {
        if (!blank) l.progress(progress);
    }

    // ContentDelegate

    @Override
    public void onFirstContentfulPaint(GeckoSession s) {
        if (!blank) l.shown(page.location);
    }

    /** The page's own view shows the video full screen; the app only takes the remote for it. */
    @Override
    public void onFullScreen(final GeckoSession s, boolean fullScreen) {
        if (fullScreen) l.fullscreen(null, s::exitFullScreen);
        else l.fullscreenEnd();
    }

    @Override
    public void onCrash(GeckoSession s) {
        l.crashed();
    }

    /** Android closed the page's engine for memory. */
    @Override
    public void onKill(GeckoSession s) {
        l.crashed();
    }

    // HistoryDelegate

    @Override
    public void onHistoryStateChange(GeckoSession s, GeckoSession.HistoryDelegate.HistoryList list) {
        String[] urls = new String[list.size()];
        for (int i = 0; i < urls.length; i++) urls[i] = list.get(i).getUri();
        page.setHistory(urls, list.getCurrentIndex());
    }

    // PermissionDelegate

    @Override
    public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission p) {
        boolean ok = p.permission == PERMISSION_AUTOPLAY_AUDIBLE || p.permission == PERMISSION_AUTOPLAY_INAUDIBLE
                || p.permission == PERMISSION_MEDIA_KEY_SYSTEM_ACCESS;
        return GeckoResult.fromValue(ok ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
    }

    @Override
    public void onAndroidPermissionsRequest(GeckoSession s, String[] permissions, Callback callback) {
        callback.reject();
    }

    @Override
    public void onMediaPermissionRequest(GeckoSession s, String uri, MediaSource[] video, MediaSource[] audio,
                                         MediaCallback callback) {
        callback.reject();
    }
}
