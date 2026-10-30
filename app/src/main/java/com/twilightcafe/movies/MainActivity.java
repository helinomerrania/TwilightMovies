package com.twilightcafe.movies;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.twilightcafe.movies.adblock.FilterSet;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Twilight Movies: KurdFilm's TV website (app.kurdfilm.krd/tv) full screen, driven by the TV remote.
 *
 * Remote:
 *  - Arrows / OK go to the website (it has its own TV interface).
 *  - Optional mouse pointer (menu → Mouse pointer): arrows move it, OK clicks,
 *    pushing against the screen edge scrolls.
 *  - BACK goes back; BACK twice on the first page exits.
 *  - Hold OK, hold BACK, or press MENU for the app menu.
 *
 * The ad blocker (AdBlocker, adblock/) sees every address a page loads, stops jumps to other sites, and
 * hides ad boxes; it can be switched off for one website in the menu.
 *
 * Also: the TV stays awake while a film plays, the app keeps trying when the website can't be reached
 * (and at once when the internet comes back), new versions come from GitHub (Updater), and the remote
 * guide shows once. The app's own screens are in Kurdish (res/values-b+ckb) unless English is chosen
 * (res/values): Language in the menu, or the button on the remote guide.
 */
public class MainActivity extends Activity {

    static final String DEFAULT_URL = "https://app.kurdfilm.krd/tv";
    static final int ACCENT = 0xFFFFB07A;   // the sunset in Twilight's pictures
    static final String KURDISH = "ckb";    // Central Kurdish (Sorani): res/values-b+ckb/strings.xml
    static final String ENGLISH = "en";     // res/values/strings.xml

    private static final String PREFS = "settings";
    private static final String KEY_URL = "home_url";
    private static final String KEY_POINTER = "pointer_mode";
    private static final String KEY_GUIDE = "guide_shown";
    private static final String KEY_LANGUAGE = "language";
    private static final String STATE_URL = "resume_url";

    private static final long OK_LONG_PRESS_MS = 650;
    private static final long BACK_LONG_PRESS_MS = 800;
    private static final long MOVE_DELAY_MS = 170;
    private static final float SPEED_MIN_DP = 260f;   // pointer speed when a key is first held (dp/s)
    private static final float SPEED_MAX_DP = 1100f;  // top speed, reached after about a second
    private static final float NUDGE_DP = 7f;         // a single short press
    private static final int ROUTE_PAGE = 1;
    private static final int ROUTE_POINTER = 2;
    private static final long AWAKE_CHECK_MS = 20000;                 // how often to look whether a film plays
    private static final int RETRY_FIRST_S = 10;                      // automatic retries: 10 s, 20, 40, then every 60
    private static final int RETRY_MAX_S = 60;
    private static final long UPDATE_FIRST_MS = 20000;                // the first look for a new version, after start
    private static final long UPDATE_EVERY_MS = 6L * 3600 * 1000;     // then this often while the app is open
    private static final long UPDATE_MIN_GAP_MS = 3L * 3600 * 1000;   // and never more often than this

    /**
     * Scrolls whatever is under the pointer: the innermost scrollable box first, then the page.
     * Args: pointer x/y as a fraction of the screen, scroll amount as a fraction of the screen.
     */
    static final String SCROLL_JS =
            "(function(nx,ny,fx,fy){var d=document,w=window,v=w.visualViewport,"
            + "vw=v?v.width:w.innerWidth,vh=v?v.height:w.innerHeight,"
            + "x=(v?v.offsetLeft:0)+nx*vw,y=(v?v.offsetTop:0)+ny*vh,dx=fx*vw,dy=fy*vh,"
            + "e=d.elementFromPoint(x,y),re=/(auto|scroll|overlay)/;"
            + "for(;e&&e!==d.body&&e!==d.documentElement;e=e.parentElement){"
            + "var s=getComputedStyle(e),"
            + "cx=!!dx&&re.test(s.overflowX)&&e.scrollWidth>e.clientWidth+1,"
            + "cy=!!dy&&re.test(s.overflowY)&&e.scrollHeight>e.clientHeight+1;"
            + "if(!cx&&!cy)continue;"
            + "var sl=e.scrollLeft,st=e.scrollTop,snap=s.scrollSnapType&&s.scrollSnapType!=='none';"
            + "if(snap&&e.scrollBy){var now=Date.now();if(e.__tvSnap&&now-e.__tvSnap<450)return 'wait';"
            + "e.__tvSnap=now;e.scrollBy({left:cx?(dx>0?1:-1)*e.clientWidth*0.8:0,"
            + "top:cy?(dy>0?1:-1)*e.clientHeight*0.8:0,behavior:'smooth'});return 'snap';}"
            + "if(cx)e.scrollLeft=sl+dx;if(cy)e.scrollTop=st+dy;"
            + "if(e.scrollLeft!==sl||e.scrollTop!==st)return 'el';}"
            + "w.scrollBy(dx,dy);return 'win';})";

    /**
     * Video helper for the main frame.
     *  'q' remember the state of the main video (returns 'ok' or 'none');
     *      with strict=1 it refuses when a link/button/field has focus.
     *  't' toggle play/pause, but only if the page did not already change it since 'q'.
     *  's' seek by n seconds, but only if the page did not already seek since 'q'.
     *  'p' play, 'z' pause.
     */
    static final String MEDIA_JS =
            "(function(a,n,strict){var d=document,w=window,"
            + "f=d.fullscreenElement||d.webkitFullscreenElement||null,v=null,s=w.__tvm,i,x,r,ar,b=0,vs;"
            + "function pick(){if(f){v=f.tagName==='VIDEO'?f:(f.querySelector?f.querySelector('video'):null);}"
            + "if(!v){vs=d.getElementsByTagName('video');for(i=0;i<vs.length;i++){x=vs[i];"
            + "r=x.getBoundingClientRect();ar=r.width*r.height;"
            + "if(ar>0&&!x.paused&&!x.ended){v=x;break;}if(ar>b){b=ar;v=x;}}}}"
            + "function play(){var p=v.play();if(p&&p.catch)p.catch(function(){});}"
            + "if(a==='q'){pick();var ae=d.activeElement;"
            + "if(!v||(strict&&ae&&ae!==v&&ae!==f&&/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(ae.tagName))){"
            + "w.__tvm=null;return 'none';}"
            + "w.__tvm={v:v,p:v.paused,t:v.currentTime,at:Date.now()};return 'ok';}"
            + "if(a==='t'||a==='s'){if(!s||!s.v||!d.contains(s.v))return 'none';v=s.v;w.__tvm=null;"
            + "if(a==='t'){if(v.paused!==s.p)return 'page';if(v.paused||v.ended)play();else v.pause();"
            + "return v.paused?'paused':'playing';}"
            + "var el=(Date.now()-s.at)/1000,exp=s.t+(v.paused?0:el*(v.playbackRate||1));"
            + "if(Math.abs(v.currentTime-exp)>2.5)return 'page';"
            + "var t=v.currentTime+n;if(isFinite(v.duration)&&v.duration>0)t=Math.min(t,v.duration-1);"
            + "v.currentTime=Math.max(0,t);return 'seeked';}"
            + "pick();if(!v)return 'none';"
            + "if(a==='p'){if(v.paused)play();return 'playing';}"
            + "if(a==='z'){if(!v.paused)v.pause();return 'paused';}"
            + "return 'none';})";

    static final String PAUSE_ALL_JS =
            "(function(){var v=document.getElementsByTagName('video');"
            + "for(var i=0;i<v.length;i++){try{v[i].pause();}catch(e){}}})()";

    /** 1 while a video on the page is playing (to keep the TV awake), else 0. */
    static final String PLAYING_JS =
            "(function(){var v=document.getElementsByTagName('video');"
            + "for(var i=0;i<v.length;i++){var x=v[i];if(!x.paused&&!x.ended&&x.readyState>2)return 1;}"
            + "return 0;})()";

    private static long lastRendererCrashAt;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SparseIntArray keyRoutes = new SparseIntArray();
    private SharedPreferences prefs;
    private float density;
    private Resources texts;
    private boolean kurdish;
    private boolean kurdishReady;
    private Updater updater;
    private int myBuild;
    private int engineMajor;

    private FrameLayout root;
    private FrameLayout fullscreenHolder;
    private WebView web;
    private CursorView cursor;
    private ProgressBar progress;
    private ImageView splash;
    private TextView osd;
    private View errorPanel;
    private View menuPanel;
    private View addressPanel;
    private View guidePanel;
    private TextView errorTitle;
    private TextView errorDetail;
    private TextView errorStatus;
    private TextView errorCode;
    private TextView menuSubtitle;
    private Button retryButton;
    private Button homeButton;
    private Button pointerButton;
    private Button languageButton;
    private Button updateButton;
    private Button adblockButton;
    private Button allowButton;
    private TextView menuFilters;
    private Button guideClose;
    private EditText addressInput;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String homeUrl;
    private boolean pointerMode;
    private String errorUrl;
    private String sslErrorUrl;
    private String resumeUrl;
    private long lastBackAt;
    private boolean splashHiding;
    private boolean engineHintShown;
    private boolean updateAnnounced;
    private boolean awaitingInstallPermission;

    private AdBlocker adBlocker;
    private boolean cosmeticsAsked;
    private String lastJump;          // the last jump to another site the ad blocker stopped (the menu can allow it)
    private long lastBlockNoteAt;

    private int retryDelay = RETRY_FIRST_S;
    private long retryAt;
    private int attempt;
    private int failedAttempt = -1;
    private boolean retrying;
    private ConnectivityManager.NetworkCallback netCallback;

    private boolean keepAwake;
    private int quietChecks;

    private KeyEvent okDown;
    private KeyEvent mediaDown;
    private boolean okLongFired;
    private boolean backTracking;
    private boolean backLongFired;

    private String verifyAction;
    private int verifySecs;
    private int verifyKeyCode;
    private long verifyAt;

    private int dirX;
    private int dirY;
    private boolean moving;
    private long moveStart;
    private long lastTick;
    private float pendingScrollX;
    private float pendingScrollY;
    private boolean scrollPosted;
    private long lastHoverAt;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        homeUrl = prefs.getString(KEY_URL, DEFAULT_URL);
        pointerMode = prefs.getBoolean(KEY_POINTER, false);
        setupTexts();
        myBuild = BuildConfig.VERSION_CODE;
        engineMajor = engineVersion();
        updater = new Updater(this, BuildConfig.REPO, myBuild);
        adBlocker = new AdBlocker(this, BuildConfig.REPO);
        adBlocker.setSites(homeUrl);
        adBlocker.setTexts(t(R.string.page_blocked_title), t(R.string.page_blocked_text), kurdish);
        adBlocker.start();   // the rules load while the splash shows; the first page waits for them

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        web = new WebView(this);
        root.addView(web, matchParent());

        fullscreenHolder = new FrameLayout(this);
        fullscreenHolder.setBackgroundColor(Color.BLACK);
        fullscreenHolder.setVisibility(View.GONE);
        root.addView(fullscreenHolder, matchParent());

        splash = new ImageView(this);
        splash.setImageResource(R.drawable.splash_art);
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(splash, matchParent());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        root.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));

        errorPanel = buildErrorPanel();
        root.addView(errorPanel, matchParent());
        menuPanel = buildMenuPanel();
        root.addView(menuPanel, matchParent());
        addressPanel = buildAddressPanel();
        root.addView(addressPanel, matchParent());
        guidePanel = buildGuidePanel();
        root.addView(guidePanel, matchParent());

        osd = new TextView(this);
        osd.setTextColor(Color.WHITE);
        osd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        osd.setGravity(Gravity.CENTER);
        osd.setMaxWidth(dp(760));
        osd.setPadding(dp(22), dp(10), dp(22), dp(10));
        osd.setBackground(rounded(0xE6161433, dp(24)));
        osd.setVisibility(View.GONE);
        FrameLayout.LayoutParams osdParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        osdParams.topMargin = dp(30);
        root.addView(osd, osdParams);

        cursor = new CursorView(this, ACCENT);
        root.addView(cursor, matchParent());

        setupWebView();
        watchNetwork();

        String resume = state != null ? state.getString(STATE_URL) : null;
        loadPage(resume != null ? resume : homeUrl);
        web.requestFocus();

        ui.postDelayed(hideSplash, 8000);
        if (!prefs.getBoolean(KEY_GUIDE, false)) ui.postDelayed(firstGuide, 3000);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);   // the box's own font-size setting would otherwise enlarge text and break TV layouts
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebViewClient(new PageClient());
        web.setWebChromeClient(new ChromeClient());
        web.addJavascriptInterface(adBlocker.bridge(), adBlocker.bridgeName);   // for hiding ad boxes (CSS only)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.resumeTimers();
        }
        ui.removeCallbacks(awakeCheck);
        ui.postDelayed(awakeCheck, 5000);
        ui.removeCallbacks(updateCheck);
        ui.postDelayed(updateCheck, UPDATE_FIRST_MS);
        if (awaitingInstallPermission) {
            // back from "allow this app to install apps": carry on with the update by itself
            awaitingInstallPermission = false;
            if (updater.readyBuild() > myBuild && updater.canInstall(this)) {
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        installUpdate();
                    }
                }, 700);
            }
        }
    }

    @Override
    protected void onPause() {
        stopPointer();
        ui.removeCallbacks(awakeCheck);
        ui.removeCallbacks(updateCheck);
        setKeepAwake(false);
        if (web != null) {
            web.evaluateJavascript(PAUSE_ALL_JS, null);
            web.onPause();
            web.pauseTimers();
        }
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        // Only the address: a full WebView state can be too large for the system to keep.
        String url = web != null ? web.getUrl() : resumeUrl;
        if (url != null) out.putString(STATE_URL, url);
    }

    @Override
    protected void onDestroy() {
        stopPointer();
        ui.removeCallbacksAndMessages(null);
        if (netCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(netCallback);
            } catch (RuntimeException ignored) {
                // already gone
            }
            netCallback = null;
        }
        if (web != null) {
            root.removeView(web);
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            // A system overlay took over: forget half-finished key presses.
            stopPointer();
            ui.removeCallbacks(okLongPress);
            ui.removeCallbacks(backLongPress);
            okDown = null;
            mediaDown = null;
            backTracking = false;
        }
    }

    // ------------------------------------------------------------------ texts and language

    private void setupTexts() {
        kurdishReady = kurdishAvailable();
        // Kurdish unless English was chosen: the language of the café, and of the website
        kurdish = kurdishReady && !ENGLISH.equals(prefs.getString(KEY_LANGUAGE, ""));
        texts = kurdish ? localized(KURDISH) : getResources();
    }

    private Resources localized(String language) {
        Configuration c = new Configuration(getResources().getConfiguration());
        c.setLocale(new Locale(language));
        return createConfigurationContext(c).getResources();
    }

    /** True once a Kurdish translation is in the app (res/values-b+ckb/strings.xml). */
    private boolean kurdishAvailable() {
        try {
            return !localized(KURDISH).getString(R.string.language_name)
                    .equals(localized(ENGLISH).getString(R.string.language_name));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The other language, by its own name ("English" in Kurdish, "کوردی" in English). */
    private String otherLanguageName() {
        return localized(kurdish ? ENGLISH : KURDISH).getString(R.string.language_name);
    }

    private String t(int id) {
        return texts.getString(id);
    }

    private String t(int id, Object... args) {
        return texts.getString(id, args);
    }

    private String languageLabel() {
        return t(R.string.menu_language) + ": " + t(R.string.language_name) + "  ·  " + otherLanguageName();
    }

    private void toggleLanguage() {
        prefs.edit().putString(KEY_LANGUAGE, kurdish ? ENGLISH : KURDISH).apply();
        closePanels();
        recreate();   // every panel again in the other language; the page reloads where it was
    }

    // ------------------------------------------------------------------ remote keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        final int code = e.getKeyCode();
        final int action = e.getAction();
        if (web == null || (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)) {
            return super.dispatchKeyEvent(e);
        }

        if (code == KeyEvent.KEYCODE_MENU) {
            if (action == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (isVisible(menuPanel)) closePanels();
                else showMenu();
            }
            return true;
        }
        if (isBackKey(code)) return onBackKey(e);

        if (isOkKey(code) && action == KeyEvent.ACTION_UP && okLongFired) {
            okLongFired = false;   // the release that follows a long press (the menu is already open)
            return true;
        }
        if (panelOpen()) return super.dispatchKeyEvent(e);

        if (isOkKey(code)) return onOkKey(e);
        if (isDpadKey(code)) return onDpadKey(e);
        if (isMediaKey(code)) return onMediaKey(e);
        if (isPageKey(code)) {
            if (pointerMode && customView == null) {
                if (action == KeyEvent.ACTION_DOWN && e.getRepeatCount() % 4 == 0) {
                    boolean up = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_CHANNEL_UP;
                    scrollAt(cursor.tipX(), cursor.tipY(), 0f, up ? -0.8f : 0.8f);
                }
                return true;
            }
            sendToPage(e);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean onOkKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okDown = new KeyEvent(e);
                okLongFired = false;
                ui.removeCallbacks(okLongPress);
                ui.postDelayed(okLongPress, OK_LONG_PRESS_MS);
            }
            return true;
        }
        ui.removeCallbacks(okLongPress);
        KeyEvent down = okDown;
        okDown = null;
        if (down == null || e.isCanceled()) return true;

        boolean full = customView != null;
        if (pointerMode && (!full || cursor.isPointerVisible())) {
            if (cursor.isPointerVisible()) pointerClick(cursor.tipX(), cursor.tipY());
            showPointer();
            return true;
        }
        if (full) {
            // Watching a video: let the player handle OK; if it ignores it, toggle play/pause.
            pressWithFallback(down, new KeyEvent(e), "t", 0, 1);
        } else {
            sendToPage(down);
            sendToPage(e);
        }
        return true;
    }

    private final Runnable okLongPress = new Runnable() {
        @Override
        public void run() {
            okDown = null;
            okLongFired = true;
            showMenu();
        }
    };

    private boolean onBackKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                backTracking = true;
                backLongFired = false;
                ui.removeCallbacks(backLongPress);
                ui.postDelayed(backLongPress, BACK_LONG_PRESS_MS);
            }
            return true;
        }
        ui.removeCallbacks(backLongPress);
        boolean tracked = backTracking;
        backTracking = false;
        if (backLongFired) {
            backLongFired = false;
            return true;
        }
        if (!tracked || e.isCanceled()) return true;
        goBack();
        return true;
    }

    private final Runnable backLongPress = new Runnable() {
        @Override
        public void run() {
            backLongFired = true;
            if (!isVisible(menuPanel)) showMenu();
        }
    };

    private boolean onDpadKey(KeyEvent e) {
        final int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            boolean full = customView != null;
            boolean usePointer = pointerMode && (!full || cursor.isPointerVisible()
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN);
            keyRoutes.put(code, usePointer ? ROUTE_POINTER : ROUTE_PAGE);
        }
        int route = keyRoutes.get(code, 0);
        if (e.getAction() == KeyEvent.ACTION_UP) keyRoutes.delete(code);

        if (route == ROUTE_POINTER) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) startPointer(code);
            } else {
                endPointer(code);
            }
        } else if (route == ROUTE_PAGE) {
            sendToPage(e);
        }
        return true;
    }

    private boolean onMediaKey(KeyEvent e) {
        final int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) mediaDown = new KeyEvent(e);
            return true;
        }
        KeyEvent down = mediaDown;
        mediaDown = null;
        if (down == null || down.getKeyCode() != code || e.isCanceled()) return true;
        KeyEvent up = new KeyEvent(e);
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                pressWithFallback(down, up, "t", 0, 0);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                pressWithFallback(down, up, "s", 10, 0);
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                pressWithFallback(down, up, "s", -10, 0);
                break;
            default:
                // play, pause, stop, next, previous: page first, fallback in onKeyNotUsedByPage
                sendToPage(down);
                sendToPage(up);
                break;
        }
        return true;
    }

    /** Sends a key to whatever shows the page (the full-screen video view, or the WebView). */
    private void sendToPage(KeyEvent e) {
        View t = target();
        if (t == null) return;
        if (!t.hasFocus()) t.requestFocus();
        t.dispatchKeyEvent(e);
    }

    /**
     * Gives the page the first chance to use a key. Only if the page reports the key as unused
     * AND the video state did not change does the app play/pause or seek itself, so a player that
     * already handles the remote never gets a double action.
     */
    private void pressWithFallback(final KeyEvent down, final KeyEvent up,
                                   final String action, final int secs, final int strict) {
        if (web == null) return;
        final boolean[] sent = {false};
        final Runnable sendPlain = new Runnable() {
            @Override
            public void run() {
                if (sent[0]) return;
                sent[0] = true;
                verifyAction = null;
                sendToPage(down);
                sendToPage(up);
            }
        };
        ui.postDelayed(sendPlain, 400);
        web.evaluateJavascript(MEDIA_JS + "('q',0," + strict + ")", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                if (sent[0]) return;
                sent[0] = true;
                ui.removeCallbacks(sendPlain);
                boolean armed = result != null && result.contains("ok");
                verifyAction = armed ? action : null;
                verifySecs = secs;
                verifyKeyCode = down.getKeyCode();
                verifyAt = SystemClock.uptimeMillis();
                sendToPage(down);
                sendToPage(up);
            }
        });
    }

    /** Called (asynchronously) when the page did not use a key we passed to it. */
    private void onKeyNotUsedByPage(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || web == null) return;
        final int code = e.getKeyCode();
        if (verifyAction != null && code == verifyKeyCode
                && SystemClock.uptimeMillis() - verifyAt < 2000) {
            final String action = verifyAction;
            final int secs = verifySecs;
            verifyAction = null;
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runMedia(action, secs);
                }
            }, 300);
            return;
        }
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                runMedia("p", 0);
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                runMedia("z", 0);
                break;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (customView == null && e.getRepeatCount() % 4 == 0) {
                    scrollAt(root.getWidth() / 2f, root.getHeight() / 2f, 0f, -0.8f);
                }
                break;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (customView == null && e.getRepeatCount() % 4 == 0) {
                    scrollAt(root.getWidth() / 2f, root.getHeight() / 2f, 0f, 0.8f);
                }
                break;
            default:
                break;
        }
    }

    private void runMedia(final String action, final int secs) {
        if (web == null) return;
        web.evaluateJavascript(MEDIA_JS + "('" + action + "'," + secs + ",0)", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String r) {
                if (r == null) return;
                if (r.contains("seeked")) {
                    showOsd(secs > 0 ? t(R.string.osd_forward, secs) : t(R.string.osd_rewind, -secs), 900);
                } else if (r.contains("paused") && !"p".equals(action)) {
                    showOsd(t(R.string.osd_paused), 900);
                } else if (r.contains("playing") && !"z".equals(action)) {
                    showOsd(t(R.string.osd_playing), 900);
                }
            }
        });
    }

    private void goBack() {
        if (isVisible(addressPanel) || isVisible(menuPanel) || isVisible(guidePanel)) {
            closePanels();
            return;
        }
        if (customView != null) {
            leaveFullscreen();
            return;
        }
        if (web != null && goBackInHistory()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAt < 2500) {
            finish();
            return;
        }
        lastBackAt = now;
        showOsd(t(R.string.osd_back_again), 2500);
    }

    /** Goes to the previous page, skipping entries that are the same address (redirects). */
    private boolean goBackInHistory() {
        WebBackForwardList list = web.copyBackForwardList();
        WebHistoryItem current = list.getCurrentItem();
        if (current == null) return false;
        if (isVisible(errorPanel) && errorUrl != null && !errorUrl.equals(current.getUrl())) {
            hideError();   // the failed page never entered the history: just show the current page again
            return true;
        }
        int index = list.getCurrentIndex();
        String here = current.getUrl();
        for (int i = index - 1; i >= 0; i--) {
            WebHistoryItem item = list.getItemAtIndex(i);
            String url = item != null ? item.getUrl() : null;
            if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.equals(here)) {
                continue;
            }
            hideError();
            web.goBackOrForward(i - index);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ mouse pointer

    private void startPointer(int code) {
        int dx = 0;
        int dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        else if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
        if (dx != 0) dirX = dx;
        if (dy != 0) dirY = dy;
        ui.removeCallbacks(hidePointer);
        cursor.setPointerVisible(true);
        if (!moving) {
            moving = true;
            moveStart = SystemClock.uptimeMillis();
            lastTick = moveStart;
            nudge(dx, dy);
            cursor.postOnAnimation(moveTick);
        }
    }

    private void endPointer(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && dirX < 0) dirX = 0;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && dirX > 0) dirX = 0;
        else if (code == KeyEvent.KEYCODE_DPAD_UP && dirY < 0) dirY = 0;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && dirY > 0) dirY = 0;
        if (dirX == 0 && dirY == 0) {
            moving = false;
            cursor.removeCallbacks(moveTick);
            flushScroll();
            hover(cursor.tipX(), cursor.tipY(), true);
            showPointer();
        }
    }

    private void stopPointer() {
        dirX = 0;
        dirY = 0;
        moving = false;
        keyRoutes.clear();
        if (cursor != null) cursor.removeCallbacks(moveTick);
        if (root != null) flushScroll();
    }

    private final Runnable moveTick = new Runnable() {
        @Override
        public void run() {
            if (dirX == 0 && dirY == 0) {
                moving = false;
                return;
            }
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastTick) / 1000f);
            lastTick = now;
            long held = now - moveStart;
            if (held > MOVE_DELAY_MS) {
                float t = Math.min(1f, (held - MOVE_DELAY_MS) / 1100f);
                float speed = (SPEED_MIN_DP + (SPEED_MAX_DP - SPEED_MIN_DP) * t * t) * density;
                float sx = dirX * speed * dt;
                float sy = dirY * speed * dt;
                if (dirX != 0 && dirY != 0) {
                    sx *= 0.7071f;
                    sy *= 0.7071f;
                }
                movePointer(sx, sy);
            }
            cursor.postOnAnimation(this);
        }
    };

    private void nudge(int dx, int dy) {
        float w = root.getWidth();
        float h = root.getHeight();
        float x = cursor.tipX();
        float y = cursor.tipY();
        boolean atEdge = (dx < 0 && x <= 0.5f) || (dx > 0 && x >= w - 1.5f)
                || (dy < 0 && y <= 0.5f) || (dy > 0 && y >= h - 1.5f);
        if (atEdge) {
            if (customView == null) scrollAt(x, y, dx * 0.15f, dy * 0.15f);
            return;
        }
        float n = NUDGE_DP * density;
        movePointer(dx * n, dy * n);
    }

    private void movePointer(float dx, float dy) {
        float w = root.getWidth();
        float h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        float x = cursor.tipX() + dx;
        float y = cursor.tipY() + dy;
        float overX = 0f;
        float overY = 0f;
        if (x < 0) {
            overX = x;
            x = 0;
        } else if (x > w - 1) {
            overX = x - (w - 1);
            x = w - 1;
        }
        if (y < 0) {
            overY = y;
            y = 0;
        } else if (y > h - 1) {
            overY = y - (h - 1);
            y = h - 1;
        }
        cursor.moveTo(x, y);
        hover(x, y, false);
        if (customView == null && (overX != 0f || overY != 0f)) {
            // Pushing against the edge of the screen scrolls the page.
            pendingScrollX += overX;
            pendingScrollY += overY;
            if (!scrollPosted) {
                scrollPosted = true;
                ui.postDelayed(scrollFlush, 50);
            }
        }
    }

    private final Runnable scrollFlush = new Runnable() {
        @Override
        public void run() {
            scrollPosted = false;
            flushScroll();
        }
    };

    private void flushScroll() {
        ui.removeCallbacks(scrollFlush);
        scrollPosted = false;
        if (pendingScrollX == 0f && pendingScrollY == 0f) return;
        float w = Math.max(1, root.getWidth());
        float h = Math.max(1, root.getHeight());
        float fx = pendingScrollX / w;
        float fy = pendingScrollY / h;
        pendingScrollX = 0f;
        pendingScrollY = 0f;
        scrollAt(cursor.tipX(), cursor.tipY(), fx, fy);
    }

    private void scrollAt(float x, float y, float fx, float fy) {
        if (web == null) return;
        float w = Math.max(1, root.getWidth());
        float h = Math.max(1, root.getHeight());
        web.evaluateJavascript(SCROLL_JS + "(" + num(x / w) + "," + num(y / h) + ","
                + num(fx) + "," + num(fy) + ")", null);
    }

    private void hover(float x, float y, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastHoverAt < 32) return;
        lastHoverAt = now;
        View t = target();
        if (t == null) return;
        float[] p = toTarget(t, x, y);
        MotionEvent ev = pointerEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, p[0], p[1],
                MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE);
        t.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    /** A tap at the pointer, delivered like a finger touch so every kind of page reacts to it. */
    private void clickAt(float x, float y, boolean feedback) {
        final View t = target();
        if (t == null) return;
        final float[] p = toTarget(t, x, y);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent down = pointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, p[0], p[1],
                MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN);
        t.dispatchTouchEvent(down);
        down.recycle();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                long now = SystemClock.uptimeMillis();
                MotionEvent up = pointerEvent(downTime, now, MotionEvent.ACTION_UP, p[0], p[1],
                        MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN);
                t.dispatchTouchEvent(up);
                up.recycle();
            }
        }, 40);
        if (feedback) cursor.pulse();
    }

    private static MotionEvent pointerEvent(long downTime, long eventTime, int action,
                                            float x, float y, int toolType, int source) {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = toolType;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = 1f;
        coords.size = 1f;
        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{props},
                new MotionEvent.PointerCoords[]{coords},
                0, 0, 1f, 1f, 0, 0, source, 0);
    }

    private float[] toTarget(View t, float x, float y) {
        int[] a = new int[2];
        int[] b = new int[2];
        root.getLocationInWindow(a);
        t.getLocationInWindow(b);
        return new float[]{x + a[0] - b[0], y + a[1] - b[1]};
    }

    private View target() {
        return customView != null ? customView : web;
    }

    private void showPointer() {
        cursor.setPointerVisible(true);
        ui.removeCallbacks(hidePointer);
        ui.postDelayed(hidePointer, customView != null ? 3000 : 15000);
    }

    private final Runnable hidePointer = new Runnable() {
        @Override
        public void run() {
            if (dirX == 0 && dirY == 0) cursor.setPointerVisible(false);
        }
    };

    // ------------------------------------------------------------------ films: full screen, and the TV awake

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        stopPointer();
        cursor.setPointerVisible(false);
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        fullscreenHolder.addView(view, matchParent());
        fullscreenHolder.setVisibility(View.VISIBLE);
        quietChecks = 0;
        setKeepAwake(true);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus();
    }

    private void leaveFullscreen() {
        if (customView == null) return;
        View view = customView;
        WebChromeClient.CustomViewCallback callback = customViewCallback;
        customView = null;
        customViewCallback = null;
        stopPointer();
        cursor.setPointerVisible(false);
        fullscreenHolder.removeView(view);
        fullscreenHolder.setVisibility(View.GONE);
        if (callback != null) {
            try {
                callback.onCustomViewHidden();
            } catch (Throwable ignored) {
                // WebView already left full screen
            }
        }
        if (!panelOpen()) focusContent();
    }

    /**
     * Keeps the TV from dimming, sleeping or starting its screensaver while a film plays, inside the page
     * or full screen. Sound counts too, for players the page's own script can't see (in a frame of
     * another site). Two quiet looks in a row (40 seconds) and the TV may sleep again.
     */
    private final Runnable awakeCheck = new Runnable() {
        @Override
        public void run() {
            if (web == null) return;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            final boolean sound = am != null && am.isMusicActive();
            web.evaluateJavascript(PLAYING_JS, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String r) {
                    if (sound || "1".equals(r)) {
                        quietChecks = 0;
                        setKeepAwake(true);
                    } else if (++quietChecks >= 2) {
                        setKeepAwake(false);
                    }
                }
            });
            ui.postDelayed(this, AWAKE_CHECK_MS);
        }
    };

    private void setKeepAwake(boolean on) {
        if (on == keepAwake) return;
        keepAwake = on;
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ------------------------------------------------------------------ pages, and when they can't load

    private void loadPage(String url) {
        hideError();
        if (web != null) web.loadUrl(url);
    }

    private void reload() {
        closePanels();
        leaveFullscreen();
        if (web == null) return;
        if (isVisible(errorPanel)) {
            retryDelay = RETRY_FIRST_S;
            retryNow();
        } else {
            web.reload();
        }
    }

    private void showError(String url, String why, boolean secure) {
        errorUrl = url;
        failedAttempt = attempt;
        errorTitle.setText(t(R.string.error_title, hostOf(url != null ? url : homeUrl)));
        String reason = secure ? t(R.string.error_secure)
                : !isOnline() ? t(R.string.error_no_internet)
                : t(R.string.error_no_answer);
        errorDetail.setText(reason + "\n\n" + t(R.string.error_advice));
        errorCode.setText(why != null ? why : "");   // the engine's own words, small, for whoever fixes it
        splash.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        if (customView != null) leaveFullscreen();
        stopPointer();
        cursor.setPointerVisible(false);
        boolean wasShown = isVisible(errorPanel);
        errorPanel.setVisibility(View.VISIBLE);
        if (!wasShown && !isVisible(menuPanel) && !isVisible(addressPanel) && !isVisible(guidePanel)) {
            retryButton.requestFocus();
        }
        scheduleRetry();
    }

    private void scheduleRetry() {
        retryAt = SystemClock.uptimeMillis() + retryDelay * 1000L;
        retryDelay = Math.min(RETRY_MAX_S, retryDelay * 2);
        ui.removeCallbacks(retryTick);
        ui.post(retryTick);
    }

    /** The countdown on the error screen; at zero the page is tried again, with the screen still up. */
    private final Runnable retryTick = new Runnable() {
        @Override
        public void run() {
            if (!isVisible(errorPanel) || web == null) return;
            long now = SystemClock.uptimeMillis();
            if (isVisible(addressPanel)) retryAt = Math.max(retryAt, now + 5000);   // someone is typing an address
            long left = retryAt - now;
            if (left <= 0) {
                retryNow();
                return;
            }
            errorStatus.setText(t(R.string.error_retry_in, (int) ((left + 999) / 1000)));
            ui.postDelayed(this, Math.min(1000, left));
        }
    };

    private void retryNow() {
        if (web == null) return;
        ui.removeCallbacks(retryTick);
        attempt++;
        retrying = true;
        errorStatus.setText(t(R.string.error_retrying));
        web.loadUrl(errorUrl != null ? errorUrl : homeUrl);
    }

    private void hideError() {
        ui.removeCallbacks(retryTick);
        errorUrl = null;
        sslErrorUrl = null;
        retrying = false;
        retryDelay = RETRY_FIRST_S;
        if (isVisible(errorPanel)) {
            errorPanel.setVisibility(View.GONE);
            if (!isVisible(menuPanel) && !isVisible(addressPanel) && !isVisible(guidePanel)) focusContent();
        }
    }

    private void focusContent() {
        if (isVisible(errorPanel)) {
            retryButton.requestFocus();
            return;
        }
        View t = target();
        if (t != null) t.requestFocus();
    }

    private void watchNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            onNetworkBack();
                        }
                    });
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb);
            netCallback = cb;
        } catch (RuntimeException e) {
            netCallback = null;   // some boxes refuse; the countdown still retries
        }
    }

    /** The internet is back: if the error screen is up, try again in a moment instead of waiting. */
    private void onNetworkBack() {
        if (!isVisible(errorPanel) || retrying) return;
        retryDelay = RETRY_FIRST_S;
        retryAt = SystemClock.uptimeMillis() + 1500;
        ui.removeCallbacks(retryTick);
        ui.post(retryTick);
    }

    @SuppressWarnings("deprecation")
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities c = n != null ? cm.getNetworkCapabilities(n) : null;
                return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private final Runnable hideSplash = new Runnable() {
        @Override
        public void run() {
            if (splash.getVisibility() != View.VISIBLE || splashHiding) return;
            splashHiding = true;
            splash.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    splash.setVisibility(View.GONE);
                }
            });
        }
    };

    private void hideSplashSoon() {
        ui.post(hideSplash);
    }

    private boolean openPopup(boolean userGesture, Message resultMsg) {
        // Pop-ups: only follow ones the viewer clicked that stay on this website; ad pop-ups are dropped.
        if (!userGesture || resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
            return false;
        }
        final WebView popup = new WebView(this);
        final boolean[] done = {false};
        final Runnable dispose = new Runnable() {
            @Override
            public void run() {
                try {
                    popup.stopLoading();
                    popup.destroy();
                } catch (Throwable ignored) {
                    // already gone
                }
            }
        };
        popup.setWebViewClient(new WebViewClient() {
            private void take(String url) {
                if (done[0] || url == null || url.startsWith("about:")) return;
                done[0] = true;
                if (web != null && isWebScheme(url) && (sameSite(url) || adBlocker.isOwnOrAllowed(url))) {
                    loadPage(url);
                } else {
                    showOsd(t(R.string.osd_popup_blocked), 1500);
                }
                ui.post(dispose);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                take(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                take(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                take(url);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (!done[0]) {
                    done[0] = true;
                    ui.post(dispose);
                }
                return true;
            }
        });
        ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
        resultMsg.sendToTarget();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!done[0]) {
                    done[0] = true;
                    dispose.run();
                }
            }
        }, 15000);
        return true;
    }

    private void onRendererGone(WebView view) {
        if (view != web) return;
        // The page's engine crashed (often low memory on TV boxes): rebuild instead of closing.
        long now = SystemClock.uptimeMillis();
        boolean again = lastRendererCrashAt != 0 && now - lastRendererCrashAt < 20000;
        lastRendererCrashAt = now;
        resumeUrl = again ? homeUrl : view.getUrl();
        if (customView != null) {
            fullscreenHolder.removeView(customView);
            fullscreenHolder.setVisibility(View.GONE);
            customView = null;
            customViewCallback = null;
        }
        setKeepAwake(false);
        root.removeView(view);
        view.destroy();
        web = null;
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) recreate();
            }
        });
    }

    private final class PageClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (!isWebScheme(url)) return true;   // block intent:, market:, etc. (ads love them)
            return !allowJump(url);               // Android 5 and 6: the page itself only
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (!isWebScheme(url)) return true;
            return request.isForMainFrame() && !allowJump(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return adBlocker.intercept(request);   // every address of the page and its frames (on the engine's threads)
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
            cosmeticsAsked = false;
            adBlocker.setPage(url);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            hideSplashSoon();
            injectCosmetics();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            hideSplashSoon();
            injectCosmetics();
            if (retrying) {
                retrying = false;
                if (failedAttempt != attempt) hideError();   // the automatic retry worked
            } else if (errorUrl == null || (url != null && !url.equals(errorUrl))) {
                hideError();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            showError(failingUrl, description, false);   // Android 5.x
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            String url = request.getUrl().toString();
            if (url.equals(sslErrorUrl)) return;
            CharSequence d = error.getDescription();
            showError(url, d != null ? d.toString() : "error " + error.getErrorCode(), false);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            String url = error.getUrl();
            String page = view.getUrl() != null ? view.getUrl() : homeUrl;
            if (url != null && (sameHost(url, page) || sameHost(url, homeUrl))) {
                sslErrorUrl = url;
                showError(url, "SSL error " + error.getPrimaryError(), true);
            }
        }

        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            // Never hand keys back to Android here: everything the page doesn't use ends with us.
            onKeyNotUsedByPage(event);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            onRendererGone(view);
            return true;
        }
    }

    private final class ChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            if (newProgress >= 70) hideSplashSoon();
            if (newProgress >= 30 && !cosmeticsAsked) {
                cosmeticsAsked = true;   // early, so ad boxes don't flash (again when it's shown and when it's done)
                injectCosmetics();
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            enterFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() {
            leaveFullscreen();
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            // Avoids the grey "play" placeholder WebView shows on videos without a poster.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            return openPopup(isUserGesture, resultMsg);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            // Allow protected (DRM) video if a player asks; never camera or microphone.
            String[] wanted = request.getResources();
            if (wanted != null) {
                for (String r : wanted) {
                    if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                        return;
                    }
                }
            }
            request.deny();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage m) {
            // A script of the website's own that this box's engine can't even read: the engine is too old.
            if (!engineHintShown && engineMajor > 0 && engineMajor < 80 && m != null
                    && m.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                    && m.message() != null && m.message().contains("SyntaxError")
                    && (m.sourceId() == null || m.sourceId().isEmpty() || sameSite(m.sourceId()))) {
                engineHintShown = true;
                showOsd(t(R.string.osd_old_engine, engineMajor), 10000);
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ new versions

    private final Runnable updateCheck = new Runnable() {
        @Override
        public void run() {
            adBlocker.checkForUpdate(false, null);   // the ad filters: looked at twice a day, downloaded every two days
            if (updater == null || !updater.enabled()) return;
            long since = System.currentTimeMillis() - updater.lastCheck();
            if (since < 0 || since >= UPDATE_MIN_GAP_MS) {
                updater.checkInBackground(ui, new Updater.Listener() {
                    @Override
                    public void onUpdateChecked(int result, int readyBuild) {
                        onUpdateResult(result, false);
                    }
                });
            } else if (updater.readyBuild() > myBuild) {
                onUpdateResult(Updater.READY, false);
            }
            ui.postDelayed(this, UPDATE_EVERY_MS);
        }
    };

    private void onUpdateResult(int result, boolean asked) {
        if (updateButton != null) updateButton.setText(updateLabel());
        if (isFinishing() || web == null) return;
        boolean ready = result == Updater.READY && updater.readyBuild() > myBuild;
        if (asked) {
            ui.removeCallbacks(hideOsd);
            osd.setVisibility(View.GONE);
            if (ready) {
                // someone asked and there is one: the menu, with "Install the new version" chosen
                showMenu();
                updateButton.requestFocus();
            } else {
                showOsd(result == Updater.UP_TO_DATE ? t(R.string.osd_up_to_date, myBuild) : t(R.string.osd_check_failed), 4000);
            }
        } else if (ready && !updateAnnounced && !keepAwake && customView == null) {
            // found by itself: said once, and never over a film (the menu shows it anyway)
            updateAnnounced = true;
            showOsd(t(R.string.osd_update_ready), 7000);
        }
    }

    private String updateLabel() {
        int ready = updater.readyBuild();
        return ready > myBuild ? t(R.string.menu_install_update, ready) : t(R.string.menu_check_update);
    }

    private void onUpdateButton() {
        if (updater.readyBuild() > myBuild) {
            installUpdate();
            return;
        }
        closePanels();
        showOsd(t(R.string.osd_checking), 20000);
        updater.checkInBackground(ui, new Updater.Listener() {
            @Override
            public void onUpdateChecked(int result, int readyBuild) {
                onUpdateResult(result, true);
            }
        });
        adBlocker.checkForUpdate(true, new AdBlocker.Done() {   // and the ad filters, whatever their age
            @Override
            public void updated(boolean newer) {
                if (!newer) return;
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        FilterSet f = adBlocker.filters();
                        if (f != null && !isFinishing()) showOsd(t(R.string.osd_filters_updated, formatCount(f.rules)), 4000);
                    }
                }, 4500);   // after the app's own answer
            }
        });
    }

    private void installUpdate() {
        closePanels();
        int r = updater.install(this);
        if (updateButton != null) updateButton.setText(updateLabel());
        switch (r) {
            case Updater.STARTED:
                showOsd(t(R.string.osd_update_started), 6000);
                break;
            case Updater.NEED_PERMISSION:
                awaitingInstallPermission = true;
                showOsd(t(R.string.osd_update_permission), 10000);
                break;
            case Updater.UNSUPPORTED:
                showOsd(t(R.string.osd_update_unsupported), 8000);
                break;
            case Updater.NOTHING_WAITING:
                onUpdateButton();   // the download is gone: look again
                break;
            default:
                showOsd(t(R.string.osd_update_failed), 5000);
                break;
        }
    }

    // ------------------------------------------------------------------ menu, guide & address panels

    private void showMenu() {
        if (web == null) return;
        stopPointer();
        okDown = null;
        if (isVisible(addressPanel)) {
            hideKeyboard();
            addressPanel.setVisibility(View.GONE);
        }
        guidePanel.setVisibility(View.GONE);
        String url = web.getUrl();
        String sub = shortUrl(url != null ? url : homeUrl) + "  ·  " + t(R.string.menu_build, myBuild);
        if (engineMajor > 0) sub += "  ·  " + t(R.string.menu_engine, engineMajor);
        menuSubtitle.setText(sub);
        FilterSet f = adBlocker.filters();
        menuFilters.setText(f != null ? t(R.string.menu_filters, formatCount(f.rules), formatDate(f.created)) : "");
        menuFilters.setVisibility(f != null ? View.VISIBLE : View.GONE);
        pointerButton.setText(pointerLabel());
        adblockButton.setText(adblockLabel());
        allowButton.setText(lastJump != null ? t(R.string.menu_allow_jump, adBlocker.site(hostOf(lastJump))) : "");
        allowButton.setVisibility(lastJump != null ? View.VISIBLE : View.GONE);
        updateButton.setText(updateLabel());
        cursor.setPointerVisible(false);
        menuPanel.setVisibility(View.VISIBLE);
        homeButton.requestFocus();
    }

    // ------------------------------------------------------------------ the ad blocker

    private String adblockLabel() {
        return adBlocker.activeFor(adBlocker.pageHost())
                ? t(R.string.menu_adblock_on, adBlocker.blockedOnPage()) : t(R.string.menu_adblock_off);
    }

    /** On or off for the website now shown (remembered for it), then the page again. */
    private void toggleAdBlocker() {
        String host = adBlocker.pageHost();
        if (host.isEmpty()) host = hostOf(homeUrl);
        boolean on = !adBlocker.activeFor(host);
        adBlocker.setActive(host, on);
        closePanels();
        showOsd(t(on ? R.string.osd_adblock_on : R.string.osd_adblock_off, adBlocker.site(host)), 3000);
        if (web != null) web.reload();
    }

    /** Lets the page go where the ad blocker stopped it last, and to that site from then on. */
    private void allowLastJump() {
        String url = lastJump;
        lastJump = null;
        closePanels();
        if (url == null || web == null) return;
        String host = hostOf(url);
        adBlocker.allowSite(host);
        showOsd(t(R.string.osd_jump_allowed, adBlocker.site(host)), 3000);
        web.loadUrl(url);
    }

    /** Whether the page may go to url (a link, a script's jump, a redirect); says so on screen if not. */
    private boolean allowJump(String url) {
        int verdict = adBlocker.checkJump(url);
        if (verdict == AdBlocker.GO) return true;
        String host = adBlocker.site(hostOf(url));
        long now = SystemClock.uptimeMillis();
        boolean say = now - lastBlockNoteAt > 3000;   // ad scripts try again and again: said once
        if (verdict == AdBlocker.JUMP) lastJump = url;
        if (say) {
            lastBlockNoteAt = now;
            showOsd(verdict == AdBlocker.AD ? t(R.string.osd_ad_page_blocked, host) : t(R.string.osd_jump_blocked, host), 5000);
        }
        return false;
    }

    /** The ad-hiding script, into the page now shown (it runs once a page, however often it's put in). */
    private void injectCosmetics() {
        if (web == null || adBlocker.filters() == null || !adBlocker.activeFor(adBlocker.pageHost())) return;
        web.evaluateJavascript(adBlocker.cosmeticScript(), null);
    }

    /** A click with the mouse pointer, after taking away see-through ad layers over the page there. */
    private void pointerClick(final float x, final float y) {
        if (web == null || customView != null || !adBlocker.activeFor(adBlocker.pageHost())) {
            clickAt(x, y, true);
            return;
        }
        final boolean[] done = {false};
        final Runnable click = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                clickAt(x, y, true);
            }
        };
        ui.postDelayed(click, 250);   // if the page doesn't answer at once, click anyway
        float w = Math.max(1, root.getWidth());
        float h = Math.max(1, root.getHeight());
        web.evaluateJavascript(AdBlocker.OVERLAY_JS + "(" + num(x / w) + "," + num(y / h) + ")", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String removed) {
                ui.removeCallbacks(click);
                click.run();
            }
        });
    }

    private String formatCount(int n) {
        return NumberFormat.getIntegerInstance(textLocale()).format(n);
    }

    private String formatDate(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, textLocale()).format(new Date(millis));
    }

    private Locale textLocale() {
        return kurdish ? new Locale(KURDISH) : Locale.getDefault();
    }

    private void closePanels() {
        boolean wasOpen = isVisible(menuPanel) || isVisible(addressPanel) || isVisible(guidePanel);
        if (isVisible(addressPanel)) {
            hideKeyboard();
            addressPanel.setVisibility(View.GONE);
        }
        menuPanel.setVisibility(View.GONE);
        guidePanel.setVisibility(View.GONE);
        if (wasOpen) focusContent();
    }

    private boolean panelOpen() {
        return isVisible(menuPanel) || isVisible(addressPanel) || isVisible(errorPanel) || isVisible(guidePanel);
    }

    private void togglePointer() {
        pointerMode = !pointerMode;
        prefs.edit().putBoolean(KEY_POINTER, pointerMode).apply();
        pointerButton.setText(pointerLabel());
        closePanels();
        if (pointerMode) {
            showPointer();
            showOsd(t(R.string.osd_pointer_on), 3500);
        } else {
            cursor.setPointerVisible(false);
            showOsd(t(R.string.osd_pointer_off), 3500);
        }
    }

    private String pointerLabel() {
        return pointerMode ? t(R.string.menu_pointer_on) : t(R.string.menu_pointer_off);
    }

    private void showGuide() {
        if (web == null) return;
        stopPointer();
        okDown = null;
        menuPanel.setVisibility(View.GONE);
        cursor.setPointerVisible(false);
        guidePanel.setVisibility(View.VISIBLE);
        guideClose.requestFocus();
    }

    /** The remote guide, once, on the first start: when the page is up and nothing else is on screen. */
    private final Runnable firstGuide = new Runnable() {
        private int tries;

        @Override
        public void run() {
            if (web == null) return;
            if (panelOpen() || customView != null || isVisible(splash)) {
                if (++tries < 12) ui.postDelayed(this, 4000);
                return;
            }
            prefs.edit().putBoolean(KEY_GUIDE, true).apply();
            showGuide();
        }
    };

    private void showAddress() {
        menuPanel.setVisibility(View.GONE);
        guidePanel.setVisibility(View.GONE);
        stopPointer();
        cursor.setPointerVisible(false);
        addressInput.setText(homeUrl);
        addressInput.setSelection(addressInput.getText().length());
        addressPanel.setVisibility(View.VISIBLE);
        addressInput.requestFocus();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isVisible(addressPanel)) return;
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(addressInput, 0);
            }
        }, 300);
    }

    private void submitAddress() {
        String url = normalizeUrl(addressInput.getText().toString());
        if (url == null) {
            showOsd(t(R.string.osd_bad_address), 2500);
            return;
        }
        setHome(url);
    }

    private void setHome(String url) {
        homeUrl = url;
        prefs.edit().putString(KEY_URL, url).apply();
        adBlocker.setSites(url);
        closePanels();
        leaveFullscreen();
        loadPage(url);
        showOsd(t(R.string.osd_opening, shortUrl(url)), 2000);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && addressInput != null) imm.hideSoftInputFromWindow(addressInput.getWindowToken(), 0);
    }

    private View buildMenuPanel() {
        FrameLayout scrim = scrim(0xB3000000);
        LinearLayout card = card();
        card.addView(label(getString(R.string.app_name), 24, Color.WHITE, true));
        menuSubtitle = label("", 14, 0xFFA8A4BC, false);
        menuSubtitle.setSingleLine(true);
        menuSubtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        menuSubtitle.setTextDirection(kurdish ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sub.topMargin = dp(2);
        card.addView(menuSubtitle, sub);
        menuFilters = label("", 13, 0xFF8E8AA5, false);   // the ad blocker's rules: how many, and from when
        menuFilters.setSingleLine(true);
        menuFilters.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.topMargin = dp(2);
        fl.bottomMargin = dp(10);
        card.addView(menuFilters, fl);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        homeButton = tvButton(t(R.string.menu_home), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanels();
                leaveFullscreen();
                loadPage(homeUrl);
            }
        });
        list.addView(homeButton, rowParams(4));
        list.addView(tvButton(t(R.string.menu_reload), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reload();
            }
        }), rowParams(8));
        pointerButton = tvButton(pointerLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePointer();
            }
        });
        list.addView(pointerButton, rowParams(8));
        adblockButton = tvButton(t(R.string.menu_adblock_off), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAdBlocker();
            }
        });
        list.addView(adblockButton, rowParams(8));
        allowButton = tvButton("", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allowLastJump();
            }
        });
        allowButton.setVisibility(View.GONE);
        list.addView(allowButton, rowParams(8));
        list.addView(tvButton(t(R.string.menu_guide), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGuide();
            }
        }), rowParams(8));
        languageButton = tvButton(kurdishReady ? languageLabel() : "", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLanguage();
            }
        });
        languageButton.setVisibility(kurdishReady ? View.VISIBLE : View.GONE);
        list.addView(languageButton, rowParams(8));
        list.addView(tvButton(t(R.string.menu_address), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddress();
            }
        }), rowParams(8));
        updateButton = tvButton(updateLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUpdateButton();
            }
        });
        updateButton.setVisibility(updater.enabled() ? View.VISIBLE : View.GONE);
        list.addView(updateButton, rowParams(8));
        list.addView(tvButton(t(R.string.menu_exit), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }), rowParams(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = label(t(R.string.menu_hint), 13, 0xFF8E8AA5, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(14);
        card.addView(hint, hp);

        scrim.addView(card, cardParams(dp(440)));
        return scrim;
    }

    private View buildGuidePanel() {
        FrameLayout scrim = scrim(0xD9000000);
        LinearLayout card = card();
        card.addView(label(t(R.string.guide_title), 24, Color.WHITE, true));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int[][] lines = {
                {R.string.guide_arrows_key, R.string.guide_arrows},
                {R.string.guide_ok_key, R.string.guide_ok},
                {R.string.guide_back_key, R.string.guide_back},
                {R.string.guide_menu_key, R.string.guide_menu},
                {R.string.guide_media_key, R.string.guide_media},
                {R.string.guide_channel_key, R.string.guide_channel},
        };
        for (int[] line : lines) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView key = label(t(line[0]), 16, ACCENT, true);
            TextView what = label(t(line[1]), 16, 0xFFEAE6F2, false);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
            kp.setMarginEnd(dp(14));
            row.addView(key, kp);
            row.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(12);
            rows.addView(row, rp);
        }
        TextView tip = label(t(R.string.guide_pointer), 15, 0xFFB9B4CF, false);
        tip.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(18);
        rows.addView(tip, tp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(true);   // on a small screen: arrows scroll it
        scroll.addView(rows);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        guideClose = tvButton(t(R.string.guide_close), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanels();
            }
        });
        guideClose.setGravity(Gravity.CENTER);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(guideClose, new LinearLayout.LayoutParams(0, dp(50), 1f));
        if (kurdishReady) {
            // The other language, by its own name: on the first start, this is where it's chosen
            Button other = tvButton(otherLanguageName(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(KEY_GUIDE, false).apply();   // the guide again, in that language
                    toggleLanguage();
                }
            });
            other.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(50), 1f);
            op.setMarginStart(dp(12));
            buttons.addView(other, op);
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(18);
        card.addView(buttons, bp);

        scrim.addView(card, cardParams(dp(660)));
        return scrim;
    }

    private View buildAddressPanel() {
        FrameLayout scrim = scrim(0xD9000000);
        LinearLayout card = card();
        card.addView(label(t(R.string.address_title), 22, Color.WHITE, true));

        addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressInput.setTextColor(Color.WHITE);
        addressInput.setHintTextColor(0xFF77737F);
        addressInput.setHint("https://");
        addressInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        addressInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        addressInput.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // addresses read left to right
        addressInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        StateListDrawable field = new StateListDrawable();
        GradientDrawable fieldOn = rounded(0xFF0B0E2D, dp(10));
        fieldOn.setStroke(dp(2), ACCENT);
        GradientDrawable fieldOff = rounded(0xFF0B0E2D, dp(10));
        fieldOff.setStroke(dp(1), 0xFF4A4370);
        field.addState(new int[]{android.R.attr.state_focused}, fieldOn);
        field.addState(new int[]{}, fieldOff);
        addressInput.setBackground(field);
        addressInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_SEND) {
                    submitAddress();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(14);
        card.addView(addressInput, ip);

        TextView def = label(t(R.string.address_default, shortUrl(DEFAULT_URL)), 13, 0xFF8E8AA5, false);
        LinearLayout.LayoutParams dp8 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp8.topMargin = dp(8);
        card.addView(def, dp8);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button open = tvButton(t(R.string.address_open), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitAddress();
            }
        });
        Button reset = tvButton(t(R.string.address_use_default), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHome(DEFAULT_URL);
            }
        });
        Button cancel = tvButton(t(R.string.address_cancel), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanels();
            }
        });
        Button[] buttons = {open, reset, cancel};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            if (i > 0) bp.setMarginStart(dp(10));
            row.addView(buttons[i], bp);
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(18);
        card.addView(row, rp);

        scrim.addView(card, cardParams(dp(680)));   // room for the Kurdish labels, even with the box's text set larger
        return scrim;
    }

    private View buildErrorPanel() {
        FrameLayout scrim = scrim(0xFF000000);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        errorTitle = label("", 26, Color.WHITE, true);
        errorTitle.setGravity(Gravity.CENTER);
        box.addView(errorTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        errorDetail = label("", 16, 0xFFB9B4CF, false);
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setMaxWidth(dp(640));
        errorDetail.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = dp(12);
        box.addView(errorDetail, dl);

        errorStatus = label("", 16, ACCENT, true);
        errorStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(16);
        box.addView(errorStatus, sl);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        retryButton = tvButton(t(R.string.error_try_again), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryDelay = RETRY_FIRST_S;
                retryNow();
            }
        });
        retryButton.setGravity(Gravity.CENTER);
        row.addView(retryButton, new LinearLayout.LayoutParams(dp(220), dp(52)));
        Button change = tvButton(t(R.string.error_change_address), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddress();
            }
        });
        change.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(220), dp(52));
        cp.setMarginStart(dp(14));
        row.addView(change, cp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(22);
        box.addView(row, rp);

        errorCode = label("", 12, 0xFF5E5A72, false);
        errorCode.setGravity(Gravity.CENTER);
        errorCode.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams ec = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ec.topMargin = dp(18);
        box.addView(errorCode, ec);

        scrim.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return scrim;
    }

    // ------------------------------------------------------------------ small helpers

    private void showOsd(String text, long millis) {
        osd.setText(text);
        osd.animate().cancel();
        osd.setAlpha(1f);
        osd.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, millis);
    }

    private final Runnable hideOsd = new Runnable() {
        @Override
        public void run() {
            osd.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                @Override
                public void run() {
                    osd.setVisibility(View.GONE);
                }
            });
        }
    };

    private Button tvButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(ACCENT, dp(10)));
        bg.addState(new int[]{android.R.attr.state_pressed}, rounded(ACCENT, dp(10)));
        bg.addState(new int[]{}, rounded(0xFF2B2752, dp(10)));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{0xFF111111, 0xFF111111, Color.WHITE}));
        b.setStateListAnimator(null);
        b.setFocusable(true);
        b.setOnClickListener(listener);
        return b;
    }

    private FrameLayout scrim(int color) {
        FrameLayout s = new FrameLayout(this);
        s.setBackgroundColor(color);
        s.setClickable(true);       // blocks touches/mouse to the page underneath
        s.setFocusable(false);      // ...but never takes remote focus (Android 8+ makes clickable views focusable)
        s.setVisibility(View.GONE);
        if (kurdish) s.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return s;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(26), dp(22), dp(26), dp(22));
        c.setBackground(rounded(0xF2161433, dp(18)));
        return c;
    }

    private FrameLayout.LayoutParams cardParams(int width) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(20);
        return lp;
    }

    private TextView label(CharSequence text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams rowParams(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private static boolean isVisible(View v) {
        return v != null && v.getVisibility() == View.VISIBLE;
    }

    private static boolean isOkKey(int c) {
        return c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER
                || c == KeyEvent.KEYCODE_NUMPAD_ENTER || c == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isBackKey(int c) {
        return c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_ESCAPE || c == KeyEvent.KEYCODE_BUTTON_B;
    }

    private static boolean isDpadKey(int c) {
        return c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN
                || c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isMediaKey(int c) {
        return c == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || c == KeyEvent.KEYCODE_MEDIA_PLAY
                || c == KeyEvent.KEYCODE_MEDIA_PAUSE || c == KeyEvent.KEYCODE_MEDIA_STOP
                || c == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || c == KeyEvent.KEYCODE_MEDIA_REWIND
                || c == KeyEvent.KEYCODE_MEDIA_NEXT || c == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    private static boolean isPageKey(int c) {
        return c == KeyEvent.KEYCODE_PAGE_UP || c == KeyEvent.KEYCODE_PAGE_DOWN
                || c == KeyEvent.KEYCODE_CHANNEL_UP || c == KeyEvent.KEYCODE_CHANNEL_DOWN;
    }

    static boolean isWebScheme(String url) {
        if (url == null) return true;
        String u = url.trim().toLowerCase(Locale.US);
        return u.startsWith("http:") || u.startsWith("https:") || u.startsWith("about:")
                || u.startsWith("data:") || u.startsWith("blob:") || u.startsWith("javascript:");
    }

    /** Turns what someone typed into a full web address, or null if it can't be one. */
    static String normalizeUrl(String input) {
        if (input == null) return null;
        String s = input.trim().replace(" ", "");
        if (s.isEmpty()) return null;
        if (!s.contains("://")) s = "https://" + s;
        Uri u = Uri.parse(s);
        String scheme = u.getScheme();
        String host = u.getHost();
        if (scheme == null || host == null) return null;
        scheme = scheme.toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        if (!host.contains(".") || host.startsWith(".") || host.endsWith(".")) return null;
        return s;
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        try {
            String h = Uri.parse(url).getHost();
            return h != null ? h : url;
        } catch (Exception e) {
            return url;
        }
    }

    private static String shortUrl(String url) {
        if (url == null) return "";
        String s = url;
        int i = s.indexOf("://");
        if (i >= 0) s = s.substring(i + 3);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static boolean sameHost(String a, String b) {
        String ha = hostOf(a).toLowerCase(Locale.US);
        String hb = hostOf(b).toLowerCase(Locale.US);
        return !ha.isEmpty() && ha.equals(hb);
    }

    /** Same website, including its subdomains (app.kurdfilm.krd ~ www.kurdfilm.krd). */
    private boolean sameSite(String url) {
        String h = hostOf(url).toLowerCase(Locale.US);
        String home = hostOf(homeUrl).toLowerCase(Locale.US);
        if (h.isEmpty() || home.isEmpty()) return false;
        String[] parts = home.split("\\.");
        String base = parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : home;
        return h.equals(home) || h.equals(base) || h.endsWith("." + base);
    }

    /** The box's own web engine's main version (Chrome 66 -> 66), 0 if unknown. */
    private int engineVersion() {
        try {
            Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(WebSettings.getDefaultUserAgent(this));
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String num(float v) {
        return String.format(Locale.US, "%.4f", v);
    }
}
