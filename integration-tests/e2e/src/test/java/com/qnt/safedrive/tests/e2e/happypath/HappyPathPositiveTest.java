package com.qnt.safedrive.tests.e2e.happypath;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class HappyPathPositiveTest extends AbstractHappyPathTest {

    @Test(groups = Groups.SMOKE,
            description = "A clean trip posted to ingest becomes an excellent score and a discounted premium, with one correlation ID on every hop")
    @Story("Ingest to premium")
    public void cleanTripEndsInADiscountedPremium() {
        Batch batch = aBatch(); // one complete batch, steady speed, no driving events
        String driverId = batch.driverId();
        String tripId = batch.tripId();
        String correlationId = Ids.correlation();

        // 1. telemetry-ingest: the edge accepts the batch
        Validator.with(ingestClient().postBatchWithRetry(batch, correlationId))
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(correlationId);

        // 2. trip.raw: ingest published the batch
        TopicReader.Message tripRaw = awaitEvent(Topics.TRIP_RAW, correlationId, "tripId", tripId);
        Validator.with(tripRaw)
                .hasTopic(Topics.TRIP_RAW)
                .hasKey(tripId)
                .envelope()
                .hasType(EventTypes.TRIP_RAW)
                .hasSource(Services.TELEMETRY_INGEST)
                .hasCorrelationId(correlationId)
                .matchesContract();

        // 3. trip.completed: trip-processor assembled the trip; a clean drive has no driving events
        TopicReader.Message tripCompleted = awaitEvent(Topics.TRIP_COMPLETED, correlationId, "tripId", tripId);
        Validator.with(tripCompleted)
                .hasTopic(Topics.TRIP_COMPLETED)
                .hasKey(driverId)
                .envelope()
                .hasType(EventTypes.TRIP_COMPLETED)
                .hasSource(Services.TRIP_PROCESSOR)
                .hasCorrelationId(correlationId)
                .matchesContract()
                .tripCompletedPayload()
                .hasTripId(tripId)
                .hasDriverId(driverId)
                .hasRevision(1)
                .hasNoDrivingEvents();

        // 4. score.updated: scoring gives a clean trip the top score
        TopicReader.Message scoreUpdated = awaitEvent(Topics.SCORE_UPDATED, correlationId, "driverId", driverId);
        Validator.with(scoreUpdated)
                .hasTopic(Topics.SCORE_UPDATED)
                .hasKey(driverId)
                .envelope()
                .hasType(EventTypes.SCORE_UPDATED)
                .hasSource(Services.SCORING)
                .hasCorrelationId(correlationId)
                .matchesContract()
                .scoreUpdatedPayload()
                .hasDriverId(driverId)
                .hasScoreVersion(1)
                .hasScore(100.0)
                .hasBasedOnTrips(1);

        // 5. premium.adjusted: policy-rating applies the excellent band (-20%) to the 120.00 base premium
        TopicReader.Message premiumAdjusted = awaitEvent(Topics.PREMIUM_ADJUSTED, correlationId, "driverId", driverId);
        Validator.with(premiumAdjusted)
                .hasTopic(Topics.PREMIUM_ADJUSTED)
                .hasKey(driverId)
                .envelope()
                .hasType(EventTypes.PREMIUM_ADJUSTED)
                .hasSource(Services.POLICY_RATING)
                .hasCorrelationId(correlationId)
                .matchesContract()
                .premiumAdjustedPayload()
                .hasDriverId(driverId)
                .hasScoreVersion(1)
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasAdjustmentPct(-20.0)
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);

        // 6. the read APIs serve the same result the events carried
        Validator.with(scoringClient().getScoreWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .scoreBody()
                .hasDriverId(driverId)
                .hasScore(100.0)
                .hasScoreVersion(1)
                .hasBasedOnTrips(1);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .premiumBody()
                .hasDriverId(driverId)
                .hasScoreVersion(1)
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
    }
}
