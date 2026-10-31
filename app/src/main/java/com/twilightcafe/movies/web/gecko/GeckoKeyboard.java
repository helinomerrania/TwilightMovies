package com.twilightcafe.movies.web.gecko;

import android.os.SystemClock;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import org.mozilla.geckoview.GeckoSession;

/**
 * The on-screen keyboard in Firefox's engine, only when it's asked for: OK on one of the page's fields
 * (assets/gecko/arrows.js asks), or a touch on the page (the mouse pointer clicks by touch). By itself the engine
 * opens it as soon as a field gets the remote, and on a TV the keyboard then takes the arrows: the remote
 * couldn't get past the website's search. Closing it, and the rest, is the engine's own.
 */
final class GeckoKeyboard implements GeckoSession.TextInputDelegate {
    private static final long ASKED = 1500;   // how long after the ask the engine may still open it
    private final GeckoSession.TextInputDelegate engine;
    private long askedAt = -ASKED;

    GeckoKeyboard(GeckoSession.TextInputDelegate engine) {
        this.engine = engine;
    }

    /** The page was touched (the pointer's click, a mouse's): the field it opens may have the keyboard. */
    void touched() {
        askedAt = SystemClock.uptimeMillis();
    }

    /** OK on a field: the keyboard now. */
    void show(GeckoSession session) {
        askedAt = SystemClock.uptimeMillis();
        engine.showSoftInput(session);
    }

    @Override
    public void showSoftInput(GeckoSession session) {
        if (SystemClock.uptimeMillis() - askedAt < ASKED) engine.showSoftInput(session);
    }

    @Override
    public void hideSoftInput(GeckoSession session) {
        engine.hideSoftInput(session);
    }

    @Override
    public void restartInput(GeckoSession session, int reason) {
        engine.restartInput(session, reason);
    }

    @Override
    public void updateSelection(GeckoSession session, int selStart, int selEnd, int compositionStart, int compositionEnd) {
        engine.updateSelection(session, selStart, selEnd, compositionStart, compositionEnd);
    }

    @Override
    public void updateExtractedText(GeckoSession session, ExtractedTextRequest request, ExtractedText text) {
        engine.updateExtractedText(session, request, text);
    }

    @Override
    public void updateCursorAnchorInfo(GeckoSession session, CursorAnchorInfo info) {
        engine.updateCursorAnchorInfo(session, info);
    }
}
