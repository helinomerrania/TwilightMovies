package com.twilightcafe.movies.adblock.engine;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** Reading the filter file's pieces: numbers of any size in as few bytes as they need, sorted hash sets, texts. */
final class DataIn {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final DataInputStream in;

    DataIn(InputStream in) {
        this.in = new DataInputStream(in);
    }

    int readInt() throws IOException {
        return in.readInt();
    }

    long readLong() throws IOException {
        return in.readLong();
    }

    byte readByte() throws IOException {
        return in.readByte();
    }

    /** 7 bits a byte, lowest first; the top bit says another byte follows. */
    long readVar() throws IOException {
        long v = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            v |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift > 63) throw new IOException("bad number");
        }
    }

    /** A sorted set of hashes: the count, the first, then each one's distance from the one before. */
    long[] readLongs() throws IOException {
        int n = (int) readVar();
        long[] out = new long[n];
        long prev = 0;
        for (int i = 0; i < n; i++) {
            prev = i == 0 ? in.readLong() : prev + readVar();
            out[i] = prev;
        }
        return out;
    }

    String readString() throws IOException {
        int n = (int) readVar();
        if (n == 0) return "";
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, UTF8);
    }
}
