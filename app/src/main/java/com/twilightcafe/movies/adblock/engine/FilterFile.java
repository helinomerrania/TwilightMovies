package com.twilightcafe.movies.adblock.engine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The filter file: a FilterSet written down (on GitHub, by tools/filters) and read back (on the box). gzip of:
 * "TWAB", the format, when it was made, the number of rules, a line about the lists; five sorted host-hash
 * sets (blocked, allowed, pages left alone, no hiding, no generic hiding); the public suffix list (rules,
 * wildcards, exceptions); the eight groups of network rules (NetworkRules); the hiding rules (by class or id,
 * for every page, per site); then "END!".
 */
public final class FilterFile {
    static final int MAGIC = 0x54574142;   // "TWAB"
    public static final int FORMAT = 1;
    static final int END = 0x454E4421;     // "END!"

    private FilterFile() {
    }

    private static DataIn open(InputStream raw) throws IOException {
        BufferedInputStream b = new BufferedInputStream(raw, 65536);
        b.mark(2);
        int m0 = b.read();
        int m1 = b.read();
        b.reset();
        InputStream src = m0 == 0x1f && m1 == 0x8b ? new GZIPInputStream(b, 65536) : b;
        DataIn in = new DataIn(new BufferedInputStream(src, 65536));
        if (in.readInt() != MAGIC) throw new IOException("not a filter file");
        int format = in.readInt();
        if (format != FORMAT) throw new IOException("filter file format " + format + ", this app reads " + FORMAT);
        return in;
    }

    static long readCreated(InputStream raw) throws IOException {
        return open(raw).readLong();
    }

    static FilterSet read(InputStream raw) throws IOException {
        DataIn in = open(raw);
        long created = in.readLong();
        int rules = in.readInt();
        String info = in.readString();
        long[] block = in.readLongs();
        long[] allow = in.readLongs();
        long[] pageAllow = in.readLongs();
        long[] noCosmetic = in.readLongs();
        long[] noGeneric = in.readLongs();
        Hosts hosts = new Hosts(new PublicSuffix(in.readLongs(), in.readLongs(), in.readLongs()));
        RuleGroup[] groups = new RuleGroup[NetworkRules.GROUPS];
        for (int g = 0; g < groups.length; g++) groups[g] = readGroup(in);
        int nk = (int) in.readVar();
        long[] keyedHashes = new long[nk];
        String[] keyedSelectors = new String[nk];
        for (int i = 0; i < nk; i++) {
            keyedHashes[i] = in.readLong();
            keyedSelectors[i] = in.readString();
        }
        int nh = (int) in.readVar();
        String[] high = new String[nh];
        for (int i = 0; i < nh; i++) high[i] = in.readString();
        int ns = (int) in.readVar();
        long[] specificHashes = new long[ns];
        byte[] kinds = new byte[ns];
        String[] specificSelectors = new String[ns];
        for (int i = 0; i < ns; i++) {
            specificHashes[i] = in.readLong();
            kinds[i] = in.readByte();
            specificSelectors[i] = in.readString();
        }
        if (in.readInt() != END) throw new IOException("filter file damaged");
        NetworkRules network = new NetworkRules(hosts, block, allow, pageAllow, groups);
        CosmeticRules cosmetic = new CosmeticRules(hosts, noCosmetic, noGeneric, keyedHashes, keyedSelectors, high,
                specificHashes, kinds, specificSelectors);
        return new FilterSet(created, rules, info, hosts, network, cosmetic);
    }

    private static RuleGroup readGroup(DataIn in) throws IOException {
        int n = (int) in.readVar();
        long[] keys = new long[n];
        NetFilter[] filters = new NetFilter[n];
        for (int i = 0; i < n; i++) {
            keys[i] = in.readLong();
            int flags = (int) in.readVar();
            int types = (int) in.readVar();
            String pattern = in.readString();
            String redirect = in.readString();
            filters[i] = new NetFilter(flags, types, pattern, redirect,
                    in.readLongs(), in.readLongs(), in.readLongs(), in.readLongs(), in.readLongs());
        }
        return new RuleGroup(keys, filters);
    }

    /** Writes set as read() reads it (gzip). */
    public static void write(FilterSet set, OutputStream os) throws IOException {
        DataOut out = new DataOut(new BufferedOutputStream(new GZIPOutputStream(os, 65536), 65536));
        NetworkRules net = set.network;
        CosmeticRules cos = set.cosmetic;
        PublicSuffix psl = set.hosts.psl();
        out.writeInt(MAGIC);
        out.writeInt(FORMAT);
        out.writeLong(set.created);
        out.writeInt(set.rules);
        out.writeString(set.info);
        for (long[] hosts : new long[][]{net.blockHosts, net.allowHosts, net.pageAllowHosts,
                cos.noCosmeticHosts, cos.noGenericHosts, psl.rules, psl.wildcards, psl.exceptions}) {
            out.writeLongs(hosts);
        }
        for (RuleGroup g : net.groups) writeGroup(out, g);
        out.writeVar(cos.keyedHashes.length);
        for (int i = 0; i < cos.keyedHashes.length; i++) {
            out.writeLong(cos.keyedHashes[i]);
            out.writeString(cos.keyedSelectors[i]);
        }
        out.writeVar(cos.highSelectors.length);
        for (String sel : cos.highSelectors) out.writeString(sel);
        out.writeVar(cos.specificHashes.length);
        for (int i = 0; i < cos.specificHashes.length; i++) {
            out.writeLong(cos.specificHashes[i]);
            out.writeByte(cos.specificKinds[i]);
            out.writeString(cos.specificSelectors[i]);
        }
        out.writeInt(END);
        out.close();
    }

    private static void writeGroup(DataOut out, RuleGroup g) throws IOException {
        out.writeVar(g.keys.length);
        for (int i = 0; i < g.keys.length; i++) {
            NetFilter f = g.filters[i];
            out.writeLong(g.keys[i]);
            out.writeVar(f.flags);
            out.writeVar(f.types);
            out.writeString(f.pattern);
            out.writeString(f.redirect);
            out.writeLongs(f.domainsIn);
            out.writeLongs(f.domainsOut);
            out.writeLongs(f.toIn);
            out.writeLongs(f.toOut);
            out.writeLongs(f.denyAllow);
        }
    }
}
