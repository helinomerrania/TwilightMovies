package com.twilightcafe.movies.adblock.compiler;

import com.twilightcafe.movies.adblock.engine.NetFilter;
import com.twilightcafe.movies.adblock.engine.Request;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A network rule's options, after its "$": the kinds of request (script, image...), the parties, domain=,
 * to=, denyallow=, important, match-case, redirect=, badfilter and the page-level exceptions ($document,
 * $elemhide, $generichide). Options the app can't apply (csp, removeparam, header, redirect-rule...) leave the
 * whole rule out, and so do rules only for pop-ups: the app stops pop-ups to other sites by itself.
 */
final class RuleOptions {
    private static final int POPUP = 1 << 20;
    private static final Map<String, Integer> TYPES = new HashMap<>();

    static {
        TYPES.put("script", Request.SCRIPT);
        TYPES.put("image", Request.IMAGE);
        TYPES.put("stylesheet", Request.STYLESHEET);
        TYPES.put("css", Request.STYLESHEET);
        TYPES.put("object", Request.OBJECT);
        TYPES.put("object-subrequest", Request.OBJECT);
        TYPES.put("xmlhttprequest", Request.XHR);
        TYPES.put("xhr", Request.XHR);
        TYPES.put("subdocument", Request.SUBDOCUMENT);
        TYPES.put("frame", Request.SUBDOCUMENT);
        TYPES.put("ping", Request.PING);
        TYPES.put("beacon", Request.PING);
        TYPES.put("media", Request.MEDIA);
        TYPES.put("font", Request.FONT);
        TYPES.put("other", Request.OTHER);
        TYPES.put("websocket", Request.WEBSOCKET);
    }

    private RuleOptions() {
    }

    /** Reads opts into s; false if the rule can't be kept. */
    static boolean read(String opts, RuleSpec s) {
        int[] types = {0, 0};   // asked for, left out
        if (!opts.isEmpty()) {
            for (String o : opts.split(",")) {
                if (o.isEmpty() || !one(o, s, types)) return false;
            }
        }
        if (s.badfilter) return true;
        int pos = types[0];
        if (pos == POPUP) return s.special != 0;   // only for pop-ups
        pos &= ~POPUP;
        int base = pos == 0 ? Request.DEFAULT : pos;
        s.types = base & ~types[1];
        if (s.special != 0 && pos == 0 && types[1] == 0) s.types = 0;
        return s.types != 0 || s.special != 0;
    }

    private static boolean one(String o, RuleSpec s, int[] types) {
        boolean not = o.startsWith("~");
        String name = not ? o.substring(1) : o;
        String value = null;
        int eq = name.indexOf('=');
        if (eq >= 0) {
            value = name.substring(eq + 1);
            name = name.substring(0, eq);
        }
        name = name.toLowerCase(Locale.ROOT);
        Integer t = TYPES.get(name);
        if (t != null) {
            types[not ? 1 : 0] |= t;
            return true;
        }
        switch (name) {
            case "third-party":
            case "3p":
                s.flags |= not ? NetFilter.FIRST_PARTY : NetFilter.THIRD_PARTY;
                return true;
            case "first-party":
            case "1p":
                s.flags |= not ? NetFilter.THIRD_PARTY : NetFilter.FIRST_PARTY;
                return true;
            case "strict1p":
                s.flags |= NetFilter.FIRST_PARTY;
                return true;
            case "strict3p":
                s.flags |= NetFilter.THIRD_PARTY;
                return true;
            case "match-case":
                s.flags |= NetFilter.MATCH_CASE;
                return true;
            case "important":
                if (!s.exception) s.flags |= NetFilter.IMPORTANT;
                return true;
            case "domain":
            case "from":
                return domains(value, s.domIn, s.domOut);
            case "to":
                return domains(value, s.toIn, s.toOut);
            case "denyallow":
                return domains(value, s.deny, null);
            case "redirect":
                if (value == null || value.isEmpty()) return false;
                s.redirect = value;
                return true;
            case "empty":
                s.redirect = "empty";
                return true;
            case "mp4":
                s.redirect = "noopmp4-1s";
                types[0] |= Request.MEDIA;
                return true;
            case "badfilter":
                s.badfilter = true;
                return true;
            case "document":
            case "doc":
                if (s.exception) s.special |= RuleSpec.DOC_ALLOW;
                else types[0] |= Request.DOCUMENT;
                return true;
            case "elemhide":
            case "ehide":
                s.special |= RuleSpec.NO_COSMETIC;
                return true;
            case "generichide":
            case "ghide":
                s.special |= RuleSpec.NO_GENERIC;
                return true;
            case "popup":
                if (!not) types[0] |= POPUP;
                return true;
            case "all":
                types[0] |= Request.ALL | POPUP;
                return true;
            case "reason":
                return true;   // a note for people
            default:
                // redirect-rule, csp, removeparam, header, replace, method, cname, popunder, urlskip,
                // specifichide, genericblock, inline-script... : not something this app can do
                return false;
        }
    }

    private static boolean domains(String value, Set<String> in, Set<String> out) {
        if (value == null || value.isEmpty()) return false;
        for (String d : value.split("\\|")) {
            d = d.trim().toLowerCase(Locale.ROOT);
            if (d.isEmpty()) continue;
            boolean not = d.startsWith("~");
            if (not) d = d.substring(1);
            if (d.startsWith("/")) return false;   // regular expressions
            d = Domains.ascii(d);
            if (d == null) return false;
            if (not) {
                if (out == null) return false;
                out.add(d);
            } else {
                in.add(d);
            }
        }
        return true;
    }
}
