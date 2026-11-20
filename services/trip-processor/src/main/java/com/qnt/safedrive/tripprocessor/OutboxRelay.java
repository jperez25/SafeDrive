package com.qnt.safedrive.tripprocessor;

import com.qnt.safedrive.commons.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes unsent outbox rows to Kafka and marks them sent. A crash between send and commit
 * re-publishes the row (at-least-once); consumers dedupe on the event ID.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private record Row(long id, UUID eventId, String topic, String key, String payload, String correlationId) {}

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${safedrive.outbox.poll-ms:500}")
    public void poll() {
        try {
            while (relayBatch() == BATCH_SIZE) {
                // keep draining while the outbox is full
            }
        } catch (Exception e) {
            log.warn("outbox relay failed, will retry: {}", e.toString());
        }
    }

    /** Sends one batch in a single transaction; returns the number of rows published. */
    @Transactional
    public int relayBatch() throws Exception {
        List<Row> rows = jdbc.sql("""
                        SELECT id, event_id, topic, msg_key, payload, correlation_id FROM outbox
                        WHERE sent_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED""")
                .param("limit", BATCH_SIZE)
                .query((rs, n) -> new Row(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)))
                .list();
        for (Row row : rows) {
            ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
            record.headers().add(CorrelationId.KAFKA_HEADER, row.correlationId().getBytes(StandardCharsets.UTF_8));
            kafka.send(record).get(10, TimeUnit.SECONDS);
            jdbc.sql("UPDATE outbox SET sent_at = now() WHERE id = :id").param("id", row.id()).update();
            meters.counter("safedrive.events.published", "topic", row.topic()).increment();
            try (CorrelationId.Scope ignored = CorrelationId.bind(row.correlationId())) {
                log.info("published event {} to {}", row.eventId(), row.topic());
            }
        }
        return rows.size();
    }
}
