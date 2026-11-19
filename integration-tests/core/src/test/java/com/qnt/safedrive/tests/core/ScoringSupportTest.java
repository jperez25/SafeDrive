package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.factory.TripCompletedFactory;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
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

/** The helpers added for the scoring tests: they accept the right thing and can fail. */
public class ScoringSupportTest {

    private static final Path EXAMPLES = Path.of("..", "..", "docs", "contracts", "events", "examples");

    @Test
    public void tripCompletedEnvelopeIsValidAndGetsAFreshEventIdEachTime() {
        TripCompleted trip = TripCompletedFactory.aTripCompleted();
        Envelope first = EnvelopeFactory.aTripCompletedEvent(trip, "corr-1");
        Envelope second = EnvelopeFactory.aTripCompletedEvent(trip, "corr-1");

        Validator.with(new TopicReader.Message("trip.completed", trip.driverId(), first))
                .envelope().hasCorrelationId("corr-1").hasEventId().matchesContract()
                .tripCompletedPayload().hasTripId(trip.tripId()).hasDriverId(trip.driverId()).hasRevision(1).hasNoDrivingEvents();
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    public void tripsAreUniqueAndKeepTheContractFieldNames() {
        assertThat(TripCompletedFactory.aTripCompleted().tripId()).isNotEqualTo(TripCompletedFactory.aTripCompleted().tripId());
        assertThat(Json.tree(Json.write(TripCompletedFactory.aTripCompleted())).propertyNames())
                .contains("distanceKm", "durationS", "avgSpeedKmh", "maxSpeedKmh", "eventCounts");
    }

    @Test
    public void eventsSetsOneKindAndKeepsTheOthersAtZero() {
        assertThat(TripCompletedFactory.events(DrivingEventKinds.CRASH, 2))
                .containsEntry(DrivingEventKinds.CRASH, 2)
                .containsEntry(DrivingEventKinds.HARD_BRAKE, 0);
    }

    @Test
    public void laterTripsEndLater() {
        var earlier = TripCompletedFactory.aTripCompletedFor("d-1", 30);
        var later = TripCompletedFactory.aTripCompletedFor("d-1", 10);
        assertThat(java.time.Instant.parse(later.endedAt())).isAfter(java.time.Instant.parse(earlier.endedAt()));
    }

    @Test
    public void scoreUpdatedValidatorChecksItsFields() throws Exception {
        Envelope event = Json.read(Files.readString(EXAMPLES.resolve("score.updated.v1.json")), Envelope.class);
        TopicReader.Message message = new TopicReader.Message("score.updated", "d-1", event);

        Validator.with(message).envelope().matchesContract().scoreUpdatedPayload()
                .hasDriverId("d-1").hasScoreVersion(3).hasScore(82.5).hasBasedOnTrips(12).hasComputedAt();
        assertThatThrownBy(() -> Validator.with(message).envelope().scoreUpdatedPayload().hasScore(50.0))
                .isInstanceOf(AssertionError.class).hasMessageContaining("score");
        assertThatThrownBy(() -> Validator.with(message).envelope().scoreUpdatedPayload().hasScoreVersion(9))
                .isInstanceOf(AssertionError.class).hasMessageContaining("scoreVersion");
    }

    @Test
    public void scoreBodyValidatorChecksTheScoreAndTheNotFoundAnswer() {
        Response ok = response(Http.OK,
                "{\"driverId\":\"d-1\",\"score\":91.67,\"scoreVersion\":3,\"basedOnTrips\":3,\"computedAt\":\"2026-11-19T10:00:00Z\"}");
        Validator.with(ok).hasStatus(Http.OK).scoreBody()
                .hasDriverId("d-1").hasScore(91.67).hasScoreVersion(3).hasBasedOnTrips(3).hasComputedAt();
        assertThatThrownBy(() -> Validator.with(ok).scoreBody().hasScore(50.0))
                .isInstanceOf(AssertionError.class).hasMessageContaining("score");
        assertThatThrownBy(() -> Validator.with(ok).scoreBody().hasScoreVersion(1))
                .isInstanceOf(AssertionError.class).hasMessageContaining("scoreVersion");

        Response missing = response(Http.NOT_FOUND, "{\"status\":\"not_found\",\"driverId\":\"d-9\",\"correlationId\":\"c-1\"}");
        Validator.with(missing).hasStatus(Http.NOT_FOUND).scoreBody()
                .hasStatus(Statuses.SCORE_NOT_FOUND).hasDriverId("d-9").hasCorrelationId("c-1");
        assertThatThrownBy(() -> Validator.with(missing).scoreBody().hasScore(1.0))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void driverScorePathContainsTheDriver() {
        assertThat(Paths.driverScore("d-1")).isEqualTo("/v1/drivers/d-1/score");
    }

    private static Response response(int status, String body) {
        return new ResponseBuilder()
                .setStatusCode(status)
                .setBody(body)
                .build();
    }
}
