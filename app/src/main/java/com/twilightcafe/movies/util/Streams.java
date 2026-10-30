package com.twilightcafe.movies.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copying downloads, counted, and closing things that may already be closed. Plain Java. */
public final class Streams {
    private Streams() {
    }

    /**
     * Copies in to out. More than max, more than expected (when it's known: above 0), or less than expected
     * (the connection closed early) and it throws, so a cut or wrong download is never used. Returns the bytes.
     */
    public static long copy(InputStream in, OutputStream out, long expected, long max) throws IOException {
        byte[] buf = new byte[65536];
        long got = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            got += n;
            if (got > max) throw new IOException("bigger than " + max + " bytes");
            if (expected > 0 && got > expected) throw new IOException("bigger than the " + expected + " bytes it should be");
            out.write(buf, 0, n);
        }
        if (expected > 0 && got != expected) throw new IOException(got + " of " + expected + " bytes");
        return got;
    }

    /** All of in, at most max bytes. */
    public static byte[] read(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out, -1, max);
        return out.toByteArray();
    }

    public static void close(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
