package com.qnt.safedrive.tests.scoring.scorecalculation;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompletedFor;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.events;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.Map;
import org.testng.annotations.Test;

public class ScoreCalculationPositiveTest extends AbstractScoreCalculationTest {

    @Test(groups = Groups.SMOKE, description = "A clean trip gives a perfect score, published as a valid score.updated keyed by driver")
    @Story("Score a trip")
    public void cleanTripScoresHundred() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();

        TopicReader.Message updated = publishAndAwaitScore(trip, correlationId, 1);

        Validator.with(updated)
                .hasTopic(Topics.SCORE_UPDATED)
                .hasKey(trip.driverId())
                .envelope()
                .hasType(EventTypes.SCORE_UPDATED)
                .hasSource(Services.SCORING)
                .hasCorrelationId(correlationId)
                .hasEventId()
                .matchesContract()
                .scoreUpdatedPayload()
                .hasDriverId(trip.driverId())
                .hasScoreVersion(1)
                .hasScore(100.0)
                .hasBasedOnTrips(1)
                .hasComputedAt();
    }

    @Test(groups = Groups.REGRESSION, description = "Events are weighted and measured per 100 km: 2 hard brakes and 1 speeding over 50 km score 86")
    @Story("Score a trip")
    public void eventsAreWeightedPer100Km() {
        TripCompleted trip = aTripCompleted().withDistanceKilometers(50)
                .withEventCounts(Map.of(DrivingEventKinds.HARD_BRAKE, 2, DrivingEventKinds.SPEEDING, 1));

        Validator.with(publishAndAwaitScore(trip, Ids.correlation(), 1)).envelope().scoreUpdatedPayload().hasScore(86.0);
    }

    @Test(groups = Groups.REGRESSION, description = "Each kind has its own weight: one distraction over 100 km costs 4, harsh acceleration 2, a crash 25")
    @Story("Score a trip")
    public void eachKindHasItsOwnWeight() {
        String correlationId = Ids.correlation();
        TripCompleted distraction = aTripCompleted().withEventCounts(events(DrivingEventKinds.DISTRACTION, 1));
        TripCompleted harshAcceleration = aTripCompleted().withEventCounts(events(DrivingEventKinds.HARSH_ACCELERATION, 1));
        TripCompleted crash = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 1));

        Validator.with(publishAndAwaitScore(distraction, correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(96.0);
        Validator.with(publishAndAwaitScore(harshAcceleration, correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(98.0);
        Validator.with(publishAndAwaitScore(crash, correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(75.0);
    }

    @Test(groups = Groups.REGRESSION, description = "The trip score never goes below zero, however many events there are")
    @Story("Score a trip")
    public void tripScoreIsFlooredAtZero() {
        TripCompleted trip = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 10));

        Validator.with(publishAndAwaitScore(trip, Ids.correlation(), 1)).envelope().scoreUpdatedPayload().hasScore(0.0);
    }

    @Test(groups = Groups.REGRESSION, description = "A trip of zero distance is measured over 1 km, so it neither fails nor divides by zero")
    @Story("Score a trip")
    public void zeroDistanceTripIsMeasuredOverOneKilometer() {
        String correlationId = Ids.correlation();
        TripCompleted clean = aTripCompleted().withDistanceKilometers(0);
        TripCompleted braking = aTripCompleted().withDistanceKilometers(0).withEventCounts(events(DrivingEventKinds.HARD_BRAKE, 1));

        Validator.with(publishAndAwaitScore(clean, correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(100.0);
        Validator.with(publishAndAwaitScore(braking, correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(0.0);
    }

    @Test(groups = Groups.REGRESSION, description = "The driver score is the mean of the trip scores, rounded to 2 decimals")
    @Story("Rolling score")
    public void driverScoreIsTheMeanOfTheTripScores() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        // 1 hard brake over 30 km = 6.67 per 100 km, so a trip score of 93.33
        TripCompleted rough = aTripCompletedFor(driverId, 50).withDistanceKilometers(30).withEventCounts(events(DrivingEventKinds.HARD_BRAKE, 1));

        publishAndAwaitScore(aTripCompletedFor(driverId, 60), correlationId, 1);
        publishAndAwaitScore(rough, correlationId, 2);
        TopicReader.Message third = publishAndAwaitScore(
                rough.withTripId(Ids.trip()).withEndedAt(aTripCompletedFor(driverId, 40).endedAt()), correlationId, 3);

        Validator.with(third).envelope().scoreUpdatedPayload().hasScore(95.55).hasBasedOnTrips(3);
    }

    @Test(groups = Groups.REGRESSION, description = "Only the last 10 trips (by end time) count: an old bad trip drops out of the score when the 11th trip arrives")
    @Story("Rolling score")
    public void onlyTheLastTenTripsCount() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        TripCompleted oldCrash = aTripCompletedFor(driverId, 100).withEventCounts(events(DrivingEventKinds.CRASH, 10));
        publishAndAwaitScore(oldCrash, correlationId, 1);
        for (int i = 2; i <= 9; i++) {
            publishAndAwaitScore(aTripCompletedFor(driverId, 100 - i), correlationId, i);
        }

        TopicReader.Message tenth = publishAndAwaitScore(aTripCompletedFor(driverId, 50), correlationId, 10);
        Validator.with(tenth).envelope().scoreUpdatedPayload().hasScore(90.0).hasBasedOnTrips(10);

        TopicReader.Message eleventh = publishAndAwaitScore(aTripCompletedFor(driverId, 40), correlationId, 11);
        Validator.with(eleventh).envelope().scoreUpdatedPayload().hasScore(100.0).hasBasedOnTrips(10);
    }

    @Test(groups = Groups.REGRESSION, description = "Every trip raises scoreVersion by exactly 1, even when trips of one driver arrive back to back")
    @Story("Score version")
    public void scoreVersionRisesByOnePerTrip() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        for (int i = 1; i <= 5; i++) {
            publishTripCompleted(aTripCompletedFor(driverId, 60 - i), correlationId);
        }

        awaitScoreUpdated(driverId, correlationId, 5);

        assertThat(scoreUpdatedFor(driverId, correlationId))
                .extracting(m -> m.envelope().payload().path("scoreVersion").asInt())
                .as("score versions, in order of publication")
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test(groups = Groups.REGRESSION, description = "A score is published even when the value does not change, so every trip is visible downstream")
    @Story("Score version")
    public void unchangedScoreIsStillPublished() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        publishAndAwaitScore(aTripCompletedFor(driverId, 20), correlationId, 1);
        TopicReader.Message second = publishAndAwaitScore(aTripCompletedFor(driverId, 10), correlationId, 2);

        Validator.with(second).envelope().scoreUpdatedPayload().hasScore(100.0).hasScoreVersion(2).hasBasedOnTrips(2);
    }

    @Test(groups = Groups.REGRESSION, description = "Two drivers are scored independently of each other")
    @Story("Rolling score")
    public void driversAreScoredIndependently() {
        String correlationId = Ids.correlation();
        TripCompleted rough = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 1));
        TripCompleted clean = aTripCompleted();

        publishTripCompleted(rough, correlationId);
        publishTripCompleted(clean, correlationId);

        Validator.with(awaitScoreUpdated(rough.driverId(), correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(75.0).hasBasedOnTrips(1);
        Validator.with(awaitScoreUpdated(clean.driverId(), correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(100.0).hasBasedOnTrips(1);
    }

    @Test(groups = Groups.REGRESSION, description = "The correlation ID of trip.completed reaches score.updated and the scoring logs")
    @Story("Correlation ID")
    public void correlationIdReachesScoreUpdatedAndLogs() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();

        publishAndAwaitScore(trip, correlationId, 1);

        assertThat(tracer.awaitLog(Services.SCORING, correlationId))
                .as("scoring log lines")
                .allSatisfy(line -> assertThat(line.json()).contains("\"correlationId\":\"" + correlationId + "\""));
    }
}
