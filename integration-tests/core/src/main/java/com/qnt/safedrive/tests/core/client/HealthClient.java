package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Paths;
import io.restassured.response.Response;

/** Liveness and readiness of any service. */
public class HealthClient extends ServiceClient {

    public HealthClient(String baseUrl) {
        super(baseUrl);
    }

    public Response liveness() {
        return request(null).get(Paths.HEALTH_LIVENESS);
    }

    public Response readiness() {
        return request(null).get(Paths.HEALTH_READINESS);
    }

    public Response overall() {
        return request(null).get(Paths.HEALTH);
    }

    /** Any path on the service, for negative checks. */
    public Response get(String path) {
        return request(null).get(path);
    }
}
