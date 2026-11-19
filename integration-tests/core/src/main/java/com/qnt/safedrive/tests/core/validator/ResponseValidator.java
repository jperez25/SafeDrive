package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.IngestResponse;
import com.qnt.safedrive.tests.core.model.PremiumResponse;
import com.qnt.safedrive.tests.core.model.ScoreResponse;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Json;
import io.restassured.response.Response;
import tools.jackson.core.JacksonException;

/**
 * Master validator for an HTTP response: only what is true of any response (status, headers).
 * Body checks live in child validators, entered through a named method such as {@link #ingestBody()}.
 */
public class ResponseValidator {

    private final Response response;

    ResponseValidator(Response response) {
        this.response = response;
    }

    public ResponseValidator hasStatus(int status) {
        assertThat(response.statusCode()).as("HTTP status, body: %s", response.asString()).isEqualTo(status);
        return this;
    }

    public ResponseValidator hasHeader(String name, String value) {
        assertThat(response.getHeader(name)).as("header %s", name).isEqualTo(value);
        return this;
    }

    /** The response carries a correlation ID header. */
    public ResponseValidator hasCorrelationHeader() {
        assertThat(response.getHeader(Http.CORRELATION_HEADER)).as("correlation header").isNotBlank();
        return this;
    }

    /** The response echoes the correlation ID that was sent. */
    public ResponseValidator hasCorrelationHeader(String expected) {
        assertThat(response.getHeader(Http.CORRELATION_HEADER)).as("correlation header").isEqualTo(expected);
        return this;
    }

    public HealthBodyValidator healthBody() {
        return new HealthBodyValidator(Json.tree(response.asString()));
    }

    public IngestBodyValidator ingestBody() {
        return new IngestBodyValidator(parse(IngestResponse.class));
    }

    /** The score API body: the score, or the not-found answer for a driver without one. */
    public ScoreBodyValidator scoreBody() {
        return new ScoreBodyValidator(parse(ScoreResponse.class));
    }

    /** The premium API body: the current rating, or the not-found answer for a driver without one. */
    public PremiumBodyValidator premiumBody() {
        return new PremiumBodyValidator(parse(PremiumResponse.class));
    }

    <B> B parse(Class<B> type) {
        try {
            return Json.read(response.asString(), type);
        } catch (JacksonException e) {
            throw new AssertionError("Body is not a " + type.getSimpleName() + ": " + response.asString(), e);
        }
    }
}
