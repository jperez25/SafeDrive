package com.qnt.safedrive.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutesTest {

    private final Routes routes = new Routes(new GatewayProperties("i", "a", 3600, "", 1000, 1000, 1000,
            "http://ingest", "http://scoring", "http://rating", "http://rewards", "http://claims"));

    @Test
    void eachRouteNeedsItsScopeAndGoesToItsService() {
        assertRoute("POST", "/api/v1/telemetry/batches", "telemetry:write", "http://ingest");
        assertRoute("GET", "/api/v1/drivers/d-1/score", "score:read", "http://scoring");
        assertRoute("GET", "/api/v1/drivers/d-1/premium", "premium:read", "http://rating");
        assertRoute("GET", "/api/v1/drivers/d-1/rewards", "rewards:read", "http://rewards");
        assertRoute("GET", "/api/v1/drivers/d-1/claims", "claims:read", "http://claims");
        assertRoute("GET", "/api/v1/claims/c-1", "claims:read", "http://claims");
    }

    @Test
    void unknownPathsAndMethodsAreNotRouted() {
        assertThat(routes.find("GET", "/api/v1/telemetry/batches")).isEmpty();
        assertThat(routes.pathKnown("/api/v1/telemetry/batches")).isTrue();
        assertThat(routes.find("GET", "/api/v1/drivers/d-1")).isEmpty();
        assertThat(routes.pathKnown("/api/v1/drivers/d-1")).isFalse();
        assertThat(routes.find("GET", "/api/v1/drivers/d-1/score/extra")).isEmpty();
        assertThat(routes.find("GET", "/api/actuator/prometheus")).isEmpty();
    }

    @Test
    void downstreamPathDropsTheApiPrefix() {
        assertThat(Routes.downstreamPath("/api/v1/drivers/d-1/score")).isEqualTo("/v1/drivers/d-1/score");
    }

    private void assertRoute(String method, String path, String scope, String base) {
        var route = routes.find(method, path).orElseThrow();
        assertThat(route.scope()).isEqualTo(scope);
        assertThat(route.baseUrl()).isEqualTo(base);
    }
}
