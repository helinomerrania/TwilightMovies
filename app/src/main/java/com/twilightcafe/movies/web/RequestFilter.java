package com.twilightcafe.movies.web;

import java.util.Map;

/**
 * Every address the page and its frames load is asked about first (the ad blocker answers), on one of the
 * engine's or the app's own threads, never the main one: null to load it, or what to answer instead.
 */
public interface RequestFilter {
    /** The page itself (kind), as Firefox's engine says it. */
    String PAGE = "main_frame";

    /** What's answered instead of what was asked for: a stand-in (a blank picture, an empty script...). */
    final class Answer {
        public final String mime;
        public final byte[] data;

        public Answer(String mime, byte[] data) {
            this.mime = mime;
            this.data = data;
        }
    }

    /**
     * url: what's loaded. kind: what it's for, in Firefox's engine's words (PAGE, "sub_frame", "script",
     * "image"...), or null to work it out from the headers (Android's WebView). frameUrl: the page or frame it's
     * for, and pageUrl: the page itself, if the engine says (null: from the headers, or the page now loading).
     */
    Answer check(String url, String kind, String frameUrl, String pageUrl, Map<String, String> headers);
}
