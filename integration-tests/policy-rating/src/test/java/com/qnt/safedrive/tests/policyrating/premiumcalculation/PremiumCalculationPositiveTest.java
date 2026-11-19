package com.qnt.safedrive.tests.policyrating.premiumcalculation;

import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PremiumCalculationPositiveTest extends AbstractPremiumCalculationTest {

    @DataProvider
    public Object[][] bands() {
        return new Object[][] {
            {"top score", 100.0, Bands.EXCELLENT, -20.0, 96.00},
            {"excellent lower bound", 90.0, Bands.EXCELLENT, -20.0, 96.00},
            {"just under excellent", 89.99, Bands.GOOD, -10.0, 108.00},
            {"good lower bound", 75.0, Bands.GOOD, -10.0, 108.00},
            {"just under good", 74.99, Bands.AVERAGE, 0.0, 120.00},
            {"average lower bound", 60.0, Bands.AVERAGE, 0.0, 120.00},
            {"just under average", 59.99, Bands.POOR, 15.0, 138.00},
            {"poor lower bound", 40.0, Bands.POOR, 15.0, 138.00},
            {"just under poor", 39.99, Bands.HIGH_RISK, 30.0, 156.00},
            {"lowest score", 0.0, Bands.HIGH_RISK, 30.0, 156.00}
        };
    }

    @Test(dataProvider = "bands", groups = Groups.REGRESSION,
            description = "The score band decides the adjustment; lower bounds are inclusive; the event and the API agree")
    @Story("Bands")
    public void scoreBandDecidesThePremium(String boundary, double score, String band, double adjustmentPct, double newPremium) {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        TopicReader.Message adjusted = publishAndAwaitPremium(driverId, 1, score, correlationId);

        Validator.with(adjusted)
                .hasKey(driverId)
                .envelope()
                .premiumAdjustedPayload()
                .hasDriverId(driverId)
                .hasScoreVersion(1)
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasAdjustmentPct(adjustmentPct)
                .hasNewPremium(newPremium)
                .hasBand(band);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .premiumBody()
                .hasBand(band)
                .hasAdjustmentPct(adjustmentPct)
                .hasNewPremium(newPremium);
    }

    @Test(groups = Groups.REGRESSION, description = "premium.adjusted is valid against its contract, keeps the correlation ID and names policy-rating as source")
    @Story("Event")
    public void publishedEventMatchesTheContract() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(publishAndAwaitPremium(driverId, 1, 95.0, correlationId))
                .hasTopic("premium.adjusted")
                .hasKey(driverId)
                .envelope()
                .hasType("premium.adjusted")
                .hasSource("policy-rating")
                .hasEventId()
                .hasCorrelationId(correlationId)
                .matchesContract();
    }

    @Test(groups = Groups.REGRESSION, description = "A new score is applied to the base premium, not on top of the previous premium (no compounding)")
    @Story("Base premium")
    public void adjustmentIsAlwaysOnTheBasePremium() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId); // 96.00
        TopicReader.Message second = publishAndAwaitPremium(driverId, 2, 30.0, correlationId);

        // 120 x 1.30 = 156.00; on top of the previous premium it would be 124.80
        Validator.with(second).envelope().premiumAdjustedPayload().hasBasePremium(120.0).hasNewPremium(156.00).hasBand(Bands.HIGH_RISK);
    }

    @Test(groups = Groups.REGRESSION, description = "A driver can go back to a better band: a surcharge is not permanent")
    @Story("Base premium")
    public void driverCanRecoverToABetterBand() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 30.0, correlationId);
        publishAndAwaitPremium(driverId, 2, 92.0, correlationId);

        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(2)
                .hasBand(Bands.EXCELLENT)
                .hasNewPremium(96.00);
    }
}
