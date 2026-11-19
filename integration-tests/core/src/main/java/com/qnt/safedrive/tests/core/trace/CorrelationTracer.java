package com.qnt.safedrive.tests.core.trace;

import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import com.qnt.safedrive.tests.core.utils.Waiter;
import java.util.List;
import java.util.Optional;

/**
 * Follows one correlation ID through topics and service logs. No database reads: this is what a
 * black-box test can see.
 */
public class CorrelationTracer {

    private final TopicReader topics;
    private final LogReader logs;
    private final Waiter waiter;

    public CorrelationTracer(TestEnvironment env, Waiter waiter) {
        this.topics = new TopicReader(env);
        this.logs = new LogReader(env);
        this.waiter = waiter;
    }

    /** Waits until at least {@code count} events with this correlation ID are on the topic. */
    public List<TopicReader.Message> awaitEvents(String topic, String correlationId, int count) {
        return waiter.until(
                count + " event(s) on " + topic + " with correlationId " + correlationId,
                () -> {
                    List<TopicReader.Message> found = topics.readByCorrelationId(topic, correlationId);
                    return found.size() >= count ? Optional.of(found) : Optional.empty();
                });
    }

    /** Waits until a message matching the filter (e.g. a payload field) is on the topic. */
    public TopicReader.Message awaitEvent(
            String topic, String correlationId, java.util.function.Predicate<TopicReader.Message> filter) {
        return waiter.until(
                "matching event on " + topic + " with correlationId " + correlationId,
                () -> topics.readByCorrelationId(topic, correlationId).stream().filter(filter).findFirst());
    }

    /** Current events on the topic with this correlation ID (no waiting). */
    public List<TopicReader.Message> events(String topic, String correlationId) {
        return topics.readByCorrelationId(topic, correlationId);
    }

    /** Log lines of a service that carry the correlation ID (no waiting). */
    public List<LogReader.Line> logLines(String service, String correlationId) {
        return logs.readByCorrelationId(service, correlationId);
    }

    /** Waits until the service has logged at least one line with the correlation ID. */
    public List<LogReader.Line> awaitLog(String service, String correlationId) {
        return waiter.until(
                service + " log lines with correlationId " + correlationId,
                () -> {
                    List<LogReader.Line> found = logs.readByCorrelationId(service, correlationId);
                    return found.isEmpty() ? Optional.empty() : Optional.of(found);
                });
    }

    /** Waits until at least {@code count} messages with this key are on the topic (dead letters, which may not be envelopes). */
    public List<TopicReader.RawMessage> awaitRawByKey(String topic, String key, int count) {
        return waiter.until(
                count + " message(s) on " + topic + " with key " + key,
                () -> {
                    List<TopicReader.RawMessage> found = topics.readRawByKey(topic, key);
                    return found.size() >= count ? Optional.of(found) : Optional.empty();
                });
    }
}
