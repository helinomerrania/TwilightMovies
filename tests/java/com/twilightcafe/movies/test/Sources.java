package com.twilightcafe.movies.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** For the tests that look at the project's own files: where they are, what's in them, what each one imports. */
public final class Sources {
    /** The repository (tests/java/run.sh runs from it). */
    public static final Path ROOT = Paths.get(System.getProperty("root", ".")).toAbsolutePath().normalize();
    public static final Path MAIN = ROOT.resolve("app/src/main");
    /** The app's code: com/twilightcafe/movies. */
    public static final Path CODE = MAIN.resolve("java/com/twilightcafe/movies");
    private static final String APP = "com.twilightcafe.movies.";
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?com\\.twilightcafe\\.movies\\.([\\w.]+);", Pattern.MULTILINE);

    private Sources() {
    }

    public static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static int lines(Path p) {
        String s = read(p);
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return s.isEmpty() || s.endsWith("\n") ? n : n + 1;
    }

    /** Every file under dir (folders named build or node_modules left out) whose name ends with one of endings. */
    public static List<Path> files(Path dir, String... endings) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/build/") && !p.toString().contains("/node_modules/"))
                    .filter(p -> { for (String e : endings) if (p.toString().endsWith(e)) return true; return false; })
                    .sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The .java files right in a folder of the app's code ("" for the top folder, "adblock/engine"...). */
    public static List<Path> javaIn(String folder) {
        Path dir = folder.isEmpty() ? CODE : CODE.resolve(folder);
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** The folders of the app's code, as paths from it ("adblock", "adblock/engine", ...). */
    public static List<String> folders() {
        try (Stream<Path> s = Files.walk(CODE)) {
            return s.filter(Files::isDirectory).filter(p -> !p.equals(CODE))
                    .map(p -> CODE.relativize(p).toString().replace('\\', '/')).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The app's folders a file imports from ("top" for the top folder: R, BuildConfig, the manifest's classes). */
    public static List<String> uses(Path file) {
        List<String> out = new ArrayList<>();
        Matcher m = IMPORT.matcher(read(file));
        while (m.find()) {
            StringBuilder folder = new StringBuilder();
            for (String part : m.group(1).split("\\.")) {
                if (Character.isUpperCase(part.charAt(0))) break;
                if (folder.length() > 0) folder.append('/');
                folder.append(part);
            }
            out.add(folder.length() == 0 ? "top" : folder.toString());
        }
        return out;
    }

    /** Whether a file uses Android (or Android's org.json, or the app's R and BuildConfig). */
    public static boolean usesAndroid(Path file) {
        String s = read(file);
        return s.contains("import android.") || s.contains("import org.json.")
                || s.contains("import " + APP + "R;") || s.contains("import " + APP + "BuildConfig;");
    }

    public static String relative(Path p) {
        return ROOT.relativize(p).toString().replace('\\', '/');
    }
}
