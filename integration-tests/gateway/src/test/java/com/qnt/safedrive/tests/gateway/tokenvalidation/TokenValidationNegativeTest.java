package com.qnt.safedrive.tests.gateway.tokenvalidation;

import com.qnt.safedrive.tests.core.client.GatewayClient;
import com.qnt.safedrive.tests.core.model.constants.GatewayMessages;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Scopes;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Jwts;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TokenValidationNegativeTest extends AbstractTokenValidationTest {

    @Test(groups = Groups.REGRESSION, description = "A call without an Authorization header gets 401")
    @Story("Missing token")
    public void noAuthorizationHeaderIsRejected() {
        String correlationId = Ids.correlation();

        Validator.with(callWith(null, correlationId))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_UNAUTHORIZED)
                .hasMessage(GatewayMessages.MISSING_BEARER_TOKEN)
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Another scheme than Bearer gets 401, even with a real token in it")
    @Story("Missing token")
    public void nonBearerSchemeIsRejected() {
        String token = tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver());

        Validator.with(callWith("Basic " + token, Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.MISSING_BEARER_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A Bearer header with no token gets 401")
    @Story("Missing token")
    public void emptyBearerIsRejected() {
        Validator.with(callWith("Bearer ", Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_UNAUTHORIZED);
    }

    @Test(groups = Groups.REGRESSION, description = "A token that is not a JWT gets 401")
    @Story("Invalid token")
    public void garbageTokenIsRejected() {
        Validator.with(callWith(GatewayClient.bearer("not-a-jwt"), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A token with a changed payload (read-only user made to look like a full user) fails the signature check")
    @Story("Invalid token")
    public void tamperedPayloadIsRejected() {
        String readOnly = tokenOf(GatewayUsers.READ_ONLY_EMAIL, Ids.driver());
        String forged = Jwts.withPayloadField(readOnly, "scope", Scopes.PREMIUM_READ);

        Validator.with(callWith(GatewayClient.bearer(forged), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A token signed with another RSA key gets 401")
    @Story("Invalid token")
    public void tokenSignedWithAnotherKeyIsRejected() {
        String forged = Jwts.signedWithAnotherKey(tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver()));

        Validator.with(callWith(GatewayClient.bearer(forged), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A token that says alg none and has no signature gets 401")
    @Story("Invalid token")
    public void algNoneIsRejected() {
        String forged = Jwts.withAlgNone(tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver()));

        Validator.with(callWith(GatewayClient.bearer(forged), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A real token with its signature cut off gets 401")
    @Story("Invalid token")
    public void tokenWithoutSignatureIsRejected() {
        String forged = Jwts.withoutSignature(tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver()));

        Validator.with(callWith(GatewayClient.bearer(forged), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }

    @Test(groups = Groups.REGRESSION, description = "A token meant for another audience gets 401")
    @Story("Invalid token")
    public void changedAudienceIsRejected() {
        String forged = Jwts.withPayloadField(tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver()), "aud", "another-audience");

        Validator.with(callWith(GatewayClient.bearer(forged), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_TOKEN);
    }
}
