package com.qnt.safedrive.policyrating;

import com.qnt.safedrive.commons.EventEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies score.updated events to the driver's policy. One transaction per message, and the policy row is
 * locked, so concurrent scores of the same driver are applied one after the other.
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);
    static final String SOURCE = "policy-rating";

    public record Rating(String driverId, int scoreVersion, BigDecimal basePremium, double adjustmentPct,
                         BigDecimal newPremium, String band, Instant updatedAt) {}

    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final BigDecimal defaultBasePremium;

    public RatingService(JdbcClient jdbc, JsonMapper mapper,
                         @Value("${safedrive.rating.default-base-premium:120.00}") BigDecimal defaultBasePremium) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.defaultBasePremium = defaultBasePremium;
    }

    @Transactional
    public void handle(EventEnvelope<ScoreUpdated> event) {
        ScoreUpdated score = event.payload();

        int fresh = jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", event.eventId()).update();
        if (fresh == 0) {
            log.info("duplicate event {} ignored", event.eventId());
            return;
        }

        jdbc.sql("""
                        INSERT INTO policies (driver_id, base_premium, new_premium, band)
                        VALUES (:driver, :base, :base, 'average') ON CONFLICT DO NOTHING""")
                .param("driver", score.driverId()).param("base", defaultBasePremium).update();
        var policy = jdbc.sql("SELECT base_premium, score_version FROM policies WHERE driver_id = :driver FOR UPDATE")
                .param("driver", score.driverId())
                .query((rs, n) -> new Object[] {rs.getBigDecimal(1), rs.getInt(2)}).single();
        BigDecimal base = (BigDecimal) policy[0];
        int applied = (Integer) policy[1];

        if (score.scoreVersion() <= applied) {
            log.info("score version {} of driver {} is not newer than {}, ignored",
                    score.scoreVersion(), score.driverId(), applied);
            return;
        }

        var band = PremiumBands.forScore(score.score());
        BigDecimal newPremium = PremiumBands.apply(base, band);
        Instant now = Instant.now();

        jdbc.sql("""
                        INSERT INTO premium_adjustments (driver_id, score_version, score, band, adjustment_pct, new_premium)
                        VALUES (:driver, :version, :score, :band, :pct, :premium)""")
                .param("driver", score.driverId()).param("version", score.scoreVersion()).param("score", score.score())
                .param("band", band.name()).param("pct", band.adjustmentPct()).param("premium", newPremium).update();
        jdbc.sql("""
                        UPDATE policies SET score_version = :version, adjustment_pct = :pct, new_premium = :premium,
                                            band = :band, updated_at = :at
                        WHERE driver_id = :driver""")
                .param("version", score.scoreVersion()).param("pct", band.adjustmentPct()).param("premium", newPremium)
                .param("band", band.name()).param("at", java.sql.Timestamp.from(now))
                .param("driver", score.driverId()).update();

        var out = EventEnvelope.of("premium.adjusted", 1, SOURCE, event.correlationId(), now,
                new Published.PremiumAdjusted(score.driverId(), score.scoreVersion(), base, band.adjustmentPct(),
                        newPremium, band.name()));
        jdbc.sql("""
                        INSERT INTO outbox (event_id, topic, msg_key, payload, correlation_id)
                        VALUES (:eventId, 'premium.adjusted', :key, :payload, :corr)""")
                .param("eventId", out.eventId()).param("key", score.driverId())
                .param("payload", mapper.writeValueAsString(out)).param("corr", event.correlationId()).update();
        log.info("driver {} rated {} ({}%), premium {} -> {} (score version {})", score.driverId(), band.name(),
                band.adjustmentPct(), base, newPremium, score.scoreVersion());
    }

    /** Current rating, or empty when no score was applied for the driver yet. */
    @Transactional(readOnly = true)
    public Optional<Rating> find(String driverId) {
        return jdbc.sql("""
                        SELECT driver_id, score_version, base_premium, adjustment_pct, new_premium, band, updated_at
                        FROM policies WHERE driver_id = :driver AND score_version > 0""")
                .param("driver", driverId)
                .query((rs, n) -> new Rating(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getDouble(4),
                        rs.getBigDecimal(5), rs.getString(6), rs.getTimestamp(7).toInstant()))
                .optional();
    }
}
