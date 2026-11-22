package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Step;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** The header and claims of a gateway access token. */
public class JwtClaimsValidator {

    private final JsonNode header;
    private final JsonNode payload;

    JwtClaimsValidator(JsonNode header, JsonNode payload) {
        this.header = header;
        this.payload = payload;
    }

    @Step("Is signed with {algorithm}")
    public JwtClaimsValidator isSignedWith(String algorithm) {
        assertThat(header.path("alg").asString()).as("header alg").isEqualTo(algorithm);
        return this;
    }

    @Step("Has issuer {issuer}")
    public JwtClaimsValidator hasIssuer(String issuer) {
        assertThat(payload.path("iss").asString()).as("claim iss").isEqualTo(issuer);
        return this;
    }

    @Step("Has audience {audience}")
    public JwtClaimsValidator hasAudience(String audience) {
        assertThat(payload.path("aud").asString()).as("claim aud").isEqualTo(audience);
        return this;
    }

    /** The subject is the user's ID (a UUID). */
    @Step("Has a user id as subject")
    public JwtClaimsValidator hasUserIdAsSubject() {
        assertThat(UUID.fromString(payload.path("sub").asString())).as("claim sub").isNotNull();
        return this;
    }

    @Step("Has driver id {driverId}")
    public JwtClaimsValidator hasDriverId(String driverId) {
        assertThat(payload.path("driverId").asString()).as("claim driverId").isEqualTo(driverId);
        return this;
    }

    @Step("Has scopes {scopes}")
    public JwtClaimsValidator hasScopes(String... scopes) {
        assertThat(payload.path("scope").asString().split(" ")).as("claim scope").containsExactlyInAnyOrder(scopes);
        return this;
    }

    @Step("Expires {seconds} seconds after it was issued")
    public JwtClaimsValidator expiresAfter(long seconds) {
        assertThat(payload.path("exp").asLong() - payload.path("iat").asLong()).as("exp - iat").isEqualTo(seconds);
        return this;
    }

    /** The token was issued just now (within the given number of seconds), so the clock and the TTL are real. */
    @Step("Was issued within {seconds} seconds")
    public JwtClaimsValidator wasIssuedWithin(long seconds) {
        long now = System.currentTimeMillis() / 1000;
        assertThat(payload.path("iat").asLong()).as("claim iat").isBetween(now - seconds, now + seconds);
        return this;
    }
}
