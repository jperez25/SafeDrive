package com.qnt.safedrive.tripprocessor;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assembles trips from batches and publishes the results through the outbox. Everything for one
 * message happens in one transaction, and the trip row is locked, so concurrent batches of the same
 * trip are serialized.
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    static final String SOURCE = "trip-processor";

    private record Trip(String tripId, String driverId, String status, int revision, Integer lastSeq, String correlationId) {}

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public TripService(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void handle(EventEnvelope<TripRaw> event) {
        TripRaw raw = event.payload();

        int fresh = jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", event.eventId()).update();
        if (fresh == 0) {
            log.info("duplicate event {} ignored", event.eventId());
            return;
        }

        jdbc.sql("""
                        INSERT INTO trips (trip_id, driver_id, correlation_id) VALUES (:trip, :driver, :corr)
                        ON CONFLICT (trip_id) DO NOTHING""")
                .param("trip", raw.tripId()).param("driver", raw.driverId()).param("corr", event.correlationId())
                .update();
        lock(raw.tripId());

        int stored = jdbc.sql("""
                        INSERT INTO samples (trip_id, batch_seq, data) VALUES (:trip, :seq, :data)
                        ON CONFLICT (trip_id, batch_seq) DO NOTHING""")
                .param("trip", raw.tripId()).param("seq", raw.batchSeq())
                .param("data", mapper.writeValueAsString(raw.samples()))
                .update();
        if (stored == 0) {
            log.info("batch {} of trip {} already stored, ignored", raw.batchSeq(), raw.tripId());
            return;
        }

        jdbc.sql("""
                        UPDATE trips SET last_activity_at = now(), correlation_id = :corr,
                                         last_seq = CASE WHEN :last THEN :seq ELSE last_seq END
                        WHERE trip_id = :trip""")
                .param("corr", event.correlationId()).param("last", raw.last()).param("seq", raw.batchSeq())
                .param("trip", raw.tripId()).update();

        Trip trip = lock(raw.tripId());
        if ("CLOSED".equals(trip.status()) || complete(trip)) {
            finish(trip);   // closed trip + new data = late batch: republish as the next revision
        }
    }

    /** Closes open trips that saw no batch since {@code cutoff}, with whatever has arrived. */
    @Transactional
    public int closeIdle(Instant cutoff) {
        List<String> ids = jdbc.sql("""
                        SELECT trip_id FROM trips WHERE status = 'OPEN' AND last_activity_at < :cutoff
                        ORDER BY last_activity_at LIMIT 50 FOR UPDATE SKIP LOCKED""")
                .param("cutoff", Timestamp.from(cutoff))
                .query(String.class).list();
        for (String id : ids) {
            Trip trip = lock(id);
            try (CorrelationId.Scope ignored = CorrelationId.bind(trip.correlationId())) {
                log.info("closing idle trip {}", id);
                finish(trip);
            }
        }
        return ids.size();
    }

    private Trip lock(String tripId) {
        return jdbc.sql("""
                        SELECT trip_id, driver_id, status, revision, last_seq, correlation_id
                        FROM trips WHERE trip_id = :trip FOR UPDATE""")
                .param("trip", tripId)
                .query((rs, n) -> new Trip(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        (Integer) rs.getObject(5), rs.getString(6)))
                .single();
    }

    /** The last batch is known and every batchSeq from 0 to it has arrived. */
    private boolean complete(Trip trip) {
        if (trip.lastSeq() == null) {
            return false;
        }
        int have = jdbc.sql("SELECT count(*) FROM samples WHERE trip_id = :trip AND batch_seq <= :last")
                .param("trip", trip.tripId()).param("last", trip.lastSeq()).query(Integer.class).single();
        return have == trip.lastSeq() + 1;
    }

    private void finish(Trip trip) {
        List<TripRaw.Sample> samples = new ArrayList<>();
        for (String json : jdbc.sql("SELECT data FROM samples WHERE trip_id = :trip ORDER BY batch_seq")
                .param("trip", trip.tripId()).query(String.class).list()) {
            samples.addAll(mapper.readValue(json, new TypeReference<List<TripRaw.Sample>>() {}));
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("trip " + trip.tripId() + " has no samples");
        }

        for (DrivingEventDetector.Detected d : DrivingEventDetector.detect(samples)) {
            int isNew = jdbc.sql("""
                            INSERT INTO driving_events (trip_id, kind, ts) VALUES (:trip, :kind, :ts)
                            ON CONFLICT DO NOTHING""")
                    .param("trip", trip.tripId()).param("kind", d.kind().name()).param("ts", Timestamp.from(d.ts()))
                    .update();
            if (isNew == 1) {
                publish("driving.event", trip.driverId(), trip.correlationId(), d.ts(), new Published.DrivingEvent(
                        trip.tripId(), trip.driverId(), d.kind().name(), d.severity().name(), d.ts(), d.lat(), d.lon()));
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DrivingEventDetector.Kind kind : DrivingEventDetector.Kind.values()) {
            counts.put(kind.name(), 0);
        }
        jdbc.sql("SELECT kind, count(*) FROM driving_events WHERE trip_id = :trip GROUP BY kind")
                .param("trip", trip.tripId())
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .list().forEach(e -> counts.put(e.getKey(), e.getValue()));

        TripSummary s = TripSummary.of(samples);
        int revision = trip.revision() + 1;
        publish("trip.completed", trip.driverId(), trip.correlationId(), s.endedAt(), new Published.TripCompleted(
                trip.tripId(), trip.driverId(), revision, s.startedAt(), s.endedAt(), s.distanceKm(), s.durationS(),
                s.avgSpeedKmh(), s.maxSpeedKmh(), counts));

        jdbc.sql("UPDATE trips SET status = 'CLOSED', revision = :rev, closed_at = now() WHERE trip_id = :trip")
                .param("rev", revision).param("trip", trip.tripId()).update();
        log.info("trip {} closed, revision {}", trip.tripId(), revision);
    }

    private <T> void publish(String type, String key, String correlationId, Instant occurredAt, T payload) {
        EventEnvelope<T> event = EventEnvelope.of(type, 1, SOURCE, correlationId, occurredAt, payload);
        jdbc.sql("""
                        INSERT INTO outbox (event_id, topic, msg_key, payload, correlation_id)
                        VALUES (:eventId, :topic, :key, :payload, :corr)""")
                .param("eventId", event.eventId()).param("topic", type).param("key", key)
                .param("payload", mapper.writeValueAsString(event)).param("corr", correlationId)
                .update();
    }
}
