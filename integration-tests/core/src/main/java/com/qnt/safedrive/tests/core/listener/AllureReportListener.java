package com.qnt.safedrive.tests.core.listener;

import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.IExecutionListener;

/**
 * Builds the static Allure HTML report from the raw results once the whole run is over.
 * Uses {@code onExecutionFinish} so every suite has finished writing its results (and
 * {@link AllureMetadataListener} its {@code environment.properties}) first.
 * Generation only: serving the report (e.g. {@code allure open}) is left to the user.
 * Never throws, so a report problem cannot change the test run's outcome.
 */
public class AllureReportListener implements IExecutionListener {

    @Override
    public void onExecutionFinish() {
        Path results = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        Path report = Path.of(System.getProperty("allure.report.directory", "target/allure-report"));
        if (!Files.isDirectory(results)) {
            System.err.println("Allure report skipped: no results directory at " + results.toAbsolutePath());
            return;
        }
        try {
            new ReportGenerator(ConfigurationBuilder.bundled().build()).generate(report, List.of(results));
            System.out.println("Allure report generated: " + report.toAbsolutePath().resolve("index.html"));
        } catch (Exception e) {
            System.err.println("Allure report generation failed: " + e);
        }
    }
}
