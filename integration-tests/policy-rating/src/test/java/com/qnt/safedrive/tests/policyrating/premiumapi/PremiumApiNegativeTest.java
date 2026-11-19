package com.qnt.safedrive.tests.policyrating.premiumapi;

import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdated;

import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class PremiumApiNegativeTest extends AbstractPremiumApiTest {

    @Test(groups = Groups.REGRESSION, description = "A driver with no applied score gets 404 with a not_found body and the correlation ID")
    @Story("Unknown driver")
    public void unknownDriverIsNotFound() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(premiumClient().getPremium(driverId, correlationId))
                .hasStatus(Http.NOT_FOUND)
                .hasCorrelationHeader(correlationId)
                .premiumBody()
                .hasStatus(Statuses.PREMIUM_NOT_FOUND)
                .hasDriverId(driverId)
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Rating one driver does not give another driver a premium")
    @Story("Unknown driver")
    public void ratingOfOneDriverIsNotServedForAnother() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(score.driverId(), 1, score.score(), correlationId);

        Validator.with(premiumClient().getPremium(Ids.driver(), correlationId)).hasStatus(Http.NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "A score that was dead-lettered leaves no rating behind: the driver is still not found")
    @Story("Unknown driver")
    public void rejectedScoreLeavesNoRating() {
        ScoreUpdated invalid = aScoreUpdated().withScore(101);
        String correlationId = Ids.correlation();
        publishScoreUpdated(invalid, correlationId);

        assertPremiumAdjustedTimes(0, invalid.driverId(), correlationId);
        Validator.with(premiumClient().getPremium(invalid.driverId(), correlationId)).hasStatus(Http.NOT_FOUND);
    }
}
