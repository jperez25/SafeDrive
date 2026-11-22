package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.GatewayError;
import io.qameta.allure.Step;

/** The body of a gateway error answer. */
public class GatewayErrorBodyValidator {

    private final GatewayError actual;

    GatewayErrorBodyValidator(GatewayError actual) {
        this.actual = actual;
    }

    @Step("Has status {status}")
    public GatewayErrorBodyValidator hasStatus(String status) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        return this;
    }

    @Step("Has message {message}")
    public GatewayErrorBodyValidator hasMessage(String message) {
        assertThat(actual.message()).as("body message").isEqualTo(message);
        return this;
    }

    @Step("Has message containing {text}")
    public GatewayErrorBodyValidator hasMessageContaining(String text) {
        assertThat(actual.message()).as("body message").contains(text);
        return this;
    }

    @Step("Has correlation id {correlationId}")
    public GatewayErrorBodyValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("body correlationId").isEqualTo(correlationId);
        return this;
    }
}
