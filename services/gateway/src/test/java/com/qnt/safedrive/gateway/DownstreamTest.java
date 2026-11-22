package com.qnt.safedrive.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DownstreamTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(int status, String body, long delayMs, AtomicReference<String> seen) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            if (seen != null) {
                seen.set(ex.getRequestMethod() + " " + ex.getRequestURI() + " cid=" + ex.getRequestHeaders().getFirst("X-Correlation-Id")
                        + " auth=" + ex.getRequestHeaders().getFirst("Authorization")
                        + " body=" + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void passesStatusBodyAndCorrelationIdThroughAndNeverForwardsTheToken() throws IOException {
        var seen = new AtomicReference<String>();
        String base = start(202, "{\"ok\":true}", 0, seen);
        var result = new Downstream(1000, 2000).call("POST", base + "/v1/telemetry/batches", "application/json",
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "cid-1");

        assertThat(result.status()).isEqualTo(202);
        assertThat(new String(result.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        assertThat(seen.get()).isEqualTo("POST /v1/telemetry/batches cid=cid-1 auth=null body={\"a\":1}");
    }

    @Test
    void passesErrorStatusesThrough() throws IOException {
        String base = start(404, "{\"status\":\"not_found\"}", 0, null);
        assertThat(new Downstream(1000, 2000).call("GET", base + "/x", null, new byte[0], "c").status()).isEqualTo(404);
    }

    @Test
    void slowServiceGives504() throws IOException {
        String base = start(200, "{}", 1500, null);
        assertThat(new Downstream(1000, 300).call("GET", base + "/x", null, new byte[0], "c").status()).isEqualTo(504);
    }

    @Test
    void serviceDownGives502() throws IOException {
        String base = start(200, "{}", 0, null);
        server.stop(0);
        assertThat(new Downstream(500, 500).call("GET", base + "/x", null, new byte[0], "c").status()).isEqualTo(502);
    }
}
