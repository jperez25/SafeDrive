package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;

/** The body of a health endpoint, e.g. {@code {"status":"UP"}}. */
public class HealthBodyValidator {

    private final JsonNode actual;

    HealthBodyValidator(JsonNode actual) {
        this.actual = actual;
    }

    public HealthBodyValidator hasStatus(String status) {
        assertThat(actual.path("status").asString()).as("health status").isEqualTo(status);
        return this;
    }
}
