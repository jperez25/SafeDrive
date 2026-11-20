package com.qnt.safedrive.tests.e2e.pipelineresilience;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class PipelineResilienceNegativeTest extends AbstractPipelineResilienceTest {

    private static final String NOT_JSON = "this is not json {";

    @Test(groups = Groups.REGRESSION,
            description = "A poison event on trip.raw is dead-lettered and does not block the next batch of the same trip, which reaches a premium")
    @Story("Poison event")
    public void poisonEventDoesNotBlockTheChain() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        publisher().send(Topics.TRIP_RAW, batch.tripId(), NOT_JSON); // same key, so same partition as the healthy batch

        ingest(batch, correlationId);

        Validator.with(tracer.awaitRawByKey(Topics.deadLetter(Topics.TRIP_RAW, Services.TRIP_PROCESSOR), batch.tripId(), 1).getFirst())
                .hasKey(batch.tripId())
                .hasValue(NOT_JSON);
        Validator.with(awaitPremiumAdjusted(batch.driverId(), correlationId, 1))
                .envelope()
                .premiumAdjustedPayload()
                .hasNewPremium(96.00)
                .hasBand(Bands.EXCELLENT);
        assertPipelineEventCounts(batch, correlationId, 1, 1, 1, 1);
    }
}
