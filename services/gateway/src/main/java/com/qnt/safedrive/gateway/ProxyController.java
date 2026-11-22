package com.qnt.safedrive.gateway;

import com.qnt.safedrive.commons.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Checks the token and its scope, then forwards the call unchanged. No ownership check (PoC). */
@RestController
class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final Routes routes;
    private final JwtService jwt;
    private final Downstream downstream;
    private final int maxBodyBytes;

    ProxyController(Routes routes, JwtService jwt, Downstream downstream, GatewayProperties props) {
        this.routes = routes;
        this.jwt = jwt;
        this.downstream = downstream;
        this.maxBodyBytes = props.maxBodyBytes();
    }

    @RequestMapping("/api/**")
    ResponseEntity<?> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        var route = routes.find(method, path);
        if (route.isEmpty()) {
            return routes.pathKnown(path)
                    ? AuthController.error(405, "method_not_allowed", "method not allowed")
                    : AuthController.error(404, "not_found", "unknown path");
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.info("{} {} rejected: no bearer token", method, route.get().scope());
            return AuthController.error(401, "unauthorized", "missing bearer token");
        }
        var claims = jwt.verify(auth.substring("Bearer ".length()).trim());
        if (claims.isEmpty()) {
            log.info("{} {} rejected: invalid token", method, route.get().scope());
            return AuthController.error(401, "unauthorized", "invalid or expired token");
        }
        if (!claims.get().scopes().contains(route.get().scope())) {
            log.info("{} {} rejected: missing scope", method, route.get().scope());
            return AuthController.error(403, "forbidden", "missing scope " + route.get().scope());
        }
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            return AuthController.error(413, "payload_too_large", "request body too large");
        }
        String query = request.getQueryString();
        String url = route.get().baseUrl() + Routes.downstreamPath(path) + (query == null ? "" : "?" + query);
        var result = downstream.call(method, url, request.getContentType(), body, CorrelationId.current());
        log.info("{} {} proxied, status {}", method, route.get().scope(), result.status());
        ResponseEntity.BodyBuilder out = ResponseEntity.status(result.status());
        if (result.contentType() != null) {
            out.header(HttpHeaders.CONTENT_TYPE, result.contentType());
        }
        return out.body(result.body());
    }
}
