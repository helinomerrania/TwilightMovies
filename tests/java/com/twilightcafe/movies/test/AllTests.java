package com.twilightcafe.movies.test;

import com.twilightcafe.movies.ArchitectureTest;
import com.twilightcafe.movies.adblock.RequestKindTest;
import com.twilightcafe.movies.adblock.SitePolicyTest;
import com.twilightcafe.movies.adblock.compiler.CompilerTest;
import com.twilightcafe.movies.adblock.engine.CosmeticRulesTest;
import com.twilightcafe.movies.adblock.engine.NetworkRulesTest;
import com.twilightcafe.movies.adblock.engine.OptionsTest;
import com.twilightcafe.movies.adblock.engine.PublicSuffixTest;
import com.twilightcafe.movies.adblock.surrogates.SurrogatesTest;
import com.twilightcafe.movies.loading.BackoffTest;
import com.twilightcafe.movies.sites.SiteTest;
import com.twilightcafe.movies.update.ReleaseTest;
import com.twilightcafe.movies.util.UrlsTest;
import com.twilightcafe.movies.web.HistoryTest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the Java tests (tests/java/run.sh): the app's plain-Java parts and the filter compiler, on a computer,
 * and the architecture's rules. Each test is a class with a run(); a name or two runs only those.
 */
public final class AllTests {
    interface Test {
        void run() throws Exception;
    }

    /** Every test, by name (ArchitectureTest checks that none is left out). */
    static final Map<String, Test> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("ArchitectureTest", ArchitectureTest::run);
        TESTS.put("UrlsTest", UrlsTest::run);
        TESTS.put("SiteTest", SiteTest::run);
        TESTS.put("BackoffTest", BackoffTest::run);
        TESTS.put("ReleaseTest", ReleaseTest::run);
        TESTS.put("PublicSuffixTest", PublicSuffixTest::run);
        TESTS.put("NetworkRulesTest", NetworkRulesTest::run);
        TESTS.put("OptionsTest", OptionsTest::run);
        TESTS.put("CosmeticRulesTest", CosmeticRulesTest::run);
        TESTS.put("CompilerTest", CompilerTest::run);
        TESTS.put("SurrogatesTest", SurrogatesTest::run);
        TESTS.put("SitePolicyTest", SitePolicyTest::run);
        TESTS.put("RequestKindTest", RequestKindTest::run);
        TESTS.put("HistoryTest", HistoryTest::run);
    }

    private AllTests() {
    }

    public static void main(String[] args) {
        List<String> only = Arrays.asList(args);
        for (Map.Entry<String, Test> t : TESTS.entrySet()) {
            if (!only.isEmpty() && !only.contains(t.getKey())) continue;
            System.out.println(t.getKey() + ":");
            int before = Check.pass + Check.fail;
            try {
                t.getValue().run();
            } catch (Throwable e) {
                Check.ok(t.getKey() + " ran to its end", false, e);
                e.printStackTrace(System.out);
            }
            if (Check.pass + Check.fail == before) Check.ok(t.getKey() + " checked something", false);
        }
        System.out.println("The Java tests: " + Check.pass + " passed, " + Check.fail + " failed");
        System.exit(Check.fail == 0 ? 0 : 1);
    }
}
