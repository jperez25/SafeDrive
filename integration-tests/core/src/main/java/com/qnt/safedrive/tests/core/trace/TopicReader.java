package com.qnt.safedrive.tests.core.trace;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.core.JacksonException;

/**
 * Reads a topic from the beginning with a Kafka consumer (no consumer group, nothing is committed, so reading
 * never disturbs a service). Messages that are not valid envelopes (poison messages) are skipped.
 * Needs the Kafka forward from {@code platform/scripts/port-forward.sh}.
 */
public class TopicReader {

    /** A message as it sits on the topic. */
    public record Message(String topic, String key, Envelope envelope) {}

    private static final Duration POLL = Duration.ofMillis(200);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(10);

    private final TestEnvironment env;

    public TopicReader(TestEnvironment env) {
        this.env = env;
    }

    /** Every message on the topic at the moment of the call. */
    public List<Message> read(String topic) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties())) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);

            List<Message> messages = new ArrayList<>();
            long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
            while (!caughtUp(consumer, end)) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("could not read all of topic " + topic + " in " + READ_TIMEOUT);
                }
                for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                    toMessage(topic, record, messages);
                }
            }
            return messages;
        }
    }

    /** Messages on the topic that carry the correlation ID. */
    public List<Message> readByCorrelationId(String topic, String correlationId) {
        return read(topic).stream()
                .filter(m -> correlationId.equals(m.envelope().correlationId()))
                .toList();
    }

    private static boolean caughtUp(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> end) {
        return end.entrySet().stream().allMatch(e -> consumer.position(e.getKey()) >= e.getValue());
    }

    private static void toMessage(String topic, ConsumerRecord<String, String> record, List<Message> into) {
        String value = record.value();
        if (value == null || !value.startsWith("{")) {
            return;
        }
        try {
            into.add(new Message(topic, record.key(), Json.read(value, Envelope.class)));
        } catch (JacksonException e) {
            // Not an envelope (e.g. a deliberate poison message from a failure-mode test): not ours to judge here.
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.kafkaBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) API_TIMEOUT.toMillis());
        return properties;
    }
}
