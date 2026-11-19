package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/** The helpers added for the policy-rating tests: they accept the right thing and can fail. */
public class RatingSupportTest {

    private static final Path EXAMPLES = Path.of("..", "..", "docs", "contracts", "events", "examples");

    @Test
    public void scoreUpdatedEnvelopeIsValidAndGetsAFreshEventIdEachTime() {
        ScoreUpdated score = ScoreUpdatedFactory.aScoreUpdated();
        Envelope first = EnvelopeFactory.aScoreUpdatedEvent(score, "corr-1");
        Envelope second = EnvelopeFactory.aScoreUpdatedEvent(score, "corr-1");

        Validator.with(new TopicReader.Message("score.updated", score.driverId(), first))
                .envelope().hasCorrelationId("corr-1").hasEventId().matchesContract()
                .scoreUpdatedPayload().hasDriverId(score.driverId()).hasScoreVersion(1).hasScore(95.0);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(ScoreUpdatedFactory.aScoreUpdated().driverId()).isNotEqualTo(score.driverId());
    }

    @Test
    public void premiumAdjustedValidatorChecksItsFields() throws Exception {
        Envelope event = Json.read(Files.readString(EXAMPLES.resolve("premium.adjusted.v1.json")), Envelope.class);
        TopicReader.Message message = new TopicReader.Message("premium.adjusted", "d-1", event);

        Validator.with(message).envelope().matchesContract().premiumAdjustedPayload()
                .hasDriverId("d-1").hasScoreVersion(3).hasBasePremium(120.0).hasAdjustmentPct(-8.5)
                .hasNewPremium(109.8).hasBand(Bands.GOOD);
        assertThatThrownBy(() -> Validator.with(message).envelope().premiumAdjustedPayload().hasBand(Bands.POOR))
                .isInstanceOf(AssertionError.class).hasMessageContaining("band");
        assertThatThrownBy(() -> Validator.with(message).envelope().premiumAdjustedPayload().hasNewPremium(1.0))
                .isInstanceOf(AssertionError.class).hasMessageContaining("newPremium");
    }

    @Test
    public void premiumBodyValidatorChecksTheRatingAndTheNotFoundAnswer() {
        Response ok = response(Http.OK, "{\"driverId\":\"d-1\",\"scoreVersion\":2,\"basePremium\":120.00,"
                + "\"adjustmentPct\":-20.0,\"newPremium\":96.00,\"band\":\"excellent\",\"updatedAt\":\"2026-11-19T10:00:00Z\"}");
        Validator.with(ok).hasStatus(Http.OK).premiumBody()
                .hasDriverId("d-1").hasScoreVersion(2).hasBasePremium(120.0).hasAdjustmentPct(-20.0)
                .hasNewPremium(96.0).hasBand(Bands.EXCELLENT).hasUpdatedAt();
        assertThatThrownBy(() -> Validator.with(ok).premiumBody().hasNewPremium(100.0))
                .isInstanceOf(AssertionError.class).hasMessageContaining("newPremium");
        assertThatThrownBy(() -> Validator.with(ok).premiumBody().hasBand(Bands.GOOD))
                .isInstanceOf(AssertionError.class).hasMessageContaining("band");

        Response missing = response(Http.NOT_FOUND, "{\"status\":\"not_found\",\"driverId\":\"d-9\",\"correlationId\":\"c-1\"}");
        Validator.with(missing).hasStatus(Http.NOT_FOUND).premiumBody()
                .hasStatus(Statuses.PREMIUM_NOT_FOUND).hasDriverId("d-9").hasCorrelationId("c-1");
        assertThatThrownBy(() -> Validator.with(missing).premiumBody().hasBand(Bands.GOOD))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void driverPremiumPathContainsTheDriver() {
        assertThat(Paths.driverPremium("d-1")).isEqualTo("/v1/drivers/d-1/premium");
    }

    private static Response response(int status, String body) {
        return new ResponseBuilder().setStatusCode(status).setBody(body).build();
    }
}
