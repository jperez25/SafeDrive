package com.qnt.safedrive.tests.core.client;

import io.qameta.allure.Step;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import io.restassured.response.Response;

/** Client for {@code scoring}. Returns the raw response so tests can assert status and headers. */
public class ScoringClient extends ServiceClient {

    public ScoringClient(String baseUrl) {
        super(baseUrl);
    }

    /** One call, no retry: for negative tests that expect a 404. */
    @Step("Get score of driver {driverId}")
    public Response getScore(String driverId, String correlationId) {
        return get(Paths.driverScore(driverId), correlationId);
    }

    /** For a score that must exist: retries when the service is down or answers 5xx. */
    @Step("Get score of driver {driverId} with retry")
    public Response getScoreWithRetry(String driverId, String correlationId) {
        return getWithRetry(Paths.driverScore(driverId), correlationId);
    }
}
