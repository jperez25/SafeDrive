package com.qnt.safedrive.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JwtServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

    private static JwtService service(String aud, Clock clock) {
        return new JwtService("", "safedrive-gateway", aud, 3600, clock, MAPPER);
    }

    @Test
    void issuedTokenVerifiesAndCarriesClaims() {
        JwtService jwt = service("safedrive", NOW);
        String token = jwt.issue("u-1", "d-7", List.of("score:read", "claims:read"));

        var claims = jwt.verify(token).orElseThrow();
        assertThat(claims.subject()).isEqualTo("u-1");
        assertThat(claims.driverId()).isEqualTo("d-7");
        assertThat(claims.scopes()).containsExactly("score:read", "claims:read");
        assertThat(claims.expiresAt()).isEqualTo(NOW.millis() / 1000 + 3600);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service("safedrive", NOW);
        String token = jwt.issue("u-1", "d-7", List.of("score:read"));
        JwtService sameKeyLater = withClock(jwt, Duration.ofSeconds(3600));
        assertThat(sameKeyLater.verify(token)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service("safedrive", NOW);
        String[] parts = jwt.issue("u-1", "d-7", List.of("score:read")).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"safedrive-gateway\",\"aud\":\"safedrive\",\"sub\":\"u-1\",\"driverId\":\"d-7\",\"scope\":\"claims:read\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(jwt.verify(parts[0] + "." + forged + "." + parts[2])).isEmpty();
    }

    @Test
    void tokenFromAnotherKeyIsRejected() {
        String token = service("safedrive", NOW).issue("u-1", "d-7", List.of("score:read"));
        assertThat(service("safedrive", NOW).verify(token)).isEmpty();
    }

    @Test
    void wrongAudienceIsRejected() {
        JwtService jwt = service("other-audience", NOW);
        assertThat(jwt.verify(jwt.issue("u-1", "d-7", List.of("score:read")))).isPresent();
        // Same key, verifier expecting another audience.
        JwtService strict = withAudience(jwt, "safedrive");
        assertThat(strict.verify(jwt.issue("u-1", "d-7", List.of("score:read")))).isEmpty();
    }

    @Test
    void algNoneAndGarbageAreRejected() {
        JwtService jwt = service("safedrive", NOW);
        var enc = Base64.getUrlEncoder().withoutPadding();
        String none = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString("{\"iss\":\"safedrive-gateway\",\"aud\":\"safedrive\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8))
                + ".";
        assertThat(jwt.verify(none)).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify("a.b")).isEmpty();
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
    }

    @Test
    void jwksExposesOnlyThePublicKey() {
        String jwks = MAPPER.writeValueAsString(service("safedrive", NOW).jwks());
        assertThat(jwks).contains("\"kty\":\"RSA\"", "\"alg\":\"RS256\"", "\"n\":", "\"e\":\"AQAB\"");
        assertThat(jwks).doesNotContain("\"d\":", "\"p\":", "\"q\":");
    }

    // The service holds its keys privately, so these helpers go through a PEM export of its private key.
    private static JwtService withClock(JwtService base, Duration offset) {
        return new JwtService(pem(base), "safedrive-gateway", "safedrive", 3600, Clock.offset(NOW, offset), MAPPER);
    }

    private static JwtService withAudience(JwtService base, String aud) {
        return new JwtService(pem(base), "safedrive-gateway", aud, 3600, NOW, MAPPER);
    }

    private static String pem(JwtService base) {
        return base.exportPrivateKeyPem();
    }
}
