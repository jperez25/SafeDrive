package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.trace.TopicReader;

/** Master validator for a message read without parsing (a dead letter): topic, key and the untouched value. */
public class RawMessageValidator {

    private final TopicReader.RawMessage message;

    RawMessageValidator(TopicReader.RawMessage message) {
        this.message = message;
    }

    public RawMessageValidator hasTopic(String topic) {
        assertThat(message.topic()).as("topic").isEqualTo(topic);
        return this;
    }

    public RawMessageValidator hasKey(String key) {
        assertThat(message.key()).as("Kafka key").isEqualTo(key);
        return this;
    }

    /** The value is byte for byte what was sent: a dead letter never alters the original event. */
    public RawMessageValidator hasValue(String value) {
        assertThat(message.value()).as("value").isEqualTo(value);
        return this;
    }
}
