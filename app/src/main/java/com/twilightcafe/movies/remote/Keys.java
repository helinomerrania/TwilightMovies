package com.twilightcafe.movies.remote;

import android.view.KeyEvent;

/** The remote's keys, by what they're for (remotes and game pads name the same keys differently). */
final class Keys {
    private Keys() {
    }

    static boolean ok(int c) {
        return c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER
                || c == KeyEvent.KEYCODE_NUMPAD_ENTER || c == KeyEvent.KEYCODE_BUTTON_A;
    }

    static boolean back(int c) {
        return c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_ESCAPE || c == KeyEvent.KEYCODE_BUTTON_B;
    }

    static boolean arrow(int c) {
        return c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN
                || c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    static boolean media(int c) {
        return c == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || c == KeyEvent.KEYCODE_MEDIA_PLAY
                || c == KeyEvent.KEYCODE_MEDIA_PAUSE || c == KeyEvent.KEYCODE_MEDIA_STOP
                || c == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || c == KeyEvent.KEYCODE_MEDIA_REWIND
                || c == KeyEvent.KEYCODE_MEDIA_NEXT || c == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    /** Page up and down, and the channel keys (which do the same). */
    static boolean page(int c) {
        return c == KeyEvent.KEYCODE_PAGE_UP || c == KeyEvent.KEYCODE_PAGE_DOWN
                || c == KeyEvent.KEYCODE_CHANNEL_UP || c == KeyEvent.KEYCODE_CHANNEL_DOWN;
    }

    static boolean up(int c) {
        return c == KeyEvent.KEYCODE_PAGE_UP || c == KeyEvent.KEYCODE_CHANNEL_UP;
    }
}
