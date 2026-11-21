package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Paths;
import io.qameta.allure.Step;
import io.restassured.response.Response;

/** Client for {@code rewards}. Returns the raw response so tests can assert status and headers. */
public class RewardsClient extends ServiceClient {

    public RewardsClient(String baseUrl) {
        super(baseUrl);
    }

    @Step("Get rewards of driver {driverId}")
    public Response getRewards(String driverId, String correlationId) {
        return get(Paths.driverRewards(driverId), correlationId);
    }

    /** For rewards that must exist: retries when the service is down or answers 5xx. */
    @Step("Get rewards of driver {driverId} with retry")
    public Response getRewardsWithRetry(String driverId, String correlationId) {
        return getWithRetry(Paths.driverRewards(driverId), correlationId);
    }
}
