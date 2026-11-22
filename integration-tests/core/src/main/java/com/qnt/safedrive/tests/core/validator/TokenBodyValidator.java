package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.TokenResponse;
import com.qnt.safedrive.tests.core.utils.Jwts;
import io.qameta.allure.Step;

/** The body of the gateway token endpoint. */
public class TokenBodyValidator {

    private final TokenResponse actual;

    TokenBodyValidator(TokenResponse actual) {
        this.actual = actual;
    }

    @Step("Has an access token")
    public TokenBodyValidator hasAccessToken() {
        assertThat(actual.accessToken()).as("body accessToken").isNotBlank();
        assertThat(actual.accessToken().split("\\.", -1)).as("JWT parts").hasSize(3).noneMatch(String::isEmpty);
        return this;
    }

    @Step("Has token type {tokenType}")
    public TokenBodyValidator hasTokenType(String tokenType) {
        assertThat(actual.tokenType()).as("body tokenType").isEqualTo(tokenType);
        return this;
    }

    @Step("Expires in {seconds} seconds")
    public TokenBodyValidator expiresIn(long seconds) {
        assertThat(actual.expiresIn()).as("body expiresIn").isEqualTo(seconds);
        return this;
    }

    @Step("Has scopes {scopes}")
    public TokenBodyValidator hasScopes(String... scopes) {
        assertThat(actual.scope().split(" ")).as("body scope").containsExactlyInAnyOrder(scopes);
        return this;
    }

    /** The claims inside the access token (read, not verified: signature checks use the public keys). */
    @Step("Validate token claims")
    public JwtClaimsValidator claims() {
        return new JwtClaimsValidator(Jwts.header(actual.accessToken()), Jwts.payload(actual.accessToken()));
    }

    public String accessToken() {
        return actual.accessToken();
    }
}
