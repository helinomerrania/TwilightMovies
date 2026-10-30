package com.twilightcafe.movies.web;

import android.view.View;

/**
 * The website, in a web engine: the box's own (webview/: Android's WebView) or Firefox's, which comes inside
 * the app (gecko/: GeckoView) for boxes whose own is too old for the websites. app/Engines chooses. The rest of
 * the app only knows this: the page loads, goes back, and runs the app's scripts (PageScripts).
 */
public interface Page {
    /** A script's answer: the value of its last expression, as JSON ("1", "\"ok\"", "null"); null if none came. */
    interface Result {
        void value(String json);
    }

    /** The view that shows the page; null once it's gone. */
    View view();

    boolean alive();

    /** Whether the engine came inside the app (Firefox's), not with the box. */
    boolean inApp();

    /** The engine's main version (Chrome 66: 66; Firefox 157: 157); 0 if unknown. */
    int engineVersion();

    /** Whether the engine says which keys the page didn't use (PageListener.keyNotUsed). */
    boolean reportsUnusedKeys();

    /** What the app's own scripts in the page may ask it, under this name (the page's own scripts can't). */
    void expose(Bridge bridge, String name);

    String url();

    void load(String url);

    void reload();

    /** Runs a script (PageScripts.call) in the page itself, not its frames; result may be null. */
    void run(String script, Result result);

    /** The address of the page the history is at (null if none). */
    String historyUrl();

    /** How far back the page before this one is (negative; 0 if none), past redirects to the same address. */
    int stepsBack();

    void go(int steps);

    /** The app leaves the screen: the script first (it pauses the videos), then the page's timers. */
    void pause(String script);

    void resume();

    void destroy();

    /** After the engine crashed: the view is dead, only taken away. */
    void drop();
}
