package com.qnt.safedrive.tests.policyrating.premiumcalculation;

import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdated;

import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class PremiumCalculationNegativeTest extends AbstractPremiumCalculationTest {

    @Test(groups = Groups.REGRESSION, description = "Scores of two drivers are rated separately: one driver's band does not leak to the other")
    @Story("Isolation")
    public void driversAreRatedIndependently() {
        String careful = Ids.driver();
        String risky = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(careful, 1, 98.0, correlationId);
        publishAndAwaitPremium(risky, 1, 20.0, correlationId);

        Validator.with(premiumClient().getPremiumWithRetry(careful, correlationId)).premiumBody().hasBand(Bands.EXCELLENT).hasNewPremium(96.00);
        Validator.with(premiumClient().getPremiumWithRetry(risky, correlationId)).premiumBody().hasBand(Bands.HIGH_RISK).hasNewPremium(156.00);
    }

    @Test(groups = Groups.REGRESSION, description = "A rejected score does not disturb the rating a driver already has")
    @Story("Isolation")
    public void rejectedScoreKeepsTheCurrentRating() {
        ScoreUpdated first = aScoreUpdated();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(first.driverId(), 1, 95.0, correlationId);

        publishScoreUpdated(first.withScoreVersion(2).withScore(-5), correlationId);

        assertPremiumAdjustedTimes(1, first.driverId(), correlationId);
        Validator.with(premiumClient().getPremiumWithRetry(first.driverId(), correlationId))
                .hasStatus(Http.OK)
                .premiumBody()
                .hasScoreVersion(1)
                .hasBand(Bands.EXCELLENT);
    }
}
