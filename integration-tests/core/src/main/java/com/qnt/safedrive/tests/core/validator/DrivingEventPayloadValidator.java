package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Sample;
import io.qameta.allure.Step;
import java.time.Instant;

/** The payload of a {@code driving.event} event. */
public class DrivingEventPayloadValidator {

    private final DrivingEvent actual;

    DrivingEventPayloadValidator(DrivingEvent actual) {
        this.actual = actual;
    }

    @Step("Has trip id {tripId}")
    public DrivingEventPayloadValidator hasTripId(String tripId) {
        assertThat(actual.tripId()).as("tripId").isEqualTo(tripId);
        return this;
    }

    @Step("Has driver id {driverId}")
    public DrivingEventPayloadValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("driverId").isEqualTo(driverId);
        return this;
    }

    @Step("Has kind {kind}")
    public DrivingEventPayloadValidator hasKind(String kind) {
        assertThat(actual.kind()).as("kind").isEqualTo(kind);
        return this;
    }

    @Step("Has severity {severity}")
    public DrivingEventPayloadValidator hasSeverity(String severity) {
        assertThat(actual.severity()).as("severity").isEqualTo(severity);
        return this;
    }

    /** The event is stamped with this sample: same time (as an instant, not as text) and same position. */
    @Step("Validate is stamped with")
    public DrivingEventPayloadValidator isStampedWith(Sample sample) {
        assertThat(Instant.parse(actual.timestamp())).as("timestamp").isEqualTo(Instant.parse(sample.timestamp()));
        assertThat(actual.latitude()).as("latitude").isEqualTo(sample.latitude());
        assertThat(actual.longitude()).as("longitude").isEqualTo(sample.longitude());
        return this;
    }
}
