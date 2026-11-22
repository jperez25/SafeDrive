package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Step;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import tools.jackson.databind.JsonNode;

/** The gateway's public key set. */
public class JwksBodyValidator {

    private final JsonNode actual;

    JwksBodyValidator(JsonNode actual) {
        this.actual = actual;
    }

    @Step("Has an RSA signing key for {algorithm}")
    public JwksBodyValidator hasSigningKey(String algorithm) {
        JsonNode key = actual.path("keys").path(0);
        assertThat(key.path("kty").asString()).as("kty").isEqualTo("RSA");
        assertThat(key.path("alg").asString()).as("alg").isEqualTo(algorithm);
        assertThat(key.path("use").asString()).as("use").isEqualTo("sig");
        assertThat(key.path("n").asString()).as("modulus").isNotBlank();
        assertThat(key.path("e").asString()).as("exponent").isNotBlank();
        return this;
    }

    /** Only public parameters are served: no private exponent or prime factors. */
    @Step("Has no private key material")
    public JwksBodyValidator hasNoPrivateKeyMaterial() {
        JsonNode key = actual.path("keys").path(0);
        for (String field : new String[] {"d", "p", "q", "dp", "dq", "qi"}) {
            assertThat(key.has(field)).as("private field %s", field).isFalse();
        }
        return this;
    }

    /** The token's signature verifies with the served key, so a client can trust a token it never saw issued. */
    @Step("Verifies the signature of the token")
    public JwksBodyValidator verifiesSignatureOf(String token) {
        JsonNode key = actual.path("keys").path(0);
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String[] parts = token.split("\\.");
        try {
            var publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, decoder.decode(key.path("n").asString())),
                    new BigInteger(1, decoder.decode(key.path("e").asString()))));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            assertThat(signature.verify(decoder.decode(parts[2]))).as("signature verifies with the served key").isTrue();
        } catch (GeneralSecurityException e) {
            throw new AssertionError("cannot verify the token with the served key", e);
        }
        return this;
    }
}
