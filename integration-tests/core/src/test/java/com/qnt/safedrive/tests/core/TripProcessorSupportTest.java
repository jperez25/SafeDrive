package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.factory.BatchFactory;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.testng.annotations.Test;

/** The helpers added for the trip-processor tests: they accept the right thing and can fail. */
public class TripProcessorSupportTest {

    private static final Path EXAMPLES = Path.of("..", "..", "docs", "contracts", "events", "examples");

    @Test
    public void eventIdIsAUuid() {
        assertThat(UUID.fromString(Ids.event())).isNotNull();
        assertThat(Ids.event()).isNotEqualTo(Ids.event());
    }

    @Test
    public void tripRawEnvelopeIsValidAndGetsAFreshEventIdEachTime() {
        Batch batch = BatchFactory.aBatch();
        Envelope first = EnvelopeFactory.aTripRawEvent(batch, "corr-1");
        Envelope second = EnvelopeFactory.aTripRawEvent(batch, "corr-1");

        Validator.with(new TopicReader.Message("trip.raw", batch.tripId(), first))
                .envelope().hasCorrelationId("corr-1").hasEventId().matchesContract().tripRawPayload().isEqualTo(batch);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    public void harshAccelerationAndDistractionBatchesChangeOnlyTheEventSample() {
        Sample calm = BatchFactory.aBatch().samples().get(2);
        Sample accelerating = BatchFactory.aHarshAccelerationBatch().samples().get(2);
        Sample distracted = BatchFactory.aDistractedBatch().samples().get(2);

        assertThat(accelerating.accelerationMetersPerSecondSquared()).isEqualTo(5.0);
        assertThat(accelerating.timestamp()).isEqualTo(calm.timestamp());
        assertThat(distracted.phoneInUse()).isTrue();
        assertThat(distracted.speedKilometersPerHour()).isEqualTo(60.0);
        assertThat(BatchFactory.aDistractedBatch().samples().get(1).phoneInUse()).isFalse();
    }

    @Test
    public void drivingEventAndTripCompletedChildrenCheckTheirFields() throws Exception {
        Envelope event = Json.read(Files.readString(EXAMPLES.resolve("driving.event.v1.json")), Envelope.class);
        TopicReader.Message message = new TopicReader.Message("driving.event", "d-1", event);
        String kind = event.payload().path("kind").asString();

        Validator.with(message).envelope().matchesContract().drivingEventPayload().hasKind(kind);
        assertThatThrownBy(() -> Validator.with(message).envelope().drivingEventPayload().hasKind("other"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("kind");

        Envelope completed = Json.read(Files.readString(EXAMPLES.resolve("trip.completed.v1.json")), Envelope.class);
        TopicReader.Message completedMessage = new TopicReader.Message("trip.completed", "d-1", completed);
        assertThatThrownBy(() -> Validator.with(completedMessage).envelope().tripCompletedPayload().hasDurationSeconds(-1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("durationS");
        assertThatThrownBy(() -> Validator.with(completedMessage).envelope().tripCompletedPayload().hasEventCount("hard_brake", 99))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("eventCounts");
    }

    @Test
    public void rawMessageValidatorComparesTheValueUntouched() {
        TopicReader.RawMessage message = new TopicReader.RawMessage("t.dlt", "k", "not json");
        Validator.with(message).hasTopic("t.dlt").hasKey("k").hasValue("not json");
        assertThatThrownBy(() -> Validator.with(message).hasValue("changed"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("value");
    }
}
