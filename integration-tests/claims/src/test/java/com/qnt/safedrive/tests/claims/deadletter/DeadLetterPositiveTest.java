package com.qnt.safedrive.tests.claims.deadletter;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aDrivingEventEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Replay with {@code platform/scripts/replay-dlt.sh}. Needs kubectl on the PATH and the Kafka forward. The replay
 * also moves other tests' new dead letters; those fail again and return to the dead-letter topic.
 */
public class DeadLetterPositiveTest extends AbstractDeadLetterTest {

    @Test(groups = Groups.REGRESSION, description = "A valid crash sitting in the dead-letter topic is replayed to driving.event and opens the claim")
    @Story("Replay")
    public void replayedValidCrashOpensTheClaim() {
        DrivingEvent crash = aCrashEvent();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation());
        publisher().send(DRIVING_EVENT_DEAD_LETTER, crash.driverId(), aDrivingEventEvent(crash, Ids.correlation()));
        assertThat(claimsOf(crash.driverId()).claims()).as("before replay").isEmpty();

        replayer().replay(DRIVING_EVENT_DEAD_LETTER);

        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);
        Validator.with(awaitNoMoreClaimsThan(1, crash.driverId())).claimsBody().hasClaimCount(1);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Replay puts a dead letter back on driving.event with the same key, event ID and correlation ID; if it still fails it is dead-lettered again")
    @Story("Replay")
    public void replayKeepsTheEventAndDeadLettersItAgainIfItStillFails() {
        DrivingEvent crash = aCrashEvent();
        String correlationId = Ids.correlation();
        Envelope event = aDrivingEventEvent(crash, correlationId).withType(EventTypes.SCORE_UPDATED);
        publisher().send(Topics.DRIVING_EVENT, crash.driverId(), event);
        awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, crash.driverId());

        replayer().replay(DRIVING_EVENT_DEAD_LETTER);

        List<TopicReader.Message> onTopic = tracer.awaitEvents(Topics.DRIVING_EVENT, correlationId, 2);
        assertThat(onTopic).extracting(m -> m.envelope().eventId()).containsOnly(event.eventId());
        assertThat(onTopic).extracting(TopicReader.Message::key).containsOnly(crash.driverId());
        assertThat(tracer.awaitRawByKey(DRIVING_EVENT_DEAD_LETTER, crash.driverId(), 2)).hasSizeGreaterThanOrEqualTo(2);
        Validator.with(awaitNoMoreClaimsThan(0, crash.driverId())).claimsBody().hasClaimCount(0);
    }
}
