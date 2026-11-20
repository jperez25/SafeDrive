package com.qnt.safedrive.tests.core.client;

import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.model.Batch;
import io.restassured.response.Response;

/** Client for {@code telemetry-ingest}. Returns the raw response so tests can assert status and headers. */
public class TelemetryIngestClient extends ServiceClient {

    public TelemetryIngestClient(String baseUrl) {
        super(baseUrl);
    }

    /** One call, no retry: for negative tests that expect a rejection. */
    public Response postBatch(Batch batch) {
        return postBatch(batch, null);
    }

    @Step("Post telemetry batch (correlation ID {correlationId})")
    public Response postBatch(@Param(mode = Parameter.Mode.HIDDEN) Batch batch, String correlationId) {
        return post(Paths.INGEST_BATCHES, Json.write(batch), correlationId);
    }

    /** For negative tests: any body, including malformed JSON. One call, no retry. */
    @Step("Post raw body to telemetry ingest (correlation ID {correlationId})")
    public Response postRaw(@Param(mode = Parameter.Mode.HIDDEN) String body, String correlationId) {
        return post(Paths.INGEST_BATCHES, body, correlationId);
    }

    /** For a valid batch that must be accepted: retries when the service is down or answers 5xx. */
    @Step("Post telemetry batch with retry (correlation ID {correlationId})")
    public Response postBatchWithRetry(@Param(mode = Parameter.Mode.HIDDEN) Batch batch, String correlationId) {
        return postWithRetry(Paths.INGEST_BATCHES, Json.write(batch), correlationId);
    }
}
