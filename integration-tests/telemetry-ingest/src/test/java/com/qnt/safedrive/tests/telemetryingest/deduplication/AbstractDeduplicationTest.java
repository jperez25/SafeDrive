package com.qnt.safedrive.tests.telemetryingest.deduplication;

import com.qnt.safedrive.tests.core.client.TelemetryIngestClient;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.utils.Parallel;
import com.qnt.safedrive.tests.telemetryingest.AbstractIngestTest;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Shared setup for the deduplication feature: same batch ID sent more than once, one after another or at once. */
@Feature("Deduplication")
public abstract class AbstractDeduplicationTest extends AbstractIngestTest {

    /** Sends each batch at the same moment, with the same correlation ID, and returns the responses in the same order. */
    protected List<Response> postAtTheSameTime(List<Batch> batches, String correlationId) {
        TelemetryIngestClient client = ingestClient();
        List<Callable<Response>> calls = new ArrayList<>();
        for (Batch batch : batches) {
            calls.add(() -> client.postBatch(batch, correlationId));
        }
        return Parallel.all(calls);
    }
}
