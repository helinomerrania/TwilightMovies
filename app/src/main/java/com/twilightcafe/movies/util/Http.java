package com.twilightcafe.movies.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;

/** Asking GitHub over https: a text, or a file into a stream, each with a size limit. Plain Java. */
public final class Http {
    /** GitHub asks every visitor for a name. */
    public static final String AGENT = "TwilightMovies-app";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Http() {
    }

    /** The text at address (at most max bytes), or null for 404. Throws when the internet or the server fails. */
    public static String text(String address, String accept, long max) throws IOException {
        HttpURLConnection c = open(address, 20000);
        if (accept != null) c.setRequestProperty("Accept", accept);
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            try {
                return new String(Streams.read(in, max), UTF8);
            } finally {
                Streams.close(in);
            }
        } finally {
            c.disconnect();
        }
    }

    /** The file at address into out: exactly expected bytes when that's known (above 0), never more than max. */
    public static long download(String address, OutputStream out, long expected, long max) throws IOException {
        HttpURLConnection c = open(address, 60000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            try {
                return Streams.copy(in, out, expected, max);
            } finally {
                Streams.close(in);
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String address, int readTimeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(address).toURL().openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(readTimeout);
        c.setInstanceFollowRedirects(true);   // GitHub's downloads come from another of its hosts
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", AGENT);
        return c;
    }
}
