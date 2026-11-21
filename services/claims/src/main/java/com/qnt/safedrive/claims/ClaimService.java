package com.qnt.safedrive.claims;

import com.qnt.safedrive.commons.EventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a claim per crash and keeps a copy of the driver's policy. A claim is idempotent on (trip, crash time).
 * driving.event and premium.adjusted are different topics, so the crash can arrive before the policy: the claim
 * waits as {@code pending_policy} and is opened when the policy arrives. Both paths take the same per-driver lock,
 * otherwise a crash and a policy handled at the same moment could miss each other and leave the claim waiting.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    public record Claim(UUID claimId, String driverId, String tripId, String status, String severity, Instant crashAt,
                        double lat, double lon, Integer policyScoreVersion, Instant createdAt) {}

    private final JdbcClient jdbc;
    private final MeterRegistry meters;

    public ClaimService(JdbcClient jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
    }

    @Transactional
    public void onCrash(EventEnvelope<DrivingEvent> event) {
        DrivingEvent crash = event.payload();
        lock(crash.driverId());
        int inserted = jdbc.sql("""
                        INSERT INTO claims (claim_id, driver_id, trip_id, crash_ts, severity, lat, lon, correlation_id,
                                            status, policy_score_version)
                        SELECT :id, :driver, :trip, :ts, :severity, :lat, :lon, :correlation,
                               CASE WHEN p.score_version IS NULL THEN 'pending_policy' ELSE 'opened' END,
                               p.score_version
                        FROM (SELECT 1) one LEFT JOIN policies p ON p.driver_id = :driver
                        ON CONFLICT (trip_id, crash_ts) DO NOTHING""")
                .param("id", UUID.randomUUID()).param("driver", crash.driverId()).param("trip", crash.tripId())
                .param("ts", java.sql.Timestamp.from(crash.ts())).param("severity", crash.severity())
                .param("lat", crash.lat()).param("lon", crash.lon()).param("correlation", event.correlationId())
                .update();
        if (inserted == 0) {
            log.info("claim for trip {} at {} already exists, duplicate ignored", crash.tripId(), crash.ts());
            return;
        }
        meters.counter("safedrive.claims.opened", "severity", crash.severity()).increment();
        log.info("claim opened for driver {} trip {} (severity {})", crash.driverId(), crash.tripId(), crash.severity());
    }

    @Transactional
    public void onPolicy(EventEnvelope<PremiumAdjusted> event) {
        PremiumAdjusted premium = event.payload();
        lock(premium.driverId());
        int stored = jdbc.sql("""
                        INSERT INTO policies (driver_id, score_version, base_premium, new_premium, band)
                        VALUES (:driver, :version, :base, :premium, :band)
                        ON CONFLICT (driver_id) DO UPDATE SET score_version = EXCLUDED.score_version,
                            base_premium = EXCLUDED.base_premium, new_premium = EXCLUDED.new_premium,
                            band = EXCLUDED.band, updated_at = now()
                        WHERE policies.score_version < EXCLUDED.score_version""")
                .param("driver", premium.driverId()).param("version", premium.scoreVersion())
                .param("base", premium.basePremium()).param("premium", premium.newPremium())
                .param("band", premium.band()).update();
        if (stored == 0) {
            log.info("policy of driver {} already at score version {} or newer, ignored", premium.driverId(),
                    premium.scoreVersion());
        }
        // Also after a stale event: harmless, and it repairs a claim that was left waiting.
        int opened = jdbc.sql("""
                        UPDATE claims c SET status = 'opened', policy_score_version = p.score_version, updated_at = now()
                        FROM policies p
                        WHERE p.driver_id = c.driver_id AND c.driver_id = :driver AND c.status = 'pending_policy'""")
                .param("driver", premium.driverId()).update();
        if (opened > 0) {
            log.info("{} waiting claim(s) of driver {} opened with the policy", opened, premium.driverId());
        }
    }

    private void lock(String driverId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:driver))").param("driver", driverId).query().singleValue();
    }

    private static final String COLUMNS = """
            claim_id, driver_id, trip_id, status, severity, crash_ts, lat, lon, policy_score_version, created_at""";

    public List<Claim> find(String driverId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM claims WHERE driver_id = :driver ORDER BY created_at DESC, claim_id")
                .param("driver", driverId).query(ClaimService::claim).list();
    }

    public Optional<Claim> findOne(UUID claimId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM claims WHERE claim_id = :id")
                .param("id", claimId).query(ClaimService::claim).optional();
    }

    private static Claim claim(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Claim(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getDouble(7), rs.getDouble(8),
                rs.getObject(9, Integer.class), rs.getTimestamp(10).toInstant());
    }
}
