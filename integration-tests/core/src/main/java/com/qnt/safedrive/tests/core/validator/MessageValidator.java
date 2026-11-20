package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Step;

/** Master validator for a message on a topic: its topic and key. The envelope is a child. */
public class MessageValidator {

    private final TopicReader.Message message;

    MessageValidator(TopicReader.Message message) {
        this.message = message;
    }

    @Step("Has topic {topic}")
    public MessageValidator hasTopic(String topic) {
        assertThat(message.topic()).as("topic").isEqualTo(topic);
        return this;
    }

    @Step("Has key {key}")
    public MessageValidator hasKey(String key) {
        assertThat(message.key()).as("Kafka key").isEqualTo(key);
        return this;
    }

    @Step("Validate envelope")
    public EventValidator envelope() {
        return new EventValidator(message.envelope());
    }
}
