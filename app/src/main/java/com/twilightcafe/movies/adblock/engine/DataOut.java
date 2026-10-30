package com.twilightcafe.movies.adblock.engine;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** Writing the filter file's pieces, as DataIn reads them. */
final class DataOut {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final DataOutputStream out;

    DataOut(OutputStream out) {
        this.out = new DataOutputStream(out);
    }

    void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    void writeLong(long v) throws IOException {
        out.writeLong(v);
    }

    void writeByte(int v) throws IOException {
        out.writeByte(v);
    }

    void writeVar(long v) throws IOException {
        while ((v & ~0x7fL) != 0) {
            out.writeByte((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    /** sorted, or null for none */
    void writeLongs(long[] sorted) throws IOException {
        int n = sorted == null ? 0 : sorted.length;
        writeVar(n);
        for (int i = 0; i < n; i++) {
            if (i == 0) out.writeLong(sorted[0]);
            else writeVar(sorted[i] - sorted[i - 1]);
        }
    }

    void writeString(String s) throws IOException {
        byte[] b = (s == null ? "" : s).getBytes(UTF8);
        writeVar(b.length);
        out.write(b);
    }

    void close() throws IOException {
        out.close();
    }
}
