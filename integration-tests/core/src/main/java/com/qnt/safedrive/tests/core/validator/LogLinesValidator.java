package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.trace.LogReader;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;
import java.util.List;

/** Log lines a service wrote for a request (fetched by the test, see {@code CorrelationTracer}). */
public class LogLinesValidator {

    private final List<LogReader.Line> actual;

    LogLinesValidator(List<LogReader.Line> actual) {
        this.actual = actual;
    }

    @Step("Has log lines")
    public LogLinesValidator hasLines() {
        assertThat(actual).as("log lines").isNotEmpty();
        return this;
    }

    /** None of the values (a password, a token) appears in any line. */
    @Step("Never logs the given secrets")
    public LogLinesValidator neverLog(@Param(mode = Parameter.Mode.HIDDEN) String... secrets) {
        for (String secret : secrets) {
            assertThat(actual).as("log lines").noneSatisfy(line -> assertThat(line.json()).contains(secret));
        }
        return this;
    }
}
