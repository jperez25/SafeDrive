package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Waiter;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Base of the REST clients: base URL, JSON, Allure logging (secrets masked) and the correlation header. */
public abstract class ServiceClient {

    private static final int POLL_ATTEMPTS = 3;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    private final String baseUrl;

    protected ServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    protected RequestSpecification request(String correlationId) {
        return request(correlationId, Map.of());
    }

    /** Same as {@link #request(String)} plus extra headers for this request only, e.g. auth or idempotency. */
    protected RequestSpecification request(String correlationId, Map<String, String> headers) {
        RequestSpecification spec = RestAssured.given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .filter(new RedactingAllureFilter());
        if (correlationId != null) {
            spec.header(Http.CORRELATION_HEADER, correlationId);
        }
        headers.forEach(spec::header);
        return spec;
    }

    // Two ways to call, chosen by the test. Raw: one call, no retry, for negative tests that expect a rejection
    // (a retry would only hide it). WithRetry: for calls that must succeed, so a service that is down or
    // restarting does not fail the test.

    protected Response get(String path, String correlationId) {
        return request(correlationId).get(path);
    }

    protected Response post(String path, String body, String correlationId) {
        return request(correlationId).body(body).post(path);
    }

    protected Response getWithRetry(String path, String correlationId) {
        return getWithRetry(path, ServiceClient::isServerUp, correlationId);
    }

    /** Like {@link #getWithRetry(String, String)} but also retries until {@code done} accepts, for late results. */
    protected Response getWithRetry(String path, Predicate<Response> done, String correlationId) {
        return retry("GET " + path, () -> get(path, correlationId), done);
    }

    protected Response postWithRetry(String path, String body, String correlationId) {
        return retry("POST " + path, () -> post(path, body, correlationId), ServiceClient::isServerUp);
    }

    /** Anything below 500 is an answer from the service (even a 4xx); 5xx or no answer means try again. */
    private static boolean isServerUp(Response response) {
        return response.statusCode() < 500;
    }

    /**
     * Tries 3 times, 1 second apart; every try is attached to Allure. A thrown error (connection refused) counts
     * as a failed try. Throws {@link Waiter.WaitTimeoutException} if no response is accepted.
     */
    private Response retry(String description, Supplier<Response> call, Predicate<Response> accept) {
        Waiter waiter = new Waiter(POLL_INTERVAL.multipliedBy(POLL_ATTEMPTS), POLL_INTERVAL);
        return waiter.untilAttempts(description, POLL_ATTEMPTS, () -> {
            Response response = call.get();
            return accept.test(response) ? Optional.of(response) : Optional.empty();
        });
    }
}
