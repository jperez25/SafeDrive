package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.trace.TopicReader;

/** Master validator for a message on a topic: its topic and key. The envelope is a child. */
public class MessageValidator {

    private final TopicReader.Message message;

    MessageValidator(TopicReader.Message message) {
        this.message = message;
    }

    public MessageValidator hasTopic(String topic) {
        assertThat(message.topic()).as("topic").isEqualTo(topic);
        return this;
    }

    public MessageValidator hasKey(String key) {
        assertThat(message.key()).as("Kafka key").isEqualTo(key);
        return this;
    }

    public EventValidator envelope() {
        return new EventValidator(message.envelope());
    }
}
