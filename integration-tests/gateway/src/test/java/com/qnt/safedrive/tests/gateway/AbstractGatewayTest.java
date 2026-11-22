package com.qnt.safedrive.tests.gateway;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.client.GatewayClient;
import com.qnt.safedrive.tests.core.factory.LoginFactory;
import com.qnt.safedrive.tests.core.model.LoginRequest;
import com.qnt.safedrive.tests.core.model.TokenResponse;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Epic;
import io.restassured.response.Response;
import java.time.Duration;

/**
 * Shared setup for every gateway feature. The gateway is a PoC entry point: tests log in as the users seeded by the
 * gateway's own SQL script, then call the services through {@code /api}. Every test asks for its own token and uses its
 * own driver ID, so parallel tests never share anything.
 */
@Epic("gateway")
public abstract class AbstractGatewayTest extends AbstractTest {

    /** How long to watch for something that must not happen (publishing is asynchronous, so one look is not enough). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** A token of the seeded user for this driver; the request itself is checked to have succeeded. */
    protected String tokenOf(String email, String driverId) {
        Response response = gatewayClient().token(new LoginRequest(email, GatewayUsers.PASSWORD), driverId, Ids.correlation());
        Validator.with(response).hasStatus(Http.OK);
        return Json.read(response.asString(), TokenResponse.class).accessToken();
    }

    /** The "Authorization" header value of a token of the driver with every scope. */
    protected String fullAccessFor(String driverId) {
        return GatewayClient.bearer(tokenOf(LoginFactory.aLogin().email(), driverId));
    }

    /** The "Authorization" header value of a token of the user that only has {@code score:read}. */
    protected String readOnlyFor(String driverId) {
        return GatewayClient.bearer(tokenOf(GatewayUsers.READ_ONLY_EMAIL, driverId));
    }
}
