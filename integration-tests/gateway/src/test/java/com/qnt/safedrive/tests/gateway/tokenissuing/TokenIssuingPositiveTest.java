package com.qnt.safedrive.tests.gateway.tokenissuing;

import static com.qnt.safedrive.tests.core.factory.LoginFactory.aLogin;

import com.qnt.safedrive.tests.core.model.LoginRequest;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Scopes;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TokenIssuingPositiveTest extends AbstractTokenIssuingTest {

    @Test(groups = Groups.SMOKE, description = "A seeded driver logs in and gets a bearer token that lasts one hour and carries the user's scopes")
    @Story("Login")
    public void loginIssuesAToken() {
        Validator.with(gatewayClient().token(aLogin(), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.OK)
                .tokenBody()
                .hasAccessToken()
                .hasTokenType(GatewayUsers.TOKEN_TYPE)
                .expiresIn(GatewayUsers.TOKEN_TTL_SECONDS)
                .hasScopes(Scopes.TELEMETRY_WRITE, Scopes.SCORE_READ, Scopes.PREMIUM_READ, Scopes.REWARDS_READ, Scopes.CLAIMS_READ);
    }

    @Test(groups = Groups.REGRESSION, description = "The token is an RS256 JWT with the issuer, audience, user, driver from the header, scopes and a one hour life")
    @Story("Claims")
    public void tokenCarriesTheExpectedClaims() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().token(aLogin(), driverId, Ids.correlation()))
                .hasStatus(Http.OK)
                .tokenBody()
                .claims()
                .isSignedWith(GatewayUsers.SIGNING_ALGORITHM)
                .hasIssuer(GatewayUsers.ISSUER)
                .hasAudience(GatewayUsers.AUDIENCE)
                .hasUserIdAsSubject()
                .hasDriverId(driverId)
                .hasScopes(Scopes.TELEMETRY_WRITE, Scopes.SCORE_READ, Scopes.PREMIUM_READ, Scopes.REWARDS_READ, Scopes.CLAIMS_READ)
                .expiresAfter(GatewayUsers.TOKEN_TTL_SECONDS)
                .wasIssuedWithin(120);
    }

    @Test(groups = Groups.REGRESSION, description = "The driver in the token is the one sent in the X-Driver-Id header, so the same user can get tokens for different drivers")
    @Story("Claims")
    public void driverComesFromTheHeader() {
        String first = Ids.driver();
        String second = Ids.driver();

        Validator.with(gatewayClient().token(aLogin(), first, Ids.correlation())).hasStatus(Http.OK).tokenBody().claims().hasDriverId(first);
        Validator.with(gatewayClient().token(aLogin(), second, Ids.correlation())).hasStatus(Http.OK).tokenBody().claims().hasDriverId(second);
    }

    @Test(groups = Groups.REGRESSION, description = "A user with fewer scopes gets a token with only those scopes")
    @Story("Claims")
    public void scopesFollowTheUser() {
        Validator.with(gatewayClient().token(new LoginRequest(GatewayUsers.READ_ONLY_EMAIL, GatewayUsers.PASSWORD), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.OK)
                .tokenBody()
                .hasScopes(Scopes.SCORE_READ)
                .claims()
                .hasScopes(Scopes.SCORE_READ);
    }

    @Test(groups = Groups.REGRESSION, description = "The token answer carries the caller's correlation ID")
    @Story("Correlation")
    public void correlationIdIsEchoed() {
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().token(aLogin(), Ids.driver(), correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "The public keys are served and verify the signature of an issued token")
    @Story("Public keys")
    public void publicKeysVerifyTheToken() {
        String token = tokenOf(GatewayUsers.DRIVER_EMAIL, Ids.driver());

        Validator.with(gatewayClient().jwks())
                .hasStatus(Http.OK)
                .jwksBody()
                .hasSigningKey(GatewayUsers.SIGNING_ALGORITHM)
                .hasNoPrivateKeyMaterial()
                .verifiesSignatureOf(token);
    }
}
