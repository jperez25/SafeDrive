package com.qnt.safedrive.tests.e2e.pipelineresilience;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aSpeedingBatchAfter;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.nextOf;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class PipelineResiliencePositiveTest extends AbstractPipelineResilienceTest {

    @Test(groups = Groups.REGRESSION, description = "A batch sent twice produces one trip, one score and one premium")
    @Story("Duplicate batch")
    public void duplicateBatchHasNoSecondEffect() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);
        Validator.with(ingestClient().postBatch(batch, correlationId))
                .hasStatus(Http.OK)
                .ingestBody()
                .hasStatus(Statuses.INGEST_DUPLICATE);

        Validator.with(awaitPremiumAdjusted(batch.driverId(), correlationId, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00);
        assertPipelineEventCounts(batch, correlationId, 1, 1, 1, 1);
    }

    @Test(groups = Groups.REGRESSION, description = "Batches that arrive out of order still give one trip, one score and one premium")
    @Story("Out of order")
    public void outOfOrderBatchesGiveOneResult() {
        Batch first = aBatch().withLast(false);
        Batch second = nextOf(first);
        String correlationId = Ids.correlation();

        ingest(second, correlationId); // the last batch arrives first
        ingest(first, correlationId);

        Validator.with(awaitEvent(Topics.TRIP_COMPLETED, correlationId, "tripId", first.tripId()))
                .envelope()
                .tripCompletedPayload()
                .hasRevision(1)
                .hasStartedAt(first.samples().getFirst().timestamp())
                .hasEndedAt(second.samples().getLast().timestamp());
        Validator.with(awaitPremiumAdjusted(first.driverId(), correlationId, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
        assertPipelineEventCounts(first, correlationId, 2, 1, 1, 1);
    }

    @Test(groups = Groups.REGRESSION,
            description = "A late batch with a speeding event after the trip closed revises the trip, recomputes the score and moves the premium")
    @Story("Late data")
    public void lateBatchRecomputesScoreAndPremium() {
        Batch first = aBatch();
        Batch late = aSpeedingBatchAfter(first);
        String correlationId = Ids.correlation();

        ingest(first, correlationId);
        Validator.with(awaitPremiumAdjusted(first.driverId(), correlationId, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
        ingest(late, correlationId);

        Validator.with(awaitEvent(Topics.TRIP_COMPLETED, correlationId, "tripId", first.tripId())) // first revision is already there
                .envelope()
                .tripCompletedPayload()
                .hasTripId(first.tripId());
        Validator.with(awaitScoreUpdated(first.driverId(), correlationId, 2))
                .envelope()
                .scoreUpdatedPayload()
                .hasScore(0.0)
                .hasBasedOnTrips(1); // the revision replaces the trip, it does not count as a second trip
        Validator.with(awaitPremiumAdjusted(first.driverId(), correlationId, 2))
                .envelope()
                .premiumAdjustedPayload()
                .hasBasePremium(Bands.DEFAULT_BASE_PREMIUM)
                .hasNewPremium(156.00)
                .hasBand(Bands.HIGH_RISK);
        Validator.with(premiumClient().getPremiumWithRetry(first.driverId(), correlationId))
                .premiumBody()
                .hasScoreVersion(2)
                .hasNewPremium(156.00);
        assertPipelineEventCounts(first, correlationId, 2, 2, 2, 2);
    }
}
