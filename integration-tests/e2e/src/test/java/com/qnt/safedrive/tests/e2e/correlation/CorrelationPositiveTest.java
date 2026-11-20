package com.qnt.safedrive.tests.e2e.correlation;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.util.List;
import org.testng.annotations.Test;

public class CorrelationPositiveTest extends AbstractCorrelationTest {

    private static final List<String> SERVICES =
            List.of(Services.TELEMETRY_INGEST, Services.TRIP_PROCESSOR, Services.SCORING, Services.POLICY_RATING);

    @Test(groups = Groups.REGRESSION, description = "Without a caller ID the edge creates one, and that ID reaches premium.adjusted")
    @Story("Created at the edge")
    public void ingestCreatesTheIdWhenTheCallerSendsNone() {
        Batch batch = aBatch();

        Response response = ingestClient().postBatchWithRetry(batch, null);

        Validator.with(response).hasStatus(Http.ACCEPTED).hasCorrelationHeader();
        String correlationId = response.header(Http.CORRELATION_HEADER);
        assertThat(correlationId).as("correlation ID created by ingest").isNotBlank();
        Validator.with(awaitPremiumAdjusted(batch.driverId(), correlationId, 1))
                .envelope()
                .hasCorrelationId(correlationId);
        Validator.with(awaitEvent(Topics.TRIP_RAW, correlationId, "tripId", batch.tripId()))
                .envelope()
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Every service logs the correlation ID of the request, and no log line carries device or position data")
    @Story("Logs")
    public void everyServiceLogsTheIdWithoutPersonalData() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);
        awaitPremiumAdjusted(batch.driverId(), correlationId, 1);

        for (String service : SERVICES) {
            assertThat(tracer.awaitLog(service, correlationId))
                    .as("%s log lines for correlation ID %s", service, correlationId)
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line.json())
                            .doesNotContain(batch.deviceId())
                            .doesNotContain(String.valueOf(batch.samples().getFirst().latitude())));
        }
    }
}
