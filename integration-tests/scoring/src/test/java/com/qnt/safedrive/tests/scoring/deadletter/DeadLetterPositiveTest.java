package com.qnt.safedrive.tests.scoring.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aTripCompletedEvent;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
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

    @Test(groups = Groups.REGRESSION, description = "A valid event sitting in the dead-letter topic is replayed to trip.completed and the driver is scored")
    @Story("Replay")
    public void replayedValidEventIsScored() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        Envelope event = aTripCompletedEvent(trip, correlationId);
        publisher().send(DEAD_LETTER_TOPIC, trip.driverId(), event);
        assertThat(scoreUpdatedFor(trip.driverId(), correlationId)).as("before replay").isEmpty();

        replayer().replay(DEAD_LETTER_TOPIC);

        TopicReader.Message updated = awaitScoreUpdated(trip.driverId(), correlationId, 1);
        Validator.with(updated)
                .hasKey(trip.driverId())
                .envelope()
                .hasCorrelationId(correlationId)
                .matchesContract()
                .scoreUpdatedPayload()
                .hasDriverId(trip.driverId())
                .hasScore(100.0);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Replay puts a dead letter back on trip.completed with the same key, event ID and correlation ID; if it still fails it is dead-lettered again")
    @Story("Replay")
    public void replayKeepsTheEventAndDeadLettersItAgainIfItStillFails() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        Envelope event = aTripCompletedEvent(trip, correlationId).withType(EventTypes.SCORE_UPDATED);
        publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), event);
        awaitDeadLetter(trip.driverId());

        replayer().replay(DEAD_LETTER_TOPIC);

        List<TopicReader.Message> onTopic = tracer.awaitEvents(Topics.TRIP_COMPLETED, correlationId, 2);
        assertThat(onTopic)
                .as("the original and its replay")
                .extracting(m -> m.envelope().eventId())
                .containsOnly(event.eventId());
        assertThat(onTopic).extracting(TopicReader.Message::key).containsOnly(trip.driverId());
        assertThat(tracer.awaitRawByKey(DEAD_LETTER_TOPIC, trip.driverId(), 2))
                .as("dead letters for key %s after the replay failed again", trip.driverId())
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(scoreUpdatedFor(trip.driverId(), correlationId)).as("score.updated for an event that still fails").isEmpty();
    }
}
