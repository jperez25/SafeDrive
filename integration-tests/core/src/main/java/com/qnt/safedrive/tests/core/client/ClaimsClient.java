package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Paths;
import io.qameta.allure.Step;
import io.restassured.response.Response;

/** Client for {@code claims}. Returns the raw response so tests can assert status and headers. */
public class ClaimsClient extends ServiceClient {

    public ClaimsClient(String baseUrl) {
        super(baseUrl);
    }

    @Step("Get claims of driver {driverId}")
    public Response getClaims(String driverId, String correlationId) {
        return get(Paths.driverClaims(driverId), correlationId);
    }

    /** For claims that must exist: retries when the service is down or answers 5xx. */
    @Step("Get claims of driver {driverId} with retry")
    public Response getClaimsWithRetry(String driverId, String correlationId) {
        return getWithRetry(Paths.driverClaims(driverId), correlationId);
    }

    @Step("Get claim {claimId}")
    public Response getClaim(String claimId, String correlationId) {
        return get(Paths.claim(claimId), correlationId);
    }
}
