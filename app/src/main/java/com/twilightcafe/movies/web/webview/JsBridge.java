package com.twilightcafe.movies.web.webview;

import android.webkit.JavascriptInterface;

import com.twilightcafe.movies.web.Bridge;

/**
 * The app's Bridge as the page's scripts reach it in Android's WebView, on the engine's own thread: window[name]
 * .ask(question, a, b), always with all three (WebView finds the method by how many there are). Text only.
 */
final class JsBridge {
    private final Bridge bridge;

    JsBridge(Bridge bridge) {
        this.bridge = bridge;
    }

    @JavascriptInterface
    public String ask(String question, String a, String b) {
        String answer = bridge.ask(question, a, b);
        return answer != null ? answer : "";
    }
}
