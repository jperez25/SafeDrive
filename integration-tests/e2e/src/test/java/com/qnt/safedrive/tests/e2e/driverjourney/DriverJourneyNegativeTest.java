package com.qnt.safedrive.tests.e2e.driverjourney;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.testng.annotations.Test;

public class DriverJourneyNegativeTest extends AbstractDriverJourneyTest {

    @Test(groups = Groups.REGRESSION, description = "A batch ingest rejects never enters the pipeline: nothing on trip.raw, and the driver has no premium")
    @Story("Rejected batch")
    public void rejectedBatchStopsAtTheEdge() {
        Batch batch = aBatch().withSamples(List.of());
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatch(batch, correlationId))
                .hasStatus(Http.BAD_REQUEST)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID);

        waiter.staysEmpty("trip.raw event for a rejected batch", NOTHING_MORE_WINDOW,
                () -> tracer.events(Topics.TRIP_RAW, correlationId).isEmpty() ? Optional.empty() : Optional.of(true));
        assertThat(tracer.events(Topics.TRIP_RAW, correlationId)).isEmpty();
        Validator.with(premiumClient().getPremium(batch.driverId(), correlationId))
                .hasStatus(Http.NOT_FOUND)
                .premiumBody()
                .hasStatus(Statuses.PREMIUM_NOT_FOUND);
    }
}
