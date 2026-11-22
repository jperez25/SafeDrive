package com.qnt.safedrive.tests.gateway.proxy;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.Test;

public class ProxyPositiveTest extends AbstractProxyTest {

    @Test(groups = Groups.SMOKE, description = "A batch posted through the gateway is accepted by ingest and reaches trip.raw with the same correlation ID")
    @Story("Ingest through the gateway")
    public void batchThroughTheGatewayReachesTheTopic() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().postBatch(batch, fullAccessFor(batch.driverId()), correlationId))
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader(correlationId)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(batch.batchId())
                .hasCorrelationId(correlationId);
        Validator.with(awaitTripRaw(batch.tripId(), correlationId)).envelope().hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "The same batch posted twice through the gateway gets ingest's own answers: 202, then 200 duplicate")
    @Story("Ingest through the gateway")
    public void duplicateBatchStatusPassesThrough() {
        Batch batch = aBatch();
        String authorization = fullAccessFor(batch.driverId());

        Validator.with(gatewayClient().postBatch(batch, authorization, Ids.correlation())).hasStatus(Http.ACCEPTED);
        Validator.with(gatewayClient().postBatch(batch, authorization, Ids.correlation()))
                .hasStatus(Http.OK)
                .ingestBody()
                .hasStatus(Statuses.INGEST_DUPLICATE)
                .hasBatchId(batch.batchId());
    }

    @Test(groups = Groups.REGRESSION, description = "An invalid batch gets ingest's own 400 and error list, not a gateway error")
    @Story("Ingest through the gateway")
    public void ingestValidationErrorPassesThrough() {
        Batch batch = aBatch().withTripId(null);

        Validator.with(gatewayClient().postBatch(batch, fullAccessFor(batch.driverId()), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("tripId");
    }

    @Test(groups = Groups.REGRESSION, description = "Malformed JSON sent through the gateway is judged by ingest: 400")
    @Story("Ingest through the gateway")
    public void malformedBodyIsLeftToIngest() {
        Validator.with(gatewayClient().postRaw("{not json", fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "The caller's correlation ID goes to the service and comes back in the service's body")
    @Story("Correlation")
    public void callerCorrelationIdTravelsToTheService() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().get(Paths.driverPremium(driverId), fullAccessFor(driverId), correlationId))
                .hasStatus(Http.NOT_FOUND)
                .hasCorrelationHeader(correlationId)
                .premiumBody()
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Without a caller ID the gateway creates one, and the same ID is what the service saw")
    @Story("Correlation")
    public void gatewayCreatesTheCorrelationId() {
        String driverId = Ids.driver();

        Response response = gatewayClient().get(Paths.driverPremium(driverId), fullAccessFor(driverId), null);

        Validator.with(response).hasStatus(Http.NOT_FOUND).hasCorrelationHeader();
        Validator.with(response)
                .premiumBody()
                .hasCorrelationId(response.header(Http.CORRELATION_HEADER));
    }
}
