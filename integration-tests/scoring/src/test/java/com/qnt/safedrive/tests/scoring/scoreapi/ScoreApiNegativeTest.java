package com.qnt.safedrive.tests.scoring.scoreapi;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScoreApiNegativeTest extends AbstractScoreApiTest {

    @Test(groups = Groups.REGRESSION, description = "A driver with no scored trip gets 404 with a not_found body and the correlation ID")
    @Story("Unknown driver")
    public void unknownDriverIsNotFound() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(scoringClient().getScore(driverId, correlationId))
                .hasStatus(Http.NOT_FOUND)
                .hasCorrelationHeader(correlationId)
                .scoreBody()
                .hasStatus(Statuses.SCORE_NOT_FOUND)
                .hasDriverId(driverId)
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Scoring one driver does not give another driver a score")
    @Story("Unknown driver")
    public void scoreOfOneDriverIsNotServedForAnother() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip, correlationId, 1);

        Validator.with(scoringClient().getScore(Ids.driver(), correlationId)).hasStatus(Http.NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "A trip that was dead-lettered leaves no score behind: the driver is still not found")
    @Story("Unknown driver")
    public void rejectedTripLeavesNoScore() {
        TripCompleted invalid = aTripCompleted().withEventCounts(null);
        String correlationId = Ids.correlation();
        publishTripCompleted(invalid, correlationId);

        assertScoreUpdatedTimes(0, invalid.driverId(), correlationId);
        Validator.with(scoringClient().getScore(invalid.driverId(), correlationId)).hasStatus(Http.NOT_FOUND);
    }
}
