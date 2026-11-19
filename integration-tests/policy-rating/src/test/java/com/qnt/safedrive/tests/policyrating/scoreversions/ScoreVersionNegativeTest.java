package com.qnt.safedrive.tests.policyrating.scoreversions;

import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdatedFor;

import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScoreVersionNegativeTest extends AbstractScoreVersionTest {

    @Test(groups = Groups.REGRESSION, description = "A stale score (lower version, arrives late) changes nothing and publishes nothing")
    @Story("Stale scores")
    public void staleScoreIsIgnored() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 2, 95.0, correlationId);

        publishScoreUpdated(aScoreUpdatedFor(driverId, 1, 10.0), correlationId);

        assertPremiumAdjustedTimes(1, driverId, correlationId);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(2)
                .hasBand(Bands.EXCELLENT)
                .hasNewPremium(96.00);
    }

    @Test(groups = Groups.REGRESSION, description = "A different event carrying the same score version is ignored: one version is applied once")
    @Story("Stale scores")
    public void sameVersionUnderANewEventIdIsIgnored() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId);

        publishScoreUpdated(aScoreUpdatedFor(driverId, 1, 10.0), correlationId);

        assertPremiumAdjustedTimes(1, driverId, correlationId);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(1)
                .hasBand(Bands.EXCELLENT);
    }

    @Test(groups = Groups.REGRESSION, description = "Out of order: version 2 arrives before version 1, so version 1 is dropped and the premium stays on version 2")
    @Story("Out of order")
    public void versionsArrivingOutOfOrderEndOnTheHighest() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 3, 80.0, correlationId);
        publishScoreUpdated(aScoreUpdatedFor(driverId, 1, 20.0), correlationId);
        publishScoreUpdated(aScoreUpdatedFor(driverId, 2, 50.0), correlationId);

        assertPremiumAdjustedTimes(1, driverId, correlationId);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(3)
                .hasBand(Bands.GOOD)
                .hasNewPremium(108.00);
    }
}
