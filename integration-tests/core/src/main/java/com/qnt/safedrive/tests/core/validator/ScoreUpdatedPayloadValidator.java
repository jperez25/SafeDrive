package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import org.assertj.core.data.Offset;

/** The payload of a {@code score.updated} event. */
public class ScoreUpdatedPayloadValidator {

    private final ScoreUpdated actual;

    ScoreUpdatedPayloadValidator(ScoreUpdated actual) {
        this.actual = actual;
    }

    public ScoreUpdatedPayloadValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("driverId").isEqualTo(driverId);
        return this;
    }

    public ScoreUpdatedPayloadValidator hasScoreVersion(int version) {
        assertThat(actual.scoreVersion()).as("scoreVersion").isEqualTo(version);
        return this;
    }

    /** Scores are rounded to 2 decimals, so compare with a tolerance below that. */
    public ScoreUpdatedPayloadValidator hasScore(double score) {
        assertThat(actual.score()).as("score").isCloseTo(score, Offset.offset(0.005));
        return this;
    }

    public ScoreUpdatedPayloadValidator hasBasedOnTrips(int trips) {
        assertThat(actual.basedOnTrips()).as("basedOnTrips").isEqualTo(trips);
        return this;
    }

    public ScoreUpdatedPayloadValidator hasComputedAt() {
        assertThat(actual.computedAt()).as("computedAt").isNotBlank();
        return this;
    }
}
