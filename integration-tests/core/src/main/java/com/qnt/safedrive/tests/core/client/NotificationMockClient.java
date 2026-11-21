package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The admin side of the mocked notification provider (WireMock, {@code platform/services/notification-mock}). Reads
 * the notifications it received and injects failures. Every stub and every query is scoped to one driver ID in the
 * request body, so parallel tests never see or break each other.
 */
public class NotificationMockClient extends ServiceClient {

    private static final String NOTIFICATIONS = "/v1/notifications";

    public NotificationMockClient(String baseUrl) {
        super(baseUrl);
    }

    /** Notifications the provider received for this driver, as sent (bodies parsed, headers included). */
    @Step("Read notifications received for driver {driverId}")
    public List<JsonNode> notificationsFor(String driverId) {
        String query = Json.write(Map.of(
                "method", "POST",
                "url", NOTIFICATIONS,
                "bodyPatterns", List.of(Map.of("matchesJsonPath", driverFilter(driverId)))));
        Response response = request(null).body(query).post("/__admin/requests/find");
        JsonNode found = Json.tree(response.asString()).path("requests");
        return found.valueStream().toList();
    }

    /** Makes the provider answer {@code status} to this driver's notifications. Returns the stub ID to remove later. */
    @Step("Make the provider answer {status} for driver {driverId}")
    public String failFor(String driverId, int status) {
        String stub = Json.write(Map.of(
                "priority", 1,
                "request", Map.of(
                        "method", "POST",
                        "urlPath", NOTIFICATIONS,
                        "bodyPatterns", List.of(Map.of("matchesJsonPath", driverFilter(driverId)))),
                "response", Map.of("status", status)));
        Response response = request(null).body(stub).post("/__admin/mappings");
        return Json.tree(response.asString()).path("id").asString();
    }

    /** Makes the provider answer {@code status} to this driver after {@code delayMillis} (a slow provider). */
    @Step("Make the provider answer {status} after {delayMillis} ms for driver {driverId}")
    public String delayFor(String driverId, int status, int delayMillis) {
        String stub = Json.write(Map.of(
                "priority", 1,
                "request", Map.of(
                        "method", "POST",
                        "urlPath", NOTIFICATIONS,
                        "bodyPatterns", List.of(Map.of("matchesJsonPath", driverFilter(driverId)))),
                "response", Map.of("status", status, "fixedDelayMilliseconds", delayMillis)));
        Response response = request(null).body(stub).post("/__admin/mappings");
        return Json.tree(response.asString()).path("id").asString();
    }

    /** Removes a stub made by {@link #failFor}; the default stub then accepts the driver's notifications again. */
    @Step("Remove stub {stubId}")
    public void removeStub(String stubId) {
        request(null).delete("/__admin/mappings/" + stubId);
    }

    private static String driverFilter(String driverId) {
        return "$[?(@.driverId == '" + driverId + "')]";
    }
}
