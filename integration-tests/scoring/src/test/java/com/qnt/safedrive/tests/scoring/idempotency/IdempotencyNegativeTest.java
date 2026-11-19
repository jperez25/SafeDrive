package com.qnt.safedrive.tests.scoring.idempotency;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompletedFor;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class IdempotencyNegativeTest extends AbstractIdempotencyTest {

    @Test(groups = Groups.REGRESSION, description = "The same event delivered twice (same event ID) gives one score.updated")
    @Story("Duplicates")
    public void sameEventDeliveredTwiceIsProcessedOnce() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        Envelope event = publishTripCompleted(trip, correlationId);
        awaitScoreUpdated(trip.driverId(), correlationId, 1);

        publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), event);

        assertScoreUpdatedTimes(1, trip.driverId(), correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A duplicate that arrives after later trips changes neither the version nor the score")
    @Story("Duplicates")
    public void lateDuplicateDoesNotChangeTheScore() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        TripCompleted first = aTripCompletedFor(driverId, 30);
        Envelope event = publishTripCompleted(first, correlationId);
        awaitScoreUpdated(driverId, correlationId, 1);
        publishAndAwaitScore(aTripCompletedFor(driverId, 20), correlationId, 2);

        publisher().send(Topics.TRIP_COMPLETED, driverId, event);

        assertScoreUpdatedTimes(2, driverId, correlationId);
        Validator.with(scoringClient().getScoreWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .scoreBody()
                .hasScoreVersion(2)
                .hasBasedOnTrips(2);
    }

    @Test(groups = Groups.REGRESSION, description = "The same trip delivered again under a new event ID is not counted twice")
    @Story("Duplicates")
    public void sameTripUnderANewEventIdIsNotCountedTwice() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip, correlationId, 1);

        publishTripCompleted(trip, correlationId);

        assertScoreUpdatedTimes(1, trip.driverId(), correlationId);
        Validator.with(scoringClient().getScoreWithRetry(trip.driverId(), correlationId))
                .scoreBody()
                .hasBasedOnTrips(1);
    }
}
