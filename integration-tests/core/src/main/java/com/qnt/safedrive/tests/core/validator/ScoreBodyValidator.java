package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.ScoreResponse;
import io.qameta.allure.Step;
import org.assertj.core.data.Offset;

/** The body of the score API: the score (200) or the not-found answer (404). */
public class ScoreBodyValidator {

    private final ScoreResponse actual;

    ScoreBodyValidator(ScoreResponse actual) {
        this.actual = actual;
    }

    @Step("Has driver id {driverId}")
    public ScoreBodyValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        return this;
    }

    @Step("Has score {score}")
    public ScoreBodyValidator hasScore(double score) {
        assertThat(actual.score()).as("body score").isNotNull().isCloseTo(score, Offset.offset(0.005));
        return this;
    }

    @Step("Has score version {version}")
    public ScoreBodyValidator hasScoreVersion(int version) {
        assertThat(actual.scoreVersion()).as("body scoreVersion").isEqualTo(version);
        return this;
    }

    @Step("Has based on trips {trips}")
    public ScoreBodyValidator hasBasedOnTrips(int trips) {
        assertThat(actual.basedOnTrips()).as("body basedOnTrips").isEqualTo(trips);
        return this;
    }

    @Step("Has computed at")
    public ScoreBodyValidator hasComputedAt() {
        assertThat(actual.computedAt()).as("body computedAt").isNotBlank();
        return this;
    }

    @Step("Has status {status}")
    public ScoreBodyValidator hasStatus(String status) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        return this;
    }

    @Step("Has correlation id {correlationId}")
    public ScoreBodyValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("body correlationId").isEqualTo(correlationId);
        return this;
    }
}
