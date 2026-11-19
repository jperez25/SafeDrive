package com.qnt.safedrive.tests.tripprocessor.deadletter;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aTripRawEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Replay with {@code platform/scripts/replay-dlt.sh}. Needs kubectl on the PATH and the Kafka forward. The replay
 * also moves other tests' new dead letters; those fail again and return to the dead-letter topic, which their
 * "at least" checks tolerate.
 */
public class DeadLetterPositiveTest extends AbstractDeadLetterTest {

    @Test(groups = Groups.REGRESSION, description = "A valid event sitting in the dead-letter topic is replayed to trip.raw and its trip completes")
    @Story("Replay")
    public void replayedValidEventCompletesItsTrip() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        Envelope event = aTripRawEvent(batch, correlationId);
        publisher().send(DEAD_LETTER_TOPIC, batch.tripId(), event);
        assertThat(tripCompletedFor(batch, correlationId)).as("before replay").isEmpty();

        replayer().replay(DEAD_LETTER_TOPIC);

        TopicReader.Message completed = awaitTripCompleted(batch, correlationId, 1);
        Validator.with(completed)
                .hasKey(batch.driverId())
                .envelope()
                .hasCorrelationId(correlationId)
                .matchesContract()
                .tripCompletedPayload()
                .hasTripId(batch.tripId())
                .hasRevision(1);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Replay puts a dead letter back on trip.raw with the same key, event ID and correlation ID; if it still fails it is dead-lettered again")
    @Story("Replay")
    public void replayKeepsTheEventAndDeadLettersItAgainIfItStillFails() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        Envelope event = aTripRawEvent(batch, correlationId).withType(EventTypes.TRIP_COMPLETED);
        publisher().send(Topics.TRIP_RAW, batch.tripId(), event);
        awaitDeadLetter(batch.tripId());

        replayer().replay(DEAD_LETTER_TOPIC);

        List<TopicReader.Message> onTripRaw = tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 2);
        assertThat(onTripRaw)
                .as("the original and its replay")
                .extracting(m -> m.envelope().eventId())
                .containsOnly(event.eventId());
        assertThat(onTripRaw).extracting(TopicReader.Message::key).containsOnly(batch.tripId());
        assertThat(tracer.awaitRawByKey(DEAD_LETTER_TOPIC, batch.tripId(), 2))
                .as("dead letters for key %s after the replay failed again", batch.tripId())
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(tripCompletedFor(batch, correlationId)).as("trip.completed for an event that still fails").isEmpty();
    }
}
