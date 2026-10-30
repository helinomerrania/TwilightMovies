package com.twilightcafe.movies.adblock;

import android.content.Context;

import com.twilightcafe.movies.adblock.engine.FilterSet;
import com.twilightcafe.movies.util.Streams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The two filter files: the one inside the app (assets, made with the app's build) and the one downloaded last
 * (the app's own files). The newer is used; a damaged download is thrown away for the one inside the app.
 */
final class FilterFiles {
    static final String NAME = "filters.bin";

    private FilterFiles() {
    }

    static File downloaded(Context ctx) {
        return new File(ctx.getFilesDir(), NAME);
    }

    /** The newer of the two, read (null if neither can be). */
    static FilterSet loadBest(Context ctx) {
        File downloaded = downloaded(ctx);
        boolean useDownloaded = downloaded.isFile() && created(ctx, false) > created(ctx, true);
        for (int attempt = 0; attempt < 2; attempt++) {
            InputStream in = null;
            try {
                in = open(ctx, !useDownloaded);
                return FilterSet.read(in);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                if (!useDownloaded) return null;
                downloaded.delete();   // damaged: the one inside the app instead
                useDownloaded = false;
            } finally {
                Streams.close(in);
            }
        }
        return null;
    }

    /** When a filter file was made, from its first bytes (-1 if it can't be read). */
    private static long created(Context ctx, boolean inApp) {
        InputStream in = null;
        try {
            in = open(ctx, inApp);
            return FilterSet.readCreated(in);
        } catch (IOException | RuntimeException e) {
            return -1;
        } finally {
            Streams.close(in);
        }
    }

    private static InputStream open(Context ctx, boolean inApp) throws IOException {
        return inApp ? ctx.getAssets().open(NAME) : new FileInputStream(downloaded(ctx));
    }
}
