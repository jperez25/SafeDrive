package com.qnt.safedrive.gateway;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates and verifies RS256 JWTs with the JDK only. Only RS256 is accepted (so no "alg: none"). */
class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String KEY_ID = "gw-1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    /** What the gateway needs from a valid token. */
    record Claims(String subject, String driverId, List<String> scopes, long expiresAt) {}

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;
    private final Clock clock;
    private final JsonMapper mapper;

    JwtService(String privateKeyPem, String issuer, String audience, long ttlSeconds, Clock clock, JsonMapper mapper) {
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
        this.mapper = mapper;
        try {
            if (privateKeyPem == null || privateKeyPem.isBlank()) {
                log.warn("GATEWAY_JWT_PRIVATE_KEY is not set: using an ephemeral key, tokens are invalid after a restart");
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair pair = gen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = (RSAPublicKey) pair.getPublic();
            } else {
                this.privateKey = parsePrivateKey(privateKeyPem);
                RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
                PublicKey pub = KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                this.publicKey = (RSAPublicKey) pub;
            }
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("Cannot load the JWT signing key (expected an RSA PKCS#8 PEM)");
        }
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    /** PKCS#8 PEM of the signing key. Used by tests to build a second service on the same key. */
    String exportPrivateKeyPem() {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    long ttlSeconds() {
        return ttlSeconds;
    }

    String issue(String userId, String driverId, List<String> scopes) {
        long now = clock.millis() / 1000;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", KEY_ID);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("aud", audience);
        payload.put("sub", userId);
        payload.put("driverId", driverId);
        payload.put("scope", String.join(" ", scopes));
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        String signingInput = b64(mapper.writeValueAsBytes(header)) + "." + b64(mapper.writeValueAsBytes(payload));
        return signingInput + "." + B64.encodeToString(sign(signingInput));
    }

    /** Empty when the token is malformed, tampered, not RS256, expired, or has the wrong issuer or audience. */
    Optional<Claims> verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return Optional.empty();
            }
            JsonNode header = mapper.readTree(B64D.decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asString(""))) {
                return Optional.empty();
            }
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(B64D.decode(parts[2]))) {
                return Optional.empty();
            }
            JsonNode p = mapper.readTree(B64D.decode(parts[1]));
            long exp = p.path("exp").asLong(0);
            if (exp <= clock.millis() / 1000
                    || !issuer.equals(p.path("iss").asString(""))
                    || !audience.equals(p.path("aud").asString(""))) {
                return Optional.empty();
            }
            String scope = p.path("scope").asString("");
            List<String> scopes = scope.isBlank() ? List.of() : List.of(scope.split(" "));
            return Optional.of(new Claims(p.path("sub").asString(""), p.path("driverId").asString(""), scopes, exp));
        } catch (RuntimeException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    /** The public key as a JWK set, for clients that want to verify tokens. */
    Map<String, Object> jwks() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("alg", "RS256");
        key.put("use", "sig");
        key.put("kid", KEY_ID);
        key.put("n", unsigned(publicKey.getModulus()));
        key.put("e", unsigned(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(key));
    }

    private byte[] sign(String input) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(input.getBytes(StandardCharsets.US_ASCII));
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign the token");
        }
    }

    private static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    private static String unsigned(BigInteger n) {
        byte[] bytes = n.toByteArray();
        int start = bytes[0] == 0 ? 1 : 0;
        return B64.encodeToString(java.util.Arrays.copyOfRange(bytes, start, bytes.length));
    }
}
