package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/** The ingest body checks accept a matching response and fail on a different one. */
public class IngestBodyValidatorTest {

    private static final String BODY = """
            {"status":"accepted","batchId":"b-1","correlationId":"c-1","acceptedSamples":3,
             "rejectedSamples":[{"index":1,"reason":"lat out of range"}]}""";

    @Test
    public void matchingResponsePasses() {
        Validator.with(response(Http.ACCEPTED, BODY))
                .hasStatus(Http.ACCEPTED)
                .hasCorrelationHeader("c-1")
                .ingestBody()
                .hasStatus("accepted")
                .hasBatchId("b-1")
                .hasCorrelationId("c-1")
                .hasAcceptedSamples(3)
                .hasRejectedSampleCount(1)
                .hasRejectedSample(1, "lat");
    }

    @Test
    public void wrongRejectedIndexFails() {
        assertThatThrownBy(() -> Validator.with(response(Http.ACCEPTED, BODY)).ingestBody().hasRejectedSample(2, "lat"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void wrongAcceptedCountFails() {
        assertThatThrownBy(() -> Validator.with(response(Http.ACCEPTED, BODY)).ingestBody().hasAcceptedSamples(4))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("acceptedSamples");
    }

    @Test
    public void noRejectedSamplesFailsWhenThereAreSome() {
        assertThatThrownBy(() -> Validator.with(response(Http.ACCEPTED, BODY)).ingestBody().hasNoRejectedSamples())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void errorTextIsMatched() {
        Response invalid = response(Http.BAD_REQUEST, "{\"status\":\"invalid\",\"errors\":[\"tripId is required\"]}");
        Validator.with(invalid).hasStatus(Http.BAD_REQUEST).ingestBody().hasErrorContaining("tripId");
        assertThatThrownBy(() -> Validator.with(invalid).ingestBody().hasErrorContaining("driverId"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void wrongStatusFails() {
        assertThatThrownBy(() -> Validator.with(response(Http.ACCEPTED, BODY)).hasStatus(Http.OK))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HTTP status");
    }

    private static Response response(int status, String body) {
        return new ResponseBuilder()
                .setStatusCode(status)
                .setHeader(Http.CORRELATION_HEADER, "c-1")
                .setBody(body)
                .build();
    }
}
