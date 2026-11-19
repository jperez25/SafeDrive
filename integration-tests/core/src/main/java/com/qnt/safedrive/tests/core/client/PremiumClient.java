package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Paths;
import io.restassured.response.Response;

/** Client for {@code policy-rating}. Returns the raw response so tests can assert status and headers. */
public class PremiumClient extends ServiceClient {

    public PremiumClient(String baseUrl) {
        super(baseUrl);
    }

    /** One call, no retry: for negative tests that expect a 404. */
    public Response getPremium(String driverId, String correlationId) {
        return get(Paths.driverPremium(driverId), correlationId);
    }

    /** For a rating that must exist: retries when the service is down or answers 5xx. */
    public Response getPremiumWithRetry(String driverId, String correlationId) {
        return getWithRetry(Paths.driverPremium(driverId), correlationId);
    }
}
