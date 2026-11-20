package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Step;

/** The envelope of an event; payload children are entered by event type, e.g. {@link #tripRawPayload()}. */
public class EventValidator {

    private final Envelope actual;

    EventValidator(Envelope actual) {
        this.actual = actual;
    }

    @Step("Has type {type}")
    public EventValidator hasType(String type) {
        assertThat(actual.type()).as("type").isEqualTo(type);
        return this;
    }

    @Step("Has source {source}")
    public EventValidator hasSource(String source) {
        assertThat(actual.source()).as("source").isEqualTo(source);
        return this;
    }

    @Step("Has event id")
    public EventValidator hasEventId() {
        assertThat(actual.eventId()).as("eventId").isNotBlank();
        return this;
    }

    @Step("Has correlation id {correlationId}")
    public EventValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("correlationId").isEqualTo(correlationId);
        return this;
    }

    /** The whole envelope, payload included, is valid against its JSON Schema in docs/contracts. */
    @Step("Matches contract")
    public EventValidator matchesContract() {
        EventContract.assertValid(actual);
        return this;
    }

    @Step("Validate trip completed payload")
    public TripCompletedPayloadValidator tripCompletedPayload() {
        return new TripCompletedPayloadValidator(Json.convert(actual.payload(), TripCompleted.class));
    }

    @Step("Validate driving event payload")
    public DrivingEventPayloadValidator drivingEventPayload() {
        return new DrivingEventPayloadValidator(Json.convert(actual.payload(), DrivingEvent.class));
    }

    @Step("Validate score updated payload")
    public ScoreUpdatedPayloadValidator scoreUpdatedPayload() {
        return new ScoreUpdatedPayloadValidator(Json.convert(actual.payload(), ScoreUpdated.class));
    }

    @Step("Validate premium adjusted payload")
    public PremiumAdjustedPayloadValidator premiumAdjustedPayload() {
        return new PremiumAdjustedPayloadValidator(Json.convert(actual.payload(), PremiumAdjusted.class));
    }

    @Step("Validate trip raw payload")
    public TripRawPayloadValidator tripRawPayload() {
        return new TripRawPayloadValidator(Json.convert(actual.payload(), Batch.class));
    }
}
