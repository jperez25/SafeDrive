package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;

/** The payload of a {@code trip.raw} event: the batch that was sent to ingest. */
public class TripRawPayloadValidator {

    private final Batch actual;

    TripRawPayloadValidator(Batch actual) {
        this.actual = actual;
    }

    public TripRawPayloadValidator hasBatchId(String batchId) {
        assertThat(actual.batchId()).as("batchId").isEqualTo(batchId);
        return this;
    }

    public TripRawPayloadValidator hasTripId(String tripId) {
        assertThat(actual.tripId()).as("tripId").isEqualTo(tripId);
        return this;
    }

    /** Every field of the payload equals the batch that was sent. */
    public TripRawPayloadValidator isEqualTo(Batch sent) {
        assertThat(actual).as("published batch").isEqualTo(sent);
        return this;
    }
}
