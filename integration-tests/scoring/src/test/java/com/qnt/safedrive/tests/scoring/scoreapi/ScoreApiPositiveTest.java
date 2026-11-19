package com.qnt.safedrive.tests.scoring.scoreapi;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompletedFor;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.events;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScoreApiPositiveTest extends AbstractScoreApiTest {

    @Test(groups = Groups.SMOKE, description = "After a trip the API serves the driver's score, and echoes the correlation ID")
    @Story("Read the score")
    public void apiServesTheScoreAfterATrip() {
        TripCompleted trip = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 1));
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip, correlationId, 1);

        Validator.with(scoringClient().getScoreWithRetry(trip.driverId(), correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .scoreBody()
                .hasDriverId(trip.driverId())
                .hasScore(75.0)
                .hasScoreVersion(1)
                .hasBasedOnTrips(1)
                .hasComputedAt();
    }

    @Test(groups = Groups.REGRESSION, description = "The API always shows the latest score: same version, score and trip count as the last score.updated")
    @Story("Read the score")
    public void apiMatchesTheLatestScoreUpdated() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(aTripCompletedFor(driverId, 30), correlationId, 1);
        publishAndAwaitScore(aTripCompletedFor(driverId, 20).withEventCounts(events(DrivingEventKinds.CRASH, 1)), correlationId, 2);
        publishAndAwaitScore(aTripCompletedFor(driverId, 10), correlationId, 3);

        // (100 + 75 + 100) / 3 = 91.67
        Validator.with(scoringClient().getScoreWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .scoreBody()
                .hasScore(91.67)
                .hasScoreVersion(3)
                .hasBasedOnTrips(3);
    }

    @Test(groups = Groups.REGRESSION, description = "A request without a correlation ID still gets one back in the response header")
    @Story("Correlation ID")
    public void apiCreatesACorrelationIdWhenNoneIsSent() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip, correlationId, 1);

        Validator.with(scoringClient().getScoreWithRetry(trip.driverId(), null)).hasStatus(Http.OK).hasCorrelationHeader();
    }
}
