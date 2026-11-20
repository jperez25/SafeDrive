package com.qnt.safedrive.tests.core.listener;

import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Tells the Allure report where the run happened. At suite start it writes {@code environment.properties}
 * (environment name, endpoints and, in GitHub Actions, the run and commit) into the Allure results directory. Registered through {@code META-INF/services}, so every module gets it with
 * any suite file. The report itself is built afterwards by {@code ./mvnw allure:report}.
 */
public class AllureMetadataListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        Path dir = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("environment.properties"), environmentProperties());
        } catch (IOException e) {
            // Metadata is a convenience: never fail a test run because of it.
            System.err.println("could not write Allure metadata: " + e);
        }
    }

    static String environmentProperties() {
        TestEnvironment env = TestEnvironment.current();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("environment", env.name());
        p.put("namespace", env.namespace());
        p.put("kafka", env.kafkaBootstrapServers());
        p.put("waitTimeout", env.waitTimeout().toString());
        env.services().forEach((name, url) -> p.put("service." + name, url));
        p.put("suite", System.getProperty("suite", "unknown"));
        String repo = System.getenv("GITHUB_REPOSITORY");
        if (repo != null) { // running in GitHub Actions
            p.put("ci.run", System.getenv().getOrDefault("GITHUB_SERVER_URL", "https://github.com") + "/" + repo
                    + "/actions/runs/" + System.getenv("GITHUB_RUN_ID"));
            p.put("ci.ref", System.getenv().getOrDefault("GITHUB_REF_NAME", "?"));
            p.put("ci.commit", System.getenv().getOrDefault("GITHUB_SHA", "?"));
        }
        StringBuilder out = new StringBuilder();
        p.forEach((k, v) -> out.append(k).append('=').append(v).append('\n'));
        return out.toString();
    }
}
