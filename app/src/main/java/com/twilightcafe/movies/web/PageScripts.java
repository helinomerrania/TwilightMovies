package com.twilightcafe.movies.web;

import android.content.res.AssetManager;

import com.twilightcafe.movies.util.Streams;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The small scripts the app runs inside the website (assets/scripts): each file is one function, read once and
 * called with its arguments. Plain old JavaScript: the café's TV box has a web engine from 2018. tests/js tries
 * every one of them on stand-in pages.
 */
public final class PageScripts {
    /** Scrolls what's under the pointer: (x, y, dx, dy), as fractions of the screen. */
    public static final String SCROLL = "scroll";
    /** Plays, pauses and seeks the page's video: (action, seconds, strict). */
    public static final String MEDIA = "media";
    public static final String PAUSE_ALL = "pause-all";
    /** 1 while a video plays, else 0. */
    public static final String PLAYING = "playing";
    /** Hides the page's ad boxes: (the name of the app's bridge). */
    public static final String HIDE_ADS = "hide-ads";
    /** Takes away see-through layers that catch the pointer's click: (x, y). */
    public static final String AD_LAYERS = "ad-layers";

    private final AssetManager assets;
    private final Map<String, String> read = new HashMap<String, String>();

    public PageScripts(AssetManager assets) {
        this.assets = assets;
    }

    /** The script name called with args: whole numbers as they are, fractions to four places, text quoted. */
    public synchronized String call(String name, Object... args) {
        String src = read.get(name);
        if (src == null) {
            src = load(name);
            read.put(name, src);
        }
        StringBuilder b = new StringBuilder(src.length() + 32).append(src).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) b.append(',');
            b.append(arg(args[i]));
        }
        return b.append(')').toString();
    }

    static String arg(Object a) {
        if (a instanceof Float || a instanceof Double) return String.format(Locale.US, "%.4f", ((Number) a).doubleValue());
        if (a instanceof Number || a instanceof Boolean) return String.valueOf(a);
        String s = String.valueOf(a);
        StringBuilder b = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20 || c == '<' || c == '>' || c == 0x2028 || c == 0x2029) b.append(String.format(Locale.US, "\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('\'').toString();
    }

    private String load(String name) {
        InputStream in = null;
        try {
            in = assets.open("scripts/" + name + ".js");
            return new String(Streams.read(in, 1 << 20), "UTF-8").trim();
        } catch (IOException e) {
            throw new IllegalStateException("scripts/" + name + ".js isn't in the app", e);
        } finally {
            Streams.close(in);
        }
    }
}
