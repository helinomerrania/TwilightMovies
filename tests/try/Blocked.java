import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.adblock.engine.Request;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * What the app's ad blocker would do to a website (after website.js saved every address it loaded): each address
 * it would block, each page it would call an ad, and each jump to another site it would stop.
 * Usage: java Blocked filters.bin requests.tsv OWN_DOMAIN...   (the website's own: kurdfilm.krd)
 */
public final class Blocked {
    private Blocked() {
    }

    public static void main(String[] args) throws Exception {
        FilterSet set;
        try (FileInputStream in = new FileInputStream(args[0])) {
            set = FilterSet.read(in);
        }
        List<String> lines = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        int blocked = 0;
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            if (f.length < 4 || !(f[0].startsWith("http:") || f[0].startsWith("https:"))) continue;
            String url = f[0];
            String host = Request.hostOf(url);
            if (f[3].equals("main")) {
                if (!own(host, args)) System.out.println("JUMP STOPPED  " + url + "  (not the website's own: the app stops it)");
                else if (set.blocksPage(url)) System.out.println("AD PAGE       " + url);
                continue;
            }
            String page = Request.hostOf(f[2]);
            Request r = new Request(url, page.isEmpty() ? host : page, type(f[1]));
            if (set.match(r)) {
                boolean own = own(host, args);   // the app never blocks what the website's own domains serve
                if (!own) blocked++;
                System.out.println((own ? "OWN, LET BY   " : "BLOCKED       ") + url + "  (" + f[1]
                        + (r.redirect != null ? ", stand-in " + r.redirect : "") + ")");
            }
        }
        System.out.println("\n" + blocked + " of " + lines.size() + " requests blocked (" + set.rules + " rules)");
    }

    private static boolean own(String host, String[] args) {
        for (int i = 2; i < args.length; i++) {
            if (host.equals(args[i]) || host.endsWith("." + args[i])) return true;
        }
        return false;
    }

    /** Chromium's kinds of request, as the rules know them (the app works them out the same way). */
    private static int type(String t) {
        switch (t) {
            case "document": return Request.SUBDOCUMENT;
            case "script": return Request.SCRIPT;
            case "stylesheet": return Request.STYLESHEET;
            case "image": return Request.IMAGE;
            case "media": return Request.MEDIA;
            case "font": return Request.FONT;
            case "xhr": case "fetch": case "eventsource": return Request.XHR;
            case "websocket": return Request.WEBSOCKET;
            case "ping": return Request.PING;
            default: return Request.UNKNOWN;
        }
    }
}
