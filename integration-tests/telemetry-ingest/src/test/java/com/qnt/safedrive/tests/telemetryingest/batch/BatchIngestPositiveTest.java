package com.qnt.safedrive.tests.telemetryingest.batch;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.nextOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
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
import io.restassured.response.Response;
import java.util.List;
import org.testng.annotations.Test;

public class BatchIngestPositiveTest extends AbstractBatchIngestTest {

    @Test(groups = Groups.SMOKE, description = "A valid batch is accepted and the correlation ID comes back")
    @Story("Accept a batch")
    public void validBatchIsAccepted() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        Response response = ingestClient().postBatchWithRetry(batch, correlationId);

        Validator.with(response)
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(correlationId)
                .hasAcceptedSamples(batch.samples().size())
                .hasNoRejectedSamples();
    }

    @Test(groups = Groups.REGRESSION,
            description = "A batch sent twice is accepted once, answered as duplicate the second time, and published once")
    @Story("Duplicate batches")
    public void duplicateBatchIsPublishedOnce() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatchWithRetry(batch, correlationId))
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(correlationId);
        tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1);

        Validator.with(ingestClient().postBatch(batch, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_DUPLICATE)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(correlationId);
        assertPublishedTimes(1, batch, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "An accepted batch reaches trip.raw: keyed by trip, valid against the contract, payload equal to the batch")
    @Story("Accept a batch")
    public void acceptedBatchIsPublishedToTripRaw() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingestClient().postBatchWithRetry(batch, correlationId);

        TopicReader.Message published = tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1).getFirst();
        Validator.with(published)
                .hasKey(batch.tripId())
                .envelope()
                .hasType(EventTypes.TRIP_RAW)
                .hasSource(Services.TELEMETRY_INGEST)
                .hasCorrelationId(correlationId)
                .hasEventId()
                .matchesContract()
                .tripRawPayload()
                .isEqualTo(batch);
    }

    @Test(groups = Groups.REGRESSION, description = "Without a correlation ID from the caller, the service creates one and uses it everywhere")
    @Story("Correlation ID")
    public void missingCorrelationIdIsGenerated() {
        Batch batch = aBatch();

        Response response = ingestClient().postBatchWithRetry(batch, null);

        String generated = response.getHeader(Http.CORRELATION_HEADER);
        assertThat(generated).as("generated correlation ID").isNotBlank();
        Validator.with(response)
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader()
                .ingestBody()
                .hasCorrelationId(generated);
        Validator.with(tracer.awaitEvents(Topics.TRIP_RAW, generated, 1).getFirst())
                .envelope()
                .hasCorrelationId(generated);
        assertThat(tracer.awaitLog(Services.TELEMETRY_INGEST, generated)).as("service log lines").isNotEmpty();
    }

    @Test(groups = Groups.REGRESSION, description = "The correlation ID the caller sent is on the published event and in the service logs")
    @Story("Correlation ID")
    public void callerCorrelationIdReachesEventAndLogs() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingestClient().postBatchWithRetry(batch, correlationId);

        Validator.with(tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1).getFirst())
                .envelope()
                .hasCorrelationId(correlationId);
        assertThat(tracer.awaitLog(Services.TELEMETRY_INGEST, correlationId))
                .as("service log lines")
                .allSatisfy(line -> assertThat(line.json()).contains("\"correlationId\":\"" + correlationId + "\""));
    }

    @Test(groups = Groups.REGRESSION, description = "Two batches of one trip are both published with the trip ID as key, in the order they were sent")
    @Story("Ordering key")
    public void batchesOfOneTripShareTheKafkaKey() {
        Batch first = aBatch().withLast(false);
        Batch second = nextOf(first);
        String correlationId = Ids.correlation();

        ingestClient().postBatchWithRetry(first, correlationId);
        ingestClient().postBatchWithRetry(second, correlationId);

        List<TopicReader.Message> published = tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 2);
        assertThat(published).extracting(TopicReader.Message::key).containsOnly(first.tripId());
        assertThat(published)
                .extracting(m -> m.envelope().payloadText("batchId"))
                .containsExactly(first.batchId(), second.batchId());
    }

    @Test(groups = Groups.REGRESSION, description = "Ingest does not care about batch order: sequence 1 before sequence 0 are both accepted")
    @Story("Out of order")
    public void outOfSequenceBatchesAreAccepted() {
        Batch first = aBatch().withLast(false);
        Batch second = nextOf(first);

        Validator.with(ingestClient().postBatchWithRetry(second, Ids.correlation()))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED);
        Validator.with(ingestClient().postBatchWithRetry(first, Ids.correlation()))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED);
    }
}
