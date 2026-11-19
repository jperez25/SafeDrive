package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.TripCompleted;

/** The payload of a {@code trip.completed} event. */
public class TripCompletedPayloadValidator {

    private final TripCompleted actual;

    TripCompletedPayloadValidator(TripCompleted actual) {
        this.actual = actual;
    }

    public TripCompletedPayloadValidator hasTripId(String tripId) {
        assertThat(actual.tripId()).as("tripId").isEqualTo(tripId);
        return this;
    }

    public TripCompletedPayloadValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("driverId").isEqualTo(driverId);
        return this;
    }

    public TripCompletedPayloadValidator hasRevision(int revision) {
        assertThat(actual.revision()).as("revision").isEqualTo(revision);
        return this;
    }

    public TripCompletedPayloadValidator hasNonNegativeDuration() {
        assertThat(actual.durationSeconds()).as("durationSeconds").isGreaterThanOrEqualTo(0);
        return this;
    }
}
