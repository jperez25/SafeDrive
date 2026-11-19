package com.qnt.safedrive.tests.telemetryingest.batch;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.samples;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.IngestLimits;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.node.ObjectNode;

public class BatchIngestNegativeTest extends AbstractBatchIngestTest {

    @DataProvider
    public Object[][] missingFields() {
        return new Object[][] {
            {"batchId", (UnaryOperator<Batch>) b -> b.withBatchId(null)},
            {"tripId", (UnaryOperator<Batch>) b -> b.withTripId(null)},
            {"driverId", (UnaryOperator<Batch>) b -> b.withDriverId(null)},
            {"deviceId", (UnaryOperator<Batch>) b -> b.withDeviceId(null)},
            {"batchSeq", (UnaryOperator<Batch>) b -> b.withBatchSequence(null)},
            {"last", (UnaryOperator<Batch>) b -> b.withLast(null)},
            {"samples", (UnaryOperator<Batch>) b -> b.withSamples(null)}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "missingFields",
            description = "A batch without a required field is rejected with 400 and the error names the field")
    @Story("Required fields")
    public void missingRequiredFieldIsRejected(String field, UnaryOperator<Batch> remove) {
        Response response = ingestClient().postBatch(remove.apply(aBatch()), Ids.correlation());

        Validator.with(response)
                .hasStatus(Http.BAD_REQUEST)
                .hasCorrelationHeader()
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasCorrelationId()
                .hasErrorContaining(field);
    }

    @DataProvider
    public Object[][] blankIds() {
        return new Object[][] {
            {"batchId", "", (UnaryOperator<Batch>) b -> b.withBatchId("")},
            {"batchId", "spaces", (UnaryOperator<Batch>) b -> b.withBatchId("   ")},
            {"tripId", "", (UnaryOperator<Batch>) b -> b.withTripId("")},
            {"tripId", "spaces", (UnaryOperator<Batch>) b -> b.withTripId("   ")},
            {"driverId", "", (UnaryOperator<Batch>) b -> b.withDriverId("")},
            {"driverId", "spaces", (UnaryOperator<Batch>) b -> b.withDriverId("   ")},
            {"deviceId", "", (UnaryOperator<Batch>) b -> b.withDeviceId("")},
            {"deviceId", "spaces", (UnaryOperator<Batch>) b -> b.withDeviceId("   ")}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "blankIds",
            description = "An empty or whitespace-only ID is rejected like a missing one")
    @Story("Required fields")
    public void blankIdIsRejected(String field, String kind, UnaryOperator<Batch> blank) {
        Validator.with(ingestClient().postBatch(blank.apply(aBatch()), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining(field);
    }

    @Test(groups = Groups.REGRESSION, description = "A negative batch sequence is rejected")
    @Story("Required fields")
    public void negativeBatchSequenceIsRejected() {
        Validator.with(ingestClient().postBatch(aBatch().withBatchSequence(-1), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("batchSeq");
    }

    @Test(groups = Groups.REGRESSION, description = "A batch with no samples is rejected")
    @Story("Sample count")
    public void emptySamplesIsRejected() {
        Validator.with(ingestClient().postBatch(aBatch().withSamples(List.of()), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("samples");
    }

    @Test(groups = Groups.REGRESSION, description = "A batch over the sample limit is rejected")
    @Story("Sample count")
    public void tooManySamplesIsRejected() {
        Validator.with(ingestClient().postBatch(
                        aBatch().withSamples(samples(IngestLimits.MAX_SAMPLES + 1)), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("at most");
    }

    @DataProvider
    public Object[][] malformedBodies() {
        return new Object[][] {
            {"broken JSON", "{\"batchId\": "},
            {"not JSON", "this is not json"},
            {"empty body", ""},
            {"wrong type", wrongTypeBody()}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "malformedBodies",
            description = "A body that cannot be read as a batch is rejected with 400 and the usual error format")
    @Story("Malformed body")
    public void malformedBodyIsRejected(String kind, String body) {
        Validator.with(ingestClient().postRaw(body, Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .hasCorrelationHeader()
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasCorrelationId()
                .hasErrors();
    }

    @Test(groups = Groups.REGRESSION, description = "A rejected batch is not published and does not use up its batch ID: the corrected batch is accepted")
    @Story("Rejected batches leave no trace")
    public void invalidBatchIsNotPublishedAndDoesNotBurnTheBatchId() {
        Batch invalid = aBatch().withTripId(null);
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatch(invalid, correlationId)).hasStatus(Http.BAD_REQUEST);
        assertPublishedTimes(0, invalid, correlationId);

        Batch corrected = invalid.withTripId(Ids.trip());
        Validator.with(ingestClient().postBatchWithRetry(corrected, correlationId))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(corrected.batchId());
    }

    private static String wrongTypeBody() {
        ObjectNode json = (ObjectNode) Json.tree(Json.write(aBatch()));
        json.put("batchSeq", "not a number");
        return json.toString();
    }
}
