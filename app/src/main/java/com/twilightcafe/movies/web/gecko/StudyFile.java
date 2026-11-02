package com.twilightcafe.movies.web.gecko;

import android.util.Log;

import com.twilightcafe.movies.util.Streams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * The app's own try only (its pages' messages in Android's log, never on the boxes): what the websites' pages ask their
 * data feeds and the pages themselves (assets/gecko/study.js), a line of JSON each, in a file the try takes from the
 * emulator (tests/try/study.sh): the app's own folder on its storage, study.jsonl. Written on the extension's threads.
 */
final class StudyFile {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final File file;

    StudyFile(File folder) {
        file = new File(folder, "study.jsonl");
        if (file.exists() && !file.delete()) Log.w("TwilightMovies", "the study's file from before is still there");
    }

    void add(final String line) {
        Extension.WORK.execute(new Runnable() {
            @Override
            public void run() {
                OutputStream out = null;
                try {
                    out = new FileOutputStream(file, true);
                    out.write((line.replace('\n', ' ') + "\n").getBytes(UTF8));
                } catch (IOException e) {
                    Log.w("TwilightMovies", "the study's file", e);
                } finally {
                    Streams.close(out);
                }
            }
        });
    }
}
