package com.qnt.safedrive.tests.e2e.driverjourney;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aSpeedingBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class DriverJourneyPositiveTest extends AbstractDriverJourneyTest {

    @Test(groups = Groups.REGRESSION, description = "A trip with a speeding event ends in the worst band: score 0, +30% on the base premium")
    @Story("Risky trip")
    public void speedingTripEndsInTheHighRiskBand() {
        Batch batch = aSpeedingBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);

        Validator.with(awaitEvent(Topics.TRIP_COMPLETED, correlationId, "tripId", batch.tripId()))
                .envelope()
                .tripCompletedPayload()
                .hasEventCount(DrivingEventKinds.SPEEDING, 1);
        Validator.with(awaitScoreUpdated(batch.driverId(), correlationId, 1))
                .envelope()
                .scoreUpdatedPayload()
                .hasScore(0.0)
                .hasBasedOnTrips(1);
        Validator.with(awaitPremiumAdjusted(batch.driverId(), correlationId, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasAdjustmentPct(30.0)
                .hasNewPremium(156.00)
                .hasBand(Bands.HIGH_RISK);
        Validator.with(premiumClient().getPremiumWithRetry(batch.driverId(), correlationId))
                .hasStatus(Http.OK)
                .premiumBody()
                .hasNewPremium(156.00)
                .hasBand(Bands.HIGH_RISK);
    }

    @Test(groups = Groups.REGRESSION,
            description = "A second trip averages with the first and the premium is rated from the base, not on top of the previous premium")
    @Story("Two trips")
    public void secondTripAveragesTheScoreAndRatesFromTheBase() {
        Batch clean = aBatch();
        Batch speeding = aSpeedingBatch().withDriverId(clean.driverId());
        String cleanCorrelation = Ids.correlation();
        String speedingCorrelation = Ids.correlation();

        ingest(clean, cleanCorrelation);
        Validator.with(awaitPremiumAdjusted(clean.driverId(), cleanCorrelation, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
        ingest(speeding, speedingCorrelation);

        // mean of trip scores 100 and 0 is 50: poor band, +15% of 120.00 (rating 96.00 again would give 110.40)
        Validator.with(awaitScoreUpdated(clean.driverId(), speedingCorrelation, 2))
                .envelope()
                .scoreUpdatedPayload()
                .hasScore(50.0)
                .hasBasedOnTrips(2);
        Validator.with(awaitPremiumAdjusted(clean.driverId(), speedingCorrelation, 2))
                .envelope()
                .premiumAdjustedPayload()
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasAdjustmentPct(15.0)
                .hasNewPremium(138.00)
                .hasBand(Bands.POOR);
        Validator.with(premiumClient().getPremiumWithRetry(clean.driverId(), speedingCorrelation))
                .hasStatus(Http.OK)
                .premiumBody()
                .hasScoreVersion(2)
                .hasNewPremium(138.00)
                .hasBand(Bands.POOR);
    }

    @Test(groups = Groups.REGRESSION, description = "Two drivers sent together each get their own score and premium")
    @Story("Isolation")
    public void driversDoNotAffectEachOther() {
        Batch clean = aBatch();
        Batch speeding = aSpeedingBatch();
        String cleanCorrelation = Ids.correlation();
        String speedingCorrelation = Ids.correlation();

        ingest(clean, cleanCorrelation);
        ingest(speeding, speedingCorrelation);

        Validator.with(awaitPremiumAdjusted(clean.driverId(), cleanCorrelation, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
        Validator.with(awaitPremiumAdjusted(speeding.driverId(), speedingCorrelation, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(156.00)
                .hasBand(Bands.HIGH_RISK);
        Validator.with(premiumClient().getPremiumWithRetry(clean.driverId(), cleanCorrelation)).premiumBody().hasBand(Bands.EXCELLENT);
        Validator.with(premiumClient().getPremiumWithRetry(speeding.driverId(), speedingCorrelation)).premiumBody().hasBand(Bands.HIGH_RISK);
    }
}
