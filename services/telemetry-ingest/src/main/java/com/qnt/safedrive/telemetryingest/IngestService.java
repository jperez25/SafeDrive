package com.qnt.safedrive.telemetryingest;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dedup and outbox. The primary key on {@code ingested_batches.batch_id} is the single arbiter: the
 * insert and the outbox row share one transaction, so concurrent identical requests publish once.
 */
@Service
public class IngestService {

    static final String TOPIC = "trip.raw";
    static final String SOURCE = "telemetry-ingest";

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public IngestService(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * @param original the batch as received (its hash identifies the content)
     * @param result   the validated batch to publish
     */
    @Transactional
    public IngestResult ingest(TelemetryBatch original, BatchValidator.Result result) {
        TelemetryBatch batch = result.accepted();
        String correlationId = CorrelationId.orGenerate(CorrelationId.current());
        String hash = hash(original);

        int inserted = jdbc.sql("""
                        INSERT INTO ingested_batches (batch_id, trip_id, driver_id, payload_hash, status, correlation_id)
                        VALUES (:batchId, :tripId, :driverId, :hash, 'ACCEPTED', :correlationId)
                        ON CONFLICT (batch_id) DO NOTHING""")
                .param("batchId", batch.batchId())
                .param("tripId", batch.tripId())
                .param("driverId", batch.driverId())
                .param("hash", hash)
                .param("correlationId", correlationId)
                .update();

        if (inserted == 0) {
            var existing = jdbc.sql("SELECT payload_hash, correlation_id FROM ingested_batches WHERE batch_id = :id")
                    .param("id", batch.batchId())
                    .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)})
                    .single();
            boolean same = existing[0].equals(hash);
            return new IngestResult(
                    same ? IngestResult.Status.DUPLICATE : IngestResult.Status.CONFLICT,
                    batch.batchId(), existing[1], 0, List.of());
        }

        Instant occurredAt = batch.samples().stream()
                .map(s -> Instant.parse(s.ts()))
                .max(Instant::compareTo)
                .orElseThrow();
        EventEnvelope<TelemetryBatch> event = EventEnvelope.of(TOPIC, 1, SOURCE, correlationId, occurredAt, batch);
        jdbc.sql("""
                        INSERT INTO outbox (event_id, topic, msg_key, payload, correlation_id)
                        VALUES (:eventId, :topic, :key, :payload, :correlationId)""")
                .param("eventId", event.eventId())
                .param("topic", TOPIC)
                .param("key", batch.tripId())
                .param("payload", mapper.writeValueAsString(event))
                .param("correlationId", correlationId)
                .update();

        return new IngestResult(
                IngestResult.Status.ACCEPTED, batch.batchId(), correlationId, batch.samples().size(), result.rejected());
    }

    /** SHA-256 of the batch as received, serialized with a fixed field order. */
    String hash(TelemetryBatch batch) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(batch).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
