package com.twilightcafe.movies.loading;

import android.os.SystemClock;
import android.view.View;

import com.twilightcafe.movies.R;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.util.Urls;
import com.twilightcafe.movies.web.Page;

/**
 * When the website can't be reached: the error screen says why (no internet, no answer, a security problem)
 * and tries again by itself, after 10 seconds, 20, 40, then every minute (Backoff), and at once when the
 * internet comes back (Connection). It stays up until a page loads.
 */
public final class ErrorScreen {
    /** What the error screen needs from the rest of the app. */
    public interface Host {
        /** Where the website opens (tried when no address failed). */
        String address();

        /** Someone is typing an address: the countdown waits. */
        boolean typing();

        /** A panel is over it: it doesn't take the remote. */
        boolean panelOpen();

        /** It's coming up: the start picture, the progress bar, a film full screen and the pointer go. */
        void showing();

        /** It's gone: the page gets the remote back. */
        void focusPage();

        void changeAddress();
    }

    private final Screen screen;
    private final Page page;
    private final Host host;
    private final ErrorPanel panel;
    private final Connection net;
    private final Backoff wait = new Backoff();
    private String failedUrl;
    private String insecureUrl;
    private boolean retrying;
    private long retryAt;
    private int attempt;
    private int failedAttempt = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!panel.shown() || !page.alive()) return;
            long now = SystemClock.uptimeMillis();
            if (host.typing()) retryAt = Math.max(retryAt, now + 5000);
            long left = retryAt - now;
            if (left <= 0) {
                retryNow();
                return;
            }
            panel.status(screen.t(R.string.error_retry_in, (int) ((left + 999) / 1000)));
            screen.main.postDelayed(this, Math.min(1000, left));
        }
    };

    public ErrorScreen(Screen s, Page page, final Host host) {
        this.screen = s;
        this.page = page;
        this.host = host;
        this.panel = new ErrorPanel(s, new ErrorPanel.Listener() {
            @Override
            public void retry() {
                tryAgain();
            }

            @Override
            public void changeAddress() {
                host.changeAddress();
            }
        });
        this.net = new Connection(s.activity, s.main, new Runnable() {
            @Override
            public void run() {
                networkBack();
            }
        });
    }

    public View view() {
        return panel.view();
    }

    public boolean shown() {
        return panel.shown();
    }

    /** The address that failed, while the error screen is up. */
    public String failedUrl() {
        return failedUrl;
    }

    public void failed(String url, String why) {
        if (url != null && url.equals(insecureUrl)) return;   // already said, as a security problem
        show(url, why, false);
    }

    /** A security error: said when it's the page itself (or the website), not something in it. */
    public void insecure(String url, String pageUrl, int code) {
        String home = host.address();
        if (url == null || !(Urls.sameHost(url, pageUrl != null ? pageUrl : home) || Urls.sameHost(url, home))) return;
        insecureUrl = url;
        show(url, "SSL error " + code, true);
    }

    private void show(String url, String why, boolean secure) {
        failedUrl = url;
        failedAttempt = attempt;
        String reason = secure ? screen.t(R.string.error_secure)
                : !net.online() ? screen.t(R.string.error_no_internet) : screen.t(R.string.error_no_answer);
        panel.set(screen.t(R.string.error_title, Urls.hostOf(url != null ? url : host.address())),
                reason + "\n\n" + screen.t(R.string.error_advice), why != null ? why : "");
        host.showing();
        boolean was = panel.shown();
        panel.show();
        if (!was && !host.panelOpen()) panel.focus();
        retryAt = SystemClock.uptimeMillis() + wait.next() * 1000L;
        screen.main.removeCallbacks(tick);
        screen.main.post(tick);
    }

    /** "Try again" (or Reload in the menu): at once, and the waits from the start. */
    public void tryAgain() {
        wait.reset();
        retryNow();
    }

    private void retryNow() {
        if (!page.alive()) return;
        screen.main.removeCallbacks(tick);
        attempt++;
        retrying = true;
        panel.status(screen.t(R.string.error_retrying));
        page.load(failedUrl != null ? failedUrl : host.address());
    }

    /** A page finished loading: the error screen goes if it was the try, or another page. */
    public void finished(String url) {
        if (retrying) {
            retrying = false;
            if (failedAttempt != attempt) hide();   // the try worked
        } else if (failedUrl == null || (url != null && !url.equals(failedUrl))) {
            hide();
        }
    }

    public void hide() {
        screen.main.removeCallbacks(tick);
        failedUrl = null;
        insecureUrl = null;
        retrying = false;
        wait.reset();
        if (!panel.shown()) return;
        panel.hide();
        if (!host.panelOpen()) host.focusPage();
    }

    public void focus() {
        panel.focus();
    }

    /** The internet is back: if the error screen is up, the next try is in a moment. */
    private void networkBack() {
        if (!panel.shown() || retrying) return;
        wait.reset();
        retryAt = SystemClock.uptimeMillis() + 1500;
        screen.main.removeCallbacks(tick);
        screen.main.post(tick);
    }

    public void stop() {
        net.stop();
    }
}
