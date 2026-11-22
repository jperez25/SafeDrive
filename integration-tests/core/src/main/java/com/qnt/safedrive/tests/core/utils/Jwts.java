package com.qnt.safedrive.tests.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads and forges JWTs for the gateway tests. The test never has the gateway's key, so a forged token is either
 * unsigned, signed with a key of its own, or a real token with a changed part (the signature then no longer fits).
 */
public final class Jwts {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Jwts() {}

    public static JsonNode header(String token) {
        return part(token, 0);
    }

    public static JsonNode payload(String token) {
        return part(token, 1);
    }

    /** The real token with one payload field replaced; header and signature are kept. */
    public static String withPayloadField(String token, String field, String value) {
        String[] parts = token.split("\\.");
        ObjectNode payload = (ObjectNode) payload(token);
        payload.put(field, value);
        return parts[0] + "." + encode(payload.toString()) + "." + parts[2];
    }

    /** Same header and payload, signed with a fresh key the gateway does not know. */
    public static String signedWithAnotherKey(String token) {
        String[] parts = token.split("\\.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(pair.getPrivate());
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return parts[0] + "." + parts[1] + "." + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot sign the forged token", e);
        }
    }

    /** The payload of the token with {@code "alg":"none"} and no signature. */
    public static String withAlgNone(String token) {
        return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + token.split("\\.")[1] + ".";
    }

    public static String withoutSignature(String token) {
        String[] parts = token.split("\\.");
        return parts[0] + "." + parts[1] + ".";
    }

    private static JsonNode part(String token, int index) {
        return Json.tree(new String(DECODER.decode(token.split("\\.")[index]), StandardCharsets.UTF_8));
    }

    private static String encode(String json) {
        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
