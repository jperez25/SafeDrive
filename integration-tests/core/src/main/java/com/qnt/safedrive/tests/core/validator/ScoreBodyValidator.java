package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.ScoreResponse;
import org.assertj.core.data.Offset;

/** The body of the score API: the score (200) or the not-found answer (404). */
public class ScoreBodyValidator {

    private final ScoreResponse actual;

    ScoreBodyValidator(ScoreResponse actual) {
        this.actual = actual;
    }

    public ScoreBodyValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        return this;
    }

    public ScoreBodyValidator hasScore(double score) {
        assertThat(actual.score()).as("body score").isNotNull().isCloseTo(score, Offset.offset(0.005));
        return this;
    }

    public ScoreBodyValidator hasScoreVersion(int version) {
        assertThat(actual.scoreVersion()).as("body scoreVersion").isEqualTo(version);
        return this;
    }

    public ScoreBodyValidator hasBasedOnTrips(int trips) {
        assertThat(actual.basedOnTrips()).as("body basedOnTrips").isEqualTo(trips);
        return this;
    }

    public ScoreBodyValidator hasComputedAt() {
        assertThat(actual.computedAt()).as("body computedAt").isNotBlank();
        return this;
    }

    public ScoreBodyValidator hasStatus(String status) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        return this;
    }

    public ScoreBodyValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("body correlationId").isEqualTo(correlationId);
        return this;
    }
}
