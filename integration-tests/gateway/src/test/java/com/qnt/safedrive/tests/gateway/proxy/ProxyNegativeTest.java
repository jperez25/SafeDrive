package com.qnt.safedrive.tests.gateway.proxy;

import com.qnt.safedrive.tests.core.model.constants.GatewayMessages;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ProxyNegativeTest extends AbstractProxyTest {

    @Test(groups = Groups.REGRESSION, description = "A path the gateway does not proxy is 404 with a gateway error body")
    @Story("Unknown path")
    public void unknownPathIsNotFound() {
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().get("/v1/drivers/" + Ids.driver(), fullAccessFor(Ids.driver()), correlationId))
                .hasStatus(Http.NOT_FOUND)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_NOT_FOUND)
                .hasMessage(GatewayMessages.UNKNOWN_PATH)
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "The service's actuator and metrics are not reachable through the gateway")
    @Story("Unknown path")
    public void serviceActuatorIsNotExposed() {
        Validator.with(gatewayClient().get("/actuator/prometheus", fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "The service paths are not served by the gateway outside /api")
    @Story("Unknown path")
    public void servicePathsOutsideApiAreNotServed() {
        Validator.with(healthClient(Services.GATEWAY).get(Paths.driverScore(Ids.driver())))
                .hasStatus(Http.NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "A known path with the wrong method is 405")
    @Story("Wrong method")
    public void wrongMethodIsNotAllowed() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().post(Paths.driverScore(driverId), fullAccessFor(driverId), Ids.correlation()))
                .hasStatus(Http.METHOD_NOT_ALLOWED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_METHOD_NOT_ALLOWED);
    }

    @Test(groups = Groups.REGRESSION, description = "A body over the limit is 413 and is not sent to ingest")
    @Story("Body limit")
    public void oversizedBodyIsRejected() {
        String oversized = "x".repeat(1_100_000);

        Validator.with(gatewayClient().postRaw(oversized, fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.PAYLOAD_TOO_LARGE)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_PAYLOAD_TOO_LARGE);
    }
}
