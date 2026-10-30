package com.twilightcafe.movies;

import com.twilightcafe.movies.test.Sources;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.twilightcafe.movies.test.Check.eq;
import static com.twilightcafe.movies.test.Check.ok;
import static com.twilightcafe.movies.test.Sources.CODE;
import static com.twilightcafe.movies.test.Sources.ROOT;

/**
 * The project keeps to the rules in ARCHITECTURE.md: small files; only what Android starts by name in the top
 * folder; only MainActivity makes the app; the ground floor never reaches up; only app/ knows the two web
 * engines; the plain-Java parts use nothing of Android; nothing newer than Android 6.0 in the app; every folder
 * documented, every class saying what it does, and every test run.
 */
public final class ArchitectureTest {
    static final int MAX_LINES = 250;

    /** The ground floor: what each of its folders may use ("top": R, BuildConfig, the manifest's classes). */
    static final Map<String, Set<String>> MAY_USE = new TreeMap<>();
    /** The features: they use the ground floor and each other; nothing below uses them. */
    static final Set<String> FEATURES = set("app", "loading", "menu", "pointer", "remote", "sites", "video");
    /** Folders of plain Java, all of them (other plain-Java classes say so at their top). */
    static final Set<String> PLAIN = set("util", "adblock/engine", "adblock/surrogates");

    static {
        MAY_USE.put("util", set());
        MAY_USE.put("adblock/engine", set());
        MAY_USE.put("adblock/surrogates", set("adblock/engine"));
        MAY_USE.put("adblock", set("adblock/engine", "adblock/surrogates", "util"));
        MAY_USE.put("settings", set("util"));
        MAY_USE.put("text", set("top"));
        MAY_USE.put("ui", set("text", "util", "top"));
        MAY_USE.put("update", set("util", "top"));
        MAY_USE.put("web", set("util"));
        MAY_USE.put("web/webview", set("web", "util"));
        MAY_USE.put("web/gecko", set("web", "util"));
    }

    /** The web engines: only app/ (Engines) chooses and makes one; everything else knows only web/'s Page. */
    static final Set<String> ENGINES = set("web/webview", "web/gecko");

    /** Library calls Android 6.0 doesn't have (the oldest boxes the app runs on are older than they are). */
    static final Pattern NEWER = Pattern.compile("java\\.util\\.(function|stream)|java\\.time\\.|import java\\.util\\.Base64"
            + "|String\\.join\\(|\\.(computeIfAbsent|computeIfPresent|removeIf|forEach|getOrDefault|putIfAbsent|replaceAll|merge)\\("
            + "|\\.stream\\(\\)|\\b(Map|List|Set)\\.(of|copyOf)\\(|Optional<|\\.readAllBytes\\(|\\.isBlank\\(|\\.strip\\(|\\.repeat\\("
            + "|Math\\.(floorMod|floorDiv|toIntExact|addExact|multiplyExact)\\(|\\b(?!Arrays\\b|Collections\\b)[a-z]\\w*\\.sort\\(");

    private ArchitectureTest() {
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    public static void run() {
        smallFiles();
        topFolder();
        onlyMainActivityMakesTheApp();
        groundFloor();
        onlyAppKnowsTheEngines();
        plainJava();
        android5();
        documented();
    }

    static void smallFiles() {
        List<Path> files = new ArrayList<>();
        for (String dir : new String[]{"app/src", "tools", "tests", "art"}) {
            files.addAll(Sources.files(ROOT.resolve(dir), ".java", ".js", ".sh", ".py"));
        }
        List<String> big = new ArrayList<>();
        for (Path f : files) {
            int n = Sources.lines(f);
            if (n > MAX_LINES) big.add(Sources.relative(f) + " (" + n + " lines)");
        }
        ok("every file of code, " + files.size() + " of them, is " + MAX_LINES + " lines or fewer", big.isEmpty(), big);
    }

    static void topFolder() {
        Set<String> components = new TreeSet<>();
        Matcher m = Pattern.compile("android:name=\"\\.(\\w+)\"").matcher(Sources.read(Sources.MAIN.resolve("AndroidManifest.xml")));
        while (m.find()) components.add(m.group(1));
        Set<String> top = new TreeSet<>();
        for (Path p : Sources.javaIn("")) top.add(p.getFileName().toString().replace(".java", ""));
        eq("the top folder holds only what Android starts by name (the manifest's classes)", top, components);
    }

    static void onlyMainActivityMakesTheApp() {
        List<String> users = new ArrayList<>();
        for (String folder : Sources.folders()) {
            if (folder.equals("app")) continue;
            for (Path p : Sources.javaIn(folder)) if (Sources.uses(p).contains("app")) users.add(Sources.relative(p));
        }
        for (Path p : Sources.javaIn("")) {
            if (!p.endsWith("MainActivity.java") && Sources.uses(p).contains("app")) users.add(Sources.relative(p));
        }
        ok("only MainActivity uses app/ (MoviesApp makes and wires every part)", users.isEmpty(), users);
    }

    static void groundFloor() {
        for (String folder : Sources.folders()) {
            ok(folder + "/ is on the ground floor or a feature",
                    MAY_USE.containsKey(folder) || FEATURES.contains(folder));
        }
        for (Map.Entry<String, Set<String>> rule : MAY_USE.entrySet()) {
            List<String> wrong = new ArrayList<>();
            for (Path p : Sources.javaIn(rule.getKey())) {
                for (String used : Sources.uses(p)) {
                    if (!used.equals(rule.getKey()) && !rule.getValue().contains(used)) wrong.add(p.getFileName() + " uses " + used + "/");
                }
            }
            ok(rule.getKey() + "/ uses only " + (rule.getValue().isEmpty() ? "itself" : new TreeSet<>(rule.getValue())), wrong.isEmpty(), wrong);
        }
    }

    static void onlyAppKnowsTheEngines() {
        List<String> wrong = new ArrayList<>();
        for (String folder : Sources.folders()) {
            if (folder.equals("app") || ENGINES.contains(folder)) continue;
            for (Path p : Sources.javaIn(folder)) {
                for (String used : Sources.uses(p)) if (ENGINES.contains(used)) wrong.add(p.getFileName() + " uses " + used + "/");
            }
        }
        ok("only app/ uses web/webview/ and web/gecko/ (the rest knows only a Page)", wrong.isEmpty(), wrong);
        List<String> each = new ArrayList<>();
        for (Path p : Sources.javaIn("web/webview")) if (Sources.uses(p).contains("web/gecko")) each.add(p.getFileName().toString());
        for (Path p : Sources.javaIn("web/gecko")) if (Sources.uses(p).contains("web/webview")) each.add(p.getFileName().toString());
        ok("neither engine uses the other", each.isEmpty(), each);
    }

    static void plainJava() {
        for (String folder : PLAIN) {
            List<String> android = new ArrayList<>();
            for (Path p : Sources.javaIn(folder)) if (Sources.usesAndroid(p)) android.add(p.getFileName().toString());
            ok(folder + "/ is plain Java (tested on a computer)", android.isEmpty(), android);
        }
        List<String> android = new ArrayList<>();
        for (Path p : Sources.files(CODE, ".java")) {
            if (Sources.read(p).contains("Plain Java.") && Sources.usesAndroid(p)) android.add(Sources.relative(p));
        }
        ok("every class that says it's plain Java is", android.isEmpty(), android);
    }

    static void android5() {
        List<String> found = new ArrayList<>();
        for (Path p : Sources.files(CODE, ".java")) {
            String[] lines = Sources.read(p).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String code = lines[i].replaceAll("//.*$|^\\s*\\*.*$|^\\s*/\\*\\*.*$", "");
                if (NEWER.matcher(code).find()) found.add(Sources.relative(p) + ":" + (i + 1) + "  " + lines[i].trim());
            }
        }
        ok("the app uses nothing Android 6.0 doesn't have", found.isEmpty(), found);
    }

    static void documented() {
        String doc = Sources.read(ROOT.resolve("ARCHITECTURE.md"));
        for (String folder : Sources.folders()) {
            ok("ARCHITECTURE.md says what " + folder + "/ is for", doc.contains("`" + folder + "/`"));
        }
        Pattern type = Pattern.compile("^(public )?((final|abstract) )?(class|interface|enum) \\w+", Pattern.MULTILINE);
        List<String> silent = new ArrayList<>();
        List<Path> all = Sources.files(CODE, ".java");
        all.addAll(Sources.files(ROOT.resolve("tools"), ".java"));
        all.addAll(Sources.files(ROOT.resolve("tests"), ".java"));
        for (Path p : all) {
            String s = Sources.read(p);
            Matcher m = type.matcher(s);
            String before = m.find() ? s.substring(0, m.start()).replaceAll("(?m)^@\\w+(\\(.*\\))?\\s*$", "").trim() : "";
            if (!before.endsWith("*/") || before.lastIndexOf("/**") < 0) silent.add(Sources.relative(p));
        }
        ok("every class says what it does at its top", silent.isEmpty(), silent);
        String runner = Sources.read(ROOT.resolve("tests/java/com/twilightcafe/movies/test/AllTests.java"));
        List<String> left = new ArrayList<>();
        for (Path p : Sources.files(ROOT.resolve("tests/java"), "Test.java")) {
            String name = p.getFileName().toString().replace(".java", "");
            if (!runner.contains("TESTS.put(\"" + name + "\"")) left.add(name);
        }
        ok("AllTests runs every test", left.isEmpty(), left);
    }
}
