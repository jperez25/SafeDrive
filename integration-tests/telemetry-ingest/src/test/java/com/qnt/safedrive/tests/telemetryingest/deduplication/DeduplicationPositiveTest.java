package com.qnt.safedrive.tests.telemetryingest.deduplication;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aSample;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.samples;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.annotations.Test;

public class DeduplicationPositiveTest extends AbstractDeduplicationTest {

    private static final int CONCURRENT_REQUESTS = 10;

    @Test(groups = Groups.REGRESSION,
            description = "A duplicate answers with the correlation ID of the first request in the body, and echoes the caller's own in the header")
    @Story("Duplicate batches")
    public void duplicateKeepsTheOriginalCorrelationId() {
        Batch batch = aBatch();
        String firstCorrelationId = Ids.correlation();
        String secondCorrelationId = Ids.correlation();

        Validator.with(ingestClient().postBatchWithRetry(batch, firstCorrelationId)).hasStatus(Http.ACCEPTED);

        Validator.with(ingestClient().postBatch(batch, secondCorrelationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(secondCorrelationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_DUPLICATE)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(firstCorrelationId);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Identical requests sent at the same moment give one acceptance, the rest duplicates, and one published event")
    @Story("Concurrent duplicates")
    public void concurrentIdenticalRequestsPublishOnce() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        List<Response> responses =
                postAtTheSameTime(Collections.nCopies(CONCURRENT_REQUESTS, batch), correlationId);

        assertThat(responses).extracting(Response::statusCode)
                .as("status of each of the %d requests", CONCURRENT_REQUESTS)
                .containsOnly(Http.ACCEPTED, Http.OK)
                .filteredOn(status -> status == Http.ACCEPTED)
                .as("accepted requests")
                .hasSize(1);
        tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1);
        assertPublishedTimes(1, batch, correlationId);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Repeating a batch that had rejected samples is still a duplicate, not a conflict")
    @Story("Duplicate batches")
    public void duplicateOfBatchWithRejectedSamplesIsStillDuplicate() {
        Batch batch = aBatchWithOneBadSample();
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatchWithRetry(batch, correlationId))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasRejectedSampleCount(1);

        Validator.with(ingestClient().postBatch(batch, correlationId))
                .hasStatus(Http.OK)
                .ingestBody()
                .hasStatus(Statuses.INGEST_DUPLICATE)
                .hasBatchId(batch.batchId());
    }

    private static Batch aBatchWithOneBadSample() {
        List<Sample> all = new ArrayList<>(samples(4));
        all.add(aSample().withLatitude(91.0));
        return aBatch().withSamples(all);
    }
}
