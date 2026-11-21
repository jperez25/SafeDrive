package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Step;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** The notifications the mocked provider received for one driver, as read from its journal. */
public class NotificationsValidator {

    private final List<JsonNode> actual;

    NotificationsValidator(List<JsonNode> actual) {
        this.actual = actual;
    }

    @Step("Has {count} notification(s)")
    public NotificationsValidator hasCount(int count) {
        assertThat(actual).as("notifications received by the provider").hasSize(count);
        return this;
    }
}
