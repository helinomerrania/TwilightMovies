package com.twilightcafe.movies.web;

/**
 * What the page's scripts may ask the app (the ad-hiding script asks which CSS hides the page's ad boxes): a
 * question by name, with up to two words, and text back. In Android's WebView the page asks at once (on the
 * engine's own thread); in Firefox's engine the answer comes later (a Promise), from a thread of the app's.
 */
public interface Bridge {
    String ask(String question, String a, String b);
}
