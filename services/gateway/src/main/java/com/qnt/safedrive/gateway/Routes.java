package com.qnt.safedrive.gateway;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The only paths the gateway proxies. Anything else is 404. Each route needs one scope. */
class Routes {

    record Route(String method, Pattern path, String scope, String baseUrl) {}

    private static final String ID = "[^/]+";
    private final List<Route> routes;

    Routes(GatewayProperties p) {
        routes = List.of(
                new Route("POST", Pattern.compile("/api/v1/telemetry/batches"), "telemetry:write", p.ingestUrl()),
                new Route("GET", Pattern.compile("/api/v1/drivers/" + ID + "/score"), "score:read", p.scoringUrl()),
                new Route("GET", Pattern.compile("/api/v1/drivers/" + ID + "/premium"), "premium:read", p.ratingUrl()),
                new Route("GET", Pattern.compile("/api/v1/drivers/" + ID + "/rewards"), "rewards:read", p.rewardsUrl()),
                new Route("GET", Pattern.compile("/api/v1/drivers/" + ID + "/claims"), "claims:read", p.claimsUrl()),
                new Route("GET", Pattern.compile("/api/v1/claims/" + ID), "claims:read", p.claimsUrl()));
    }

    /** True when the path exists for some method (to tell 405 from 404). */
    boolean pathKnown(String path) {
        return routes.stream().anyMatch(r -> r.path().matcher(path).matches());
    }

    Optional<Route> find(String method, String path) {
        return routes.stream()
                .filter(r -> r.method().equals(method) && r.path().matcher(path).matches())
                .findFirst();
    }

    /** The service path: the gateway path without the "/api" prefix. */
    static String downstreamPath(String path) {
        return path.substring("/api".length());
    }
}
