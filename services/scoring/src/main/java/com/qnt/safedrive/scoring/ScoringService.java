package com.qnt.safedrive.scoring;

import com.qnt.safedrive.commons.EventEnvelope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies trip.completed events and recomputes the driver score. One transaction per message, and the
 * driver row is locked, so two trips of the same driver never produce the same scoreVersion.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
    static final String SOURCE = "scoring";

    public record DriverScore(String driverId, double score, int scoreVersion, int basedOnTrips, Instant computedAt) {}

    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final int window;

    public ScoringService(JdbcClient jdbc, JsonMapper mapper, @Value("${safedrive.scoring.window-trips:10}") int window) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.window = window;
    }

    @Transactional
    public void handle(EventEnvelope<TripCompleted> event) {
        TripCompleted trip = event.payload();

        int fresh = jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", event.eventId()).update();
        if (fresh == 0) {
            log.info("duplicate event {} ignored", event.eventId());
            return;
        }

        // Lock the driver first: every write for this driver is serialized from here on.
        jdbc.sql("INSERT INTO driver_scores (driver_id) VALUES (:driver) ON CONFLICT DO NOTHING")
                .param("driver", trip.driverId()).update();
        int version = jdbc.sql("SELECT score_version FROM driver_scores WHERE driver_id = :driver FOR UPDATE")
                .param("driver", trip.driverId()).query(Integer.class).single();

        Optional<Integer> known = jdbc.sql("SELECT revision FROM trip_scores WHERE trip_id = :trip")
                .param("trip", trip.tripId()).query(Integer.class).optional();
        if (known.isPresent() && known.get() >= trip.revision()) {
            log.info("trip {} revision {} is not newer than {}, ignored", trip.tripId(), trip.revision(), known.get());
            return;
        }

        jdbc.sql("""
                        INSERT INTO trip_scores (trip_id, driver_id, revision, trip_score, ended_at)
                        VALUES (:trip, :driver, :rev, :score, :ended)
                        ON CONFLICT (trip_id) DO UPDATE SET revision = :rev, trip_score = :score,
                                                            ended_at = :ended, updated_at = now()""")
                .param("trip", trip.tripId()).param("driver", trip.driverId()).param("rev", trip.revision())
                .param("score", TripScorer.tripScore(trip.distanceKm(), trip.eventCounts()))
                .param("ended", Timestamp.from(trip.endedAt())).update();

        List<Double> recent = jdbc.sql("""
                        SELECT trip_score FROM trip_scores WHERE driver_id = :driver
                        ORDER BY ended_at DESC, trip_id LIMIT :n""")
                .param("driver", trip.driverId()).param("n", window).query(Double.class).list();
        double score = TripScorer.driverScore(recent);
        Instant now = Instant.now();
        int newVersion = version + 1;

        jdbc.sql("""
                        UPDATE driver_scores SET score_version = :version, score = :score,
                                                 based_on_trips = :trips, computed_at = :at
                        WHERE driver_id = :driver""")
                .param("version", newVersion).param("score", score).param("trips", recent.size())
                .param("at", Timestamp.from(now)).param("driver", trip.driverId()).update();

        var out = EventEnvelope.of("score.updated", 1, SOURCE, event.correlationId(), now,
                new Published.ScoreUpdated(trip.driverId(), newVersion, score, recent.size(), now));
        jdbc.sql("""
                        INSERT INTO outbox (event_id, topic, msg_key, payload, correlation_id)
                        VALUES (:eventId, 'score.updated', :key, :payload, :corr)""")
                .param("eventId", out.eventId()).param("key", trip.driverId())
                .param("payload", mapper.writeValueAsString(out)).param("corr", event.correlationId()).update();
        log.info("driver {} scored {} (version {}, {} trips) after trip {} revision {}", trip.driverId(), score,
                newVersion, recent.size(), trip.tripId(), trip.revision());
    }

    /** Current score, or empty when the driver has no scored trip yet. */
    @Transactional(readOnly = true)
    public Optional<DriverScore> find(String driverId) {
        return jdbc.sql("""
                        SELECT driver_id, score, score_version, based_on_trips, computed_at
                        FROM driver_scores WHERE driver_id = :driver AND score_version > 0""")
                .param("driver", driverId)
                .query((rs, n) -> new DriverScore(rs.getString(1), rs.getDouble(2), rs.getInt(3), rs.getInt(4),
                        rs.getTimestamp(5).toInstant()))
                .optional();
    }
}
