package com.qnt.safedrive.tests.core.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

public class AllureReportListenerTest {

    @Test
    public void buildsTheHtmlReportFromTheResults() throws IOException {
        Path dir = Files.createTempDirectory("allure");
        Path results = Files.createDirectory(dir.resolve("results"));
        Path report = dir.resolve("report");
        Files.writeString(results.resolve("1-result.json"), """
                {"uuid":"1","historyId":"1","name":"sample","fullName":"Sample.sample","status":"passed",
                 "stage":"finished","start":1,"stop":2}""");
        String oldResults = System.setProperty("allure.results.directory", results.toString());
        String oldReport = System.setProperty("allure.report.directory", report.toString());
        try {
            new AllureReportListener().onExecutionFinish();
        } finally {
            restore("allure.results.directory", oldResults);
            restore("allure.report.directory", oldReport);
        }
        assertThat(report.resolve("index.html")).exists();
    }

    @Test
    public void missingResultsAreSkippedWithoutFailing() {
        String old = System.setProperty("allure.results.directory", "target/does-not-exist");
        try {
            new AllureReportListener().onExecutionFinish();
        } finally {
            restore("allure.results.directory", old);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
