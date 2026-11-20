package com.qnt.safedrive.scoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retry 3 times with backoff, then publish the untouched record (same key, same envelope) to
 * {@code <topic>.scoring.dlt} with the failure reason in headers. Unprocessable events skip the retries.
 */
@Configuration
class KafkaErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandling.class);
    static final String GROUP = "scoring";

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template, MeterRegistry meters) {
        var recoverer = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new TopicPartition(record.topic() + "." + GROUP + ".dlt", record.partition()));
        // Log the reason (never the payload) so a dead-lettered event is visible in the logs.
        org.springframework.kafka.listener.ConsumerAwareRecordRecoverer logAndRecover = (record, consumer, ex) -> {
            log.error("dead-lettering {}-{}@{} key={}: {}", record.topic(), record.partition(), record.offset(),
                    record.key(), ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            meters.counter("safedrive.events.dead_lettered", "topic", record.topic()).increment();
            recoverer.accept(record, consumer, ex);
        };
        var backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        var handler = new DefaultErrorHandler(logAndRecover, backOff);
        handler.addNotRetryableExceptions(UnprocessableEventException.class);
        return handler;
    }
}
