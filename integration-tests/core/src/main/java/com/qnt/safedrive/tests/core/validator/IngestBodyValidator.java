package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.IngestResponse;

/** The body of the ingest API: accepted, duplicate, conflict and invalid share this shape. */
public class IngestBodyValidator {

    private final IngestResponse actual;

    IngestBodyValidator(IngestResponse actual) {
        this.actual = actual;
    }

    public IngestBodyValidator hasStatus(String status) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        return this;
    }

    public IngestBodyValidator hasBatchId(String batchId) {
        assertThat(actual.batchId()).as("body batchId").isEqualTo(batchId);
        return this;
    }

    public IngestBodyValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("body correlationId").isEqualTo(correlationId);
        return this;
    }

    /** The server answered with a correlation ID (sent by us or generated). */
    public IngestBodyValidator hasCorrelationId() {
        assertThat(actual.correlationId()).as("body correlationId").isNotBlank();
        return this;
    }

    public IngestBodyValidator hasErrors() {
        assertThat(actual.errors()).as("errors").isNotEmpty();
        return this;
    }

    public IngestBodyValidator hasErrorContaining(String text) {
        assertThat(actual.errors()).as("errors").anyMatch(error -> error.contains(text));
        return this;
    }

    public IngestBodyValidator hasAcceptedSamples(int count) {
        assertThat(actual.acceptedSamples()).as("acceptedSamples").isEqualTo(count);
        return this;
    }

    public IngestBodyValidator hasNoRejectedSamples() {
        assertThat(actual.rejectedSamples()).as("rejectedSamples").isNullOrEmpty();
        return this;
    }

    /** The sample at {@code index} was dropped, and the reason contains the text. */
    public IngestBodyValidator hasRejectedSample(int index, String reasonContaining) {
        assertThat(actual.rejectedSamples()).as("rejectedSamples").anySatisfy(rejected -> {
            assertThat(rejected.path("index").asInt(-1)).as("rejected index").isEqualTo(index);
            assertThat(rejected.path("reason").asString()).as("rejected reason").contains(reasonContaining);
        });
        return this;
    }

    public IngestBodyValidator hasRejectedSampleCount(int count) {
        assertThat(actual.rejectedSamples()).as("rejectedSamples").hasSize(count);
        return this;
    }
}
