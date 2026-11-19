package com.qnt.safedrive.tests.policyrating.premiumapi;

import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class PremiumApiPositiveTest extends AbstractPremiumApiTest {

    @Test(groups = Groups.SMOKE, description = "After a score the API serves the driver's rating, and echoes the correlation ID")
    @Story("Read the premium")
    public void apiServesTheRatingAfterAScore() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId);

        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .premiumBody()
                .hasDriverId(driverId)
                .hasScoreVersion(1)
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasAdjustmentPct(-20.0)
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT)
                .hasUpdatedAt();
    }

    @Test(groups = Groups.REGRESSION, description = "The API always shows the latest rating: same version, band and premium as the last premium.adjusted")
    @Story("Read the premium")
    public void apiMatchesTheLatestPremiumAdjusted() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId);
        publishAndAwaitPremium(driverId, 2, 80.0, correlationId);
        publishAndAwaitPremium(driverId, 3, 50.0, correlationId);

        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .premiumBody()
                .hasScoreVersion(3)
                .hasBand(Bands.POOR)
                .hasAdjustmentPct(15.0)
                .hasNewPremium(138.00);
    }

    @Test(groups = Groups.REGRESSION, description = "A request without a correlation ID still gets one back in the response header")
    @Story("Correlation ID")
    public void apiCreatesACorrelationIdWhenNoneIsSent() {
        String driverId = Ids.driver();
        publishAndAwaitPremium(driverId, 1, 95.0, Ids.correlation());

        Validator.with(premiumClient().getPremiumWithRetry(driverId, null)).hasStatus(Http.OK).hasCorrelationHeader();
    }
}
