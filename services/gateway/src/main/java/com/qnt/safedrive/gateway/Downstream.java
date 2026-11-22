package com.qnt.safedrive.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** Plain HTTP call to a service. Passes status and body through; maps failures to 502 and 504. */
class Downstream {

    record Result(int status, String contentType, byte[] body) {}

    private final HttpClient client;
    private final Duration requestTimeout;

    Downstream(int connectTimeoutMs, int requestTimeoutMs) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    Result call(String method, String url, String contentType, byte[] body, String correlationId) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("X-Correlation-Id", correlationId);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<byte[]> r = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Result(r.statusCode(), r.headers().firstValue("Content-Type").orElse(null), r.body());
        } catch (HttpTimeoutException e) {
            return new Result(504, "application/json", error("gateway_timeout", correlationId));
        } catch (IOException e) {
            return new Result(502, "application/json", error("bad_gateway", correlationId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(502, "application/json", error("bad_gateway", correlationId));
        }
    }

    private static byte[] error(String status, String correlationId) {
        return ("{\"status\":\"" + status + "\",\"correlationId\":\"" + correlationId + "\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
