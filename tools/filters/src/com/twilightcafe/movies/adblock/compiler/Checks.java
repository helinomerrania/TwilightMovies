package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Request;

import java.util.ArrayList;
import java.util.List;

/**
 * The checks every filter file must pass before it's published (tools/filters/checks.txt). Each line:
 * "block|allow TYPE ADDRESS [PAGE]" (TYPE: script, image, stylesheet, xhr, frame, media, font, page, other,
 * unknown; PAGE: the host of the page it's on, else the address's own), or "hide|show HOST SELECTOR" for the
 * hiding rules on a site.
 */
final class Checks {
    private Checks() {
    }

    /** The lines that failed. */
    static List<String> run(FilterSet set, List<String> lines) {
        List<String> failed = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+", 4);
            if (f[0].equals("hide") || f[0].equals("show")) {
                boolean hidden = set.pageCss(f[1]).contains(f[2] + "{");
                if (hidden != f[0].equals("hide")) failed.add(line);
                continue;
            }
            String page = f.length >= 4 ? f[3] : Request.hostOf(f[2]);
            boolean blocked = set.match(new Request(f[2], page, typeOf(f[1])));
            if (blocked != f[0].equals("block")) failed.add(line);
        }
        return failed;
    }

    static int typeOf(String t) {
        switch (t) {
            case "script": return Request.SCRIPT;
            case "image": return Request.IMAGE;
            case "stylesheet": return Request.STYLESHEET;
            case "xhr": return Request.XHR;
            case "frame": return Request.SUBDOCUMENT;
            case "media": return Request.MEDIA;
            case "font": return Request.FONT;
            case "page": return Request.DOCUMENT;
            case "other": return Request.OTHER;
            default: return Request.UNKNOWN;
        }
    }
}
