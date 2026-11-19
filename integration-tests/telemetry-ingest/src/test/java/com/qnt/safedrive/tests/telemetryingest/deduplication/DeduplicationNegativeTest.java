package com.qnt.safedrive.tests.telemetryingest.deduplication;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.samples;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DeduplicationNegativeTest extends AbstractDeduplicationTest {

    private static final int REQUESTS_PER_VARIANT = 5;

    @DataProvider
    public Object[][] changes() {
        return new Object[][] {
            {"different samples", (UnaryOperator<Batch>) b -> b.withSamples(samples(b.samples().size() + 1))},
            {"different trip", (UnaryOperator<Batch>) b -> b.withTripId(Ids.trip())}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "changes",
            description = "The same batch ID with different content is a conflict, and the original event stays the only one")
    @Story("Conflicting batches")
    public void sameBatchIdDifferentContentIsConflict(String change, UnaryOperator<Batch> modify) {
        Batch original = aBatch();
        Batch changed = modify.apply(original);
        String correlationId = Ids.correlation();
        Validator.with(ingestClient().postBatchWithRetry(original, correlationId)).hasStatus(Http.ACCEPTED);
        tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1);

        Validator.with(ingestClient().postBatch(changed, correlationId))
                .hasStatus(Http.CONFLICT)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_CONFLICT)
                .hasBatchId(original.batchId())
                .hasErrors();
        assertPublishedTimes(1, original, correlationId);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Two versions of one batch ID sent at the same moment: one wins and is published once, the other version gets 409")
    @Story("Concurrent conflicts")
    public void concurrentConflictingRequestsPublishOnce() {
        Batch versionA = aBatch();
        Batch versionB = versionA.withSamples(samples(versionA.samples().size() + 1));
        String correlationId = Ids.correlation();
        List<Batch> batches = new ArrayList<>();
        for (int i = 0; i < REQUESTS_PER_VARIANT; i++) {
            batches.add(versionA);
            batches.add(versionB);
        }

        List<Response> responses = postAtTheSameTime(batches, correlationId);

        List<Integer> statuses = responses.stream().map(Response::statusCode).toList();
        assertThat(statuses).as("statuses").containsOnly(Http.ACCEPTED, Http.OK, Http.CONFLICT);
        assertThat(statuses).filteredOn(status -> status == Http.ACCEPTED).as("accepted requests").hasSize(1);
        Batch winner = batches.get(statuses.indexOf(Http.ACCEPTED));
        for (int i = 0; i < responses.size(); i++) {
            int expected = batches.get(i) == winner ? Http.OK : Http.CONFLICT;
            if (statuses.get(i) != Http.ACCEPTED) {
                assertThat(statuses.get(i)).as("status of request %d (same version as winner: %b)", i, batches.get(i) == winner)
                        .isEqualTo(expected);
            }
        }
        tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1);
        assertPublishedTimes(1, versionA, correlationId);
    }
}
