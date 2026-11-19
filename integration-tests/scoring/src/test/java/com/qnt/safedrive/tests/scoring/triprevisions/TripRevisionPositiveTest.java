package com.qnt.safedrive.tests.scoring.triprevisions;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompletedFor;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.events;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TripRevisionPositiveTest extends AbstractTripRevisionTest {

    @Test(groups = Groups.REGRESSION, description = "A newer revision replaces the trip: the score is recomputed and the trip is not counted twice")
    @Story("Late data")
    public void newerRevisionReplacesTheTrip() {
        TripCompleted first = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(first, correlationId, 1);

        // 5 hard brakes over 100 km = 10 per 100 km
        TripCompleted revised = first.withRevision(2).withEventCounts(events(DrivingEventKinds.HARD_BRAKE, 5));
        var updated = publishAndAwaitScore(revised, correlationId, 2);

        Validator.with(updated)
                .hasKey(first.driverId())
                .envelope()
                .matchesContract()
                .scoreUpdatedPayload()
                .hasScoreVersion(2)
                .hasScore(90.0)
                .hasBasedOnTrips(1);
    }

    @Test(groups = Groups.REGRESSION, description = "A revision only replaces its own trip: the driver's other trips keep their scores")
    @Story("Late data")
    public void revisionLeavesOtherTripsAlone() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        TripCompleted other = aTripCompletedFor(driverId, 60);
        TripCompleted trip = aTripCompletedFor(driverId, 50);
        publishAndAwaitScore(other, correlationId, 1);
        publishAndAwaitScore(trip, correlationId, 2);

        // a crash over 100 km makes this trip 75; the other trip stays 100
        var updated = publishAndAwaitScore(trip.withRevision(2).withEventCounts(events(DrivingEventKinds.CRASH, 1)), correlationId, 3);

        Validator.with(updated).envelope().scoreUpdatedPayload().hasScore(87.5).hasBasedOnTrips(2);
    }

    @Test(groups = Groups.REGRESSION, description = "A revision that makes the trip cleaner raises the score again")
    @Story("Late data")
    public void revisionCanRaiseTheScore() {
        TripCompleted rough = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 1));
        String correlationId = Ids.correlation();
        publishAndAwaitScore(rough, correlationId, 1);

        var updated = publishAndAwaitScore(rough.withRevision(2).withEventCounts(events(DrivingEventKinds.CRASH, 0)), correlationId, 2);

        Validator.with(updated).envelope().scoreUpdatedPayload().hasScore(100.0).hasBasedOnTrips(1);
    }

    @Test(groups = Groups.REGRESSION, description = "A revision that skips a number (1 then 3) is still newer and is applied")
    @Story("Late data")
    public void revisionThatSkipsANumberIsApplied() {
        TripCompleted first = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(first, correlationId, 1);

        var updated = publishAndAwaitScore(first.withRevision(3).withEventCounts(events(DrivingEventKinds.CRASH, 1)), correlationId, 2);

        Validator.with(updated).envelope().scoreUpdatedPayload().hasScore(75.0).hasBasedOnTrips(1);
    }
}
