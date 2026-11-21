package com.qnt.safedrive.rewards;

import com.qnt.safedrive.commons.EventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Grants and notifies rewards. Idempotent on (driver, score version): the row is the guard, so a duplicate event
 * never creates a second reward, and a reward still pending (provider failed before) is notified again.
 * The provider is called outside any transaction.
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    public record Reward(UUID rewardId, int scoreVersion, String kind, String status, Instant createdAt,
                         Instant notifiedAt) {}

    private final JdbcClient jdbc;
    private final NotificationClient notifications;
    private final MeterRegistry meters;

    public RewardService(JdbcClient jdbc, NotificationClient notifications, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.meters = meters;
    }

    public void handle(EventEnvelope<PremiumAdjusted> event) {
        PremiumAdjusted premium = event.payload();
        var kind = RewardRule.kindFor(premium.band());
        if (kind.isEmpty()) {
            log.info("band {} of driver {} earns no reward", premium.band(), premium.driverId());
            return;
        }

        jdbc.sql("""
                        INSERT INTO rewards (reward_id, driver_id, score_version, kind)
                        VALUES (:id, :driver, :version, :kind) ON CONFLICT (driver_id, score_version) DO NOTHING""")
                .param("id", UUID.randomUUID()).param("driver", premium.driverId())
                .param("version", premium.scoreVersion()).param("kind", kind.get()).update();
        var row = jdbc.sql("SELECT reward_id, status FROM rewards WHERE driver_id = :driver AND score_version = :version")
                .param("driver", premium.driverId()).param("version", premium.scoreVersion())
                .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)}).single();
        UUID rewardId = (UUID) row[0];
        if (!"pending".equals(row[1])) {
            log.info("reward {} of driver {} already notified, duplicate ignored", rewardId, premium.driverId());
            return;
        }

        jdbc.sql("UPDATE rewards SET attempts = attempts + 1 WHERE reward_id = :id").param("id", rewardId).update();
        notifications.send(rewardId, premium.driverId(), kind.get());
        jdbc.sql("UPDATE rewards SET status = 'notified', notified_at = now() WHERE reward_id = :id")
                .param("id", rewardId).update();
        meters.counter("safedrive.rewards.notified", "kind", kind.get()).increment();
        log.info("reward {} ({}) of driver {} notified (score version {})", rewardId, kind.get(), premium.driverId(),
                premium.scoreVersion());
    }

    public List<Reward> find(String driverId) {
        return jdbc.sql("""
                        SELECT reward_id, score_version, kind, status, created_at, notified_at FROM rewards
                        WHERE driver_id = :driver ORDER BY score_version""")
                .param("driver", driverId)
                .query((rs, n) -> new Reward(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
                        rs.getString(4), rs.getTimestamp(5).toInstant(),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()))
                .list();
    }
}
