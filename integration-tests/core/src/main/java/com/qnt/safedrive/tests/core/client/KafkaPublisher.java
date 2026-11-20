package com.qnt.safedrive.tests.core.client;

import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Puts a message on a topic and waits for the broker to acknowledge it. For input that the HTTP edge would refuse:
 * poison events, the same event ID twice, a batch the processor must treat as late. Needs the Kafka forward from
 * {@code platform/scripts/port-forward.sh}.
 */
public class KafkaPublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);

    private final TestEnvironment env;

    public KafkaPublisher(TestEnvironment env) {
        this.env = env;
    }

    /** Sends the value as it is, whether or not it is JSON. */
    @Step("Publish to topic {topic} with key {key}")
    public void send(String topic, String key, @Param(mode = Parameter.Mode.HIDDEN) String value) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties())) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while sending to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("could not send to " + topic, e);
        }
    }

    /** Sends the envelope as JSON; returns the exact text that was sent, so a test can compare it with a dead letter. */
    public String send(String topic, String key, Envelope envelope) {
        String value = Json.write(envelope);
        send(topic, key, value);
        return value;
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.kafkaBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) SEND_TIMEOUT.toMillis());
        return properties;
    }
}
