package com.qnt.safedrive.tests.core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Environment settings from {@code environment.<env>.json} on the classpath; {@code -Denv=<env>}
 * picks the file (default {@code local}). Files hold no secrets.
 *
 * <pre>
 * { "name": "local",
 *   "services": { "telemetry-ingest": "http://localhost:8081", ... },
 *   "namespace": "safedrive", "kafkaBootstrapServers": "localhost:9094",
 *   "waitTimeoutSeconds": 30, "pollMillis": 500 }
 * </pre>
 */
public final class TestEnvironment {

    private static TestEnvironment current;

    private final JsonNode json;

    private TestEnvironment(JsonNode json) {
        this.json = json;
    }

    public static synchronized TestEnvironment current() {
        if (current == null) {
            current = load(System.getProperty("env", "local"));
        }
        return current;
    }

    public static TestEnvironment load(String env) {
        String resource = "/environment." + env + ".json";
        try (InputStream in = TestEnvironment.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("environment file not found on classpath: " + resource);
            }
            return new TestEnvironment(Json.tree(new String(in.readAllBytes())));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }

    public String name() {
        return text("name", "local");
    }

    /** Base URL of a service, e.g. {@code http://localhost:8081}. */
    public String serviceUrl(String service) {
        JsonNode url = json.path("services").path(service);
        if (!url.isString()) {
            throw new IllegalStateException("no URL for service '" + service + "' in environment " + name());
        }
        return url.asString();
    }

    public String namespace() {
        return text("namespace", "safedrive");
    }

    /** Kafka as seen from the machine that runs the tests (through {@code port-forward.sh}). */
    public String kafkaBootstrapServers() {
        return text("kafkaBootstrapServers", "localhost:9094");
    }

    public Duration waitTimeout() {
        return Duration.ofSeconds(json.path("waitTimeoutSeconds").asLong(30));
    }

    public Duration pollInterval() {
        return Duration.ofMillis(json.path("pollMillis").asLong(500));
    }

    /** Directory with the event JSON Schemas (relative to the module that runs the tests). */
    public Path contractsDir() {
        return Path.of(text("contractsDir", "../../docs/contracts/events"));
    }

    /** Directory with the platform scripts such as {@code replay-dlt.sh} (relative to the module that runs the tests). */
    public Path scriptsDir() {
        return Path.of(text("scriptsDir", "../../platform/scripts"));
    }

    public Map<String, String> services() {
        return Json.MAPPER.convertValue(json.path("services"), Json.MAPPER.constructType(Map.class));
    }

    private String text(String field, String fallback) {
        JsonNode node = json.path(field);
        return node.isString() ? node.asString() : fallback;
    }
}
