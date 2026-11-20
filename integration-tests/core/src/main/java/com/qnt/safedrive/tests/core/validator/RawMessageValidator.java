package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;

/** Master validator for a message read without parsing (a dead letter): topic, key and the untouched value. */
public class RawMessageValidator {

    private final TopicReader.RawMessage message;

    RawMessageValidator(TopicReader.RawMessage message) {
        this.message = message;
    }

    @Step("Has topic {topic}")
    public RawMessageValidator hasTopic(String topic) {
        assertThat(message.topic()).as("topic").isEqualTo(topic);
        return this;
    }

    @Step("Has key {key}")
    public RawMessageValidator hasKey(String key) {
        assertThat(message.key()).as("Kafka key").isEqualTo(key);
        return this;
    }

    /** The value is byte for byte what was sent: a dead letter never alters the original event. */
    @Step("Has value")
    public RawMessageValidator hasValue(@Param(mode = Parameter.Mode.HIDDEN) String value) {
        assertThat(message.value()).as("value").isEqualTo(value);
        return this;
    }
}
