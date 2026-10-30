package com.twilightcafe.movies.adblock;

/** The page shown instead of one the lists say is an ad or a harmful site, in the app's language. Plain Java. */
public final class BlockedPage {
    private BlockedPage() {
    }

    public static String html(String title, String text, String host, boolean rtl) {
        return "<!DOCTYPE html><html dir=\"" + (rtl ? "rtl" : "ltr") + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>"
                + "html,body{height:100%;margin:0;background:#0b0e2d;color:#eae6f2;font-family:sans-serif}"
                + "body{display:flex;align-items:center;justify-content:center;text-align:center}"
                + "h1{font-size:30px;margin:0 0 12px}p{font-size:19px;color:#b9b4cf;margin:0}"
                + "small{display:block;margin-top:16px;color:#8e8aa5;direction:ltr}</style></head><body><div>"
                + "<h1>" + escape(title) + "</h1><p>" + escape(text) + "</p><small>" + escape(host)
                + "</small></div></body></html>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
