package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.utils.Ids;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Valid {@code score.updated} payloads with a unique driver: version 1, score 95 (band excellent).
 * Override with {@code withX}, e.g. {@code aScoreUpdated().withScore(50).withScoreVersion(2)}.
 */
public final class ScoreUpdatedFactory {

    private ScoreUpdatedFactory() {}

    public static ScoreUpdated aScoreUpdated() {
        return aScoreUpdatedFor(Ids.driver(), 1, 95.0);
    }

    /** A score of this driver at the given version. */
    public static ScoreUpdated aScoreUpdatedFor(String driverId, int scoreVersion, double score) {
        return ScoreUpdated.builder()
                .driverId(driverId)
                .scoreVersion(scoreVersion)
                .score(score)
                .basedOnTrips(scoreVersion)
                .computedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                .build();
    }
}
