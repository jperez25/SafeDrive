package com.qnt.safedrive.tests.policyrating.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aScoreUpdatedEvent;
import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdated;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Bands;
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

    @Test(groups = Groups.REGRESSION, description = "A valid event sitting in the dead-letter topic is replayed to score.updated and the driver is rated")
    @Story("Replay")
    public void replayedValidEventIsRated() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        Envelope event = aScoreUpdatedEvent(score, correlationId);
        publisher().send(DEAD_LETTER_TOPIC, score.driverId(), event);
        assertThat(premiumAdjustedFor(score.driverId(), correlationId)).as("before replay").isEmpty();

        replayer().replay(DEAD_LETTER_TOPIC);

        TopicReader.Message adjusted = awaitPremiumAdjusted(score.driverId(), correlationId, 1);
        Validator.with(adjusted)
                .hasKey(score.driverId())
                .envelope()
                .hasCorrelationId(correlationId)
                .matchesContract()
                .premiumAdjustedPayload()
                .hasDriverId(score.driverId())
                .hasBand(Bands.EXCELLENT)
                .hasNewPremium(96.00);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Replay puts a dead letter back on score.updated with the same key, event ID and correlation ID; if it still fails it is dead-lettered again")
    @Story("Replay")
    public void replayKeepsTheEventAndDeadLettersItAgainIfItStillFails() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        Envelope event = aScoreUpdatedEvent(score, correlationId).withType(EventTypes.TRIP_COMPLETED);
        publisher().send(Topics.SCORE_UPDATED, score.driverId(), event);
        awaitDeadLetter(score.driverId());

        replayer().replay(DEAD_LETTER_TOPIC);

        List<TopicReader.Message> onTopic = tracer.awaitEvents(Topics.SCORE_UPDATED, correlationId, 2);
        assertThat(onTopic)
                .as("the original and its replay")
                .extracting(m -> m.envelope().eventId())
                .containsOnly(event.eventId());
        assertThat(onTopic).extracting(TopicReader.Message::key).containsOnly(score.driverId());
        assertThat(tracer.awaitRawByKey(DEAD_LETTER_TOPIC, score.driverId(), 2))
                .as("dead letters for key %s after the replay failed again", score.driverId())
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(premiumAdjustedFor(score.driverId(), correlationId)).as("premium.adjusted for an event that still fails").isEmpty();
    }
}
