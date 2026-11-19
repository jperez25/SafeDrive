package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import java.time.Instant;
import org.assertj.core.data.Offset;

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

    /** Compared as instants, so the text format of the timestamp does not matter. */
    public TripCompletedPayloadValidator hasStartedAt(String timestamp) {
        assertThat(Instant.parse(actual.startedAt())).as("startedAt").isEqualTo(Instant.parse(timestamp));
        return this;
    }

    public TripCompletedPayloadValidator hasEndedAt(String timestamp) {
        assertThat(Instant.parse(actual.endedAt())).as("endedAt").isEqualTo(Instant.parse(timestamp));
        return this;
    }

    public TripCompletedPayloadValidator hasDurationSeconds(long seconds) {
        assertThat(actual.durationSeconds()).as("durationS").isEqualTo(seconds);
        return this;
    }

    public TripCompletedPayloadValidator hasAverageSpeed(double kilometersPerHour) {
        assertThat(actual.averageSpeedKilometersPerHour()).as("avgSpeedKmh").isCloseTo(kilometersPerHour, Offset.offset(0.01));
        return this;
    }

    public TripCompletedPayloadValidator hasMaximumSpeed(double kilometersPerHour) {
        assertThat(actual.maximumSpeedKilometersPerHour()).as("maxSpeedKmh").isEqualTo(kilometersPerHour);
        return this;
    }

    public TripCompletedPayloadValidator hasPositiveDistance() {
        assertThat(actual.distanceKilometers()).as("distanceKm").isPositive();
        return this;
    }

    /** The cumulative count of this kind of driving event over every batch of the trip so far. */
    public TripCompletedPayloadValidator hasEventCount(String kind, int count) {
        assertThat(actual.eventCounts()).as("eventCounts").containsEntry(kind, count);
        return this;
    }

    public TripCompletedPayloadValidator hasNoDrivingEvents() {
        assertThat(actual.eventCounts()).as("eventCounts").isNotEmpty().allSatisfy((kind, count) -> assertThat(count).as(kind).isZero());
        return this;
    }
}
