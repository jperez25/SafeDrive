package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.LoginRequest;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for {@code gateway}. Returns the raw response so tests can assert status and headers. Calls that go through
 * the proxy take the whole {@code Authorization} header value ({@link #bearer(String)} builds it), so a test can also
 * send a wrong scheme or no header at all. One call each, no retry: the gateway answers or rejects at once.
 */
public class GatewayClient extends ServiceClient {

    public GatewayClient(String baseUrl) {
        super(baseUrl);
    }

    public static String bearer(String token) {
        return Http.BEARER_PREFIX + token;
    }

    @Step("Request a token (driver ID header {driverId})")
    public Response token(@Param(mode = Parameter.Mode.HIDDEN) LoginRequest login, String driverId, String correlationId) {
        return tokenRaw(Json.write(login), driverId, correlationId);
    }

    /** Any body, including malformed or empty JSON; {@code driverId} null leaves the header out. */
    @Step("Request a token with a raw body (driver ID header {driverId})")
    public Response tokenRaw(@Param(mode = Parameter.Mode.HIDDEN) String body, String driverId, String correlationId) {
        Map<String, String> headers = new HashMap<>();
        if (driverId != null) {
            headers.put(Http.DRIVER_HEADER, driverId);
        }
        return request(correlationId, headers).body(body).post(Paths.GATEWAY_TOKEN);
    }

    @Step("Get the public keys")
    public Response jwks() {
        return request(null).get(Paths.GATEWAY_JWKS);
    }

    /** GET of a service path through the gateway, e.g. {@code Paths.driverScore(id)}. */
    @Step("Get {servicePath} through the gateway")
    public Response get(String servicePath, String authorization, String correlationId) {
        return authorized(correlationId, authorization).get(Paths.viaGateway(servicePath));
    }

    /** POST with no body to a service path (a method the route does not allow, or any other negative check). */
    @Step("Post {servicePath} through the gateway")
    public Response post(String servicePath, String authorization, String correlationId) {
        return authorized(correlationId, authorization).post(Paths.viaGateway(servicePath));
    }

    @Step("Post telemetry batch through the gateway (correlation ID {correlationId})")
    public Response postBatch(@Param(mode = Parameter.Mode.HIDDEN) Batch batch, String authorization, String correlationId) {
        return postRaw(Json.write(batch), authorization, correlationId);
    }

    @Step("Post raw body to telemetry ingest through the gateway (correlation ID {correlationId})")
    public Response postRaw(@Param(mode = Parameter.Mode.HIDDEN) String body, String authorization, String correlationId) {
        return authorized(correlationId, authorization).body(body).post(Paths.viaGateway(Paths.INGEST_BATCHES));
    }

    private RequestSpecification authorized(String correlationId, String authorization) {
        Map<String, String> headers = new HashMap<>();
        if (authorization != null) {
            headers.put(Http.AUTHORIZATION_HEADER, authorization);
        }
        return request(correlationId, headers);
    }
}
