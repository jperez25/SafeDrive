package com.qnt.safedrive.tests.gateway.tokenvalidation;

import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.gateway.AbstractGatewayTest;
import io.qameta.allure.Feature;
import io.restassured.response.Response;

/**
 * Shared setup for the token validation feature: what the gateway does with a token on a proxied call. Every test
 * calls the same route (the premium of a driver nobody has rated), so the only difference is the Authorization header.
 */
@Feature("Token validation")
public abstract class AbstractTokenValidationTest extends AbstractGatewayTest {

    /** One call of the premium route with this exact Authorization header value (null sends no header). */
    protected Response callWith(String authorization, String correlationId) {
        return gatewayClient().get(Paths.driverPremium(Ids.driver()), authorization, correlationId);
    }
}
