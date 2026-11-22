package com.qnt.safedrive.tests.gateway.logs;

import static com.qnt.safedrive.tests.core.factory.LoginFactory.aLogin;

import com.qnt.safedrive.tests.core.model.TokenResponse;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.Test;

public class LogsPositiveTest extends AbstractLogsTest {

    @Test(groups = Groups.REGRESSION, description = "A login is logged with its correlation ID, and the log has neither the password nor the token")
    @Story("Login logs")
    public void loginLogsHaveNoSecrets() {
        String correlationId = Ids.correlation();

        Response response = gatewayClient().token(aLogin(), Ids.driver(), correlationId);

        Validator.with(response).hasStatus(Http.OK);
        String token = Json.read(response.asString(), TokenResponse.class).accessToken();
        Validator.withLogLines(tracer.awaitLog(Services.GATEWAY, correlationId))
                .hasLines()
                .neverLog(GatewayUsers.PASSWORD, token);
    }

    @Test(groups = Groups.REGRESSION, description = "A proxied call is logged with its correlation ID, and the log does not hold the token")
    @Story("Proxy logs")
    public void proxyLogsHaveNoToken() {
        String driverId = Ids.driver();
        String token = tokenOf(GatewayUsers.DRIVER_EMAIL, driverId);
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().get(Paths.driverScore(driverId), "Bearer " + token, correlationId)).hasStatus(Http.NOT_FOUND);

        Validator.withLogLines(tracer.awaitLog(Services.GATEWAY, correlationId))
                .hasLines()
                .neverLog(token);
    }
}
