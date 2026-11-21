package com.qnt.safedrive.tests.rewards.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aPremiumAdjustedEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
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

    @Test(groups = Groups.REGRESSION, description = "A valid event sitting in the dead-letter topic is replayed to premium.adjusted and the driver is rewarded")
    @Story("Replay")
    public void replayedValidEventIsRewarded() {
        PremiumAdjusted premium = aPremiumAdjusted();
        publisher().send(DEAD_LETTER_TOPIC, premium.driverId(), aPremiumAdjustedEvent(premium, Ids.correlation()));
        assertThat(rewardsOf(premium.driverId()).rewards()).as("before replay").isEmpty();

        replayer().replay(DEAD_LETTER_TOPIC);

        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);
        Validator.with(awaitNoMoreNotificationsThan(1, premium.driverId())).hasCount(1);
    }

    @Test(groups = Groups.REGRESSION,
            description = "Replay puts a dead letter back on premium.adjusted with the same key, event ID and correlation ID; if it still fails it is dead-lettered again")
    @Story("Replay")
    public void replayKeepsTheEventAndDeadLettersItAgainIfItStillFails() {
        PremiumAdjusted premium = aPremiumAdjusted();
        String correlationId = Ids.correlation();
        Envelope event = aPremiumAdjustedEvent(premium, correlationId).withType(EventTypes.SCORE_UPDATED);
        publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);
        awaitDeadLetter(premium.driverId());

        replayer().replay(DEAD_LETTER_TOPIC);

        List<TopicReader.Message> onTopic = tracer.awaitEvents(Topics.PREMIUM_ADJUSTED, correlationId, 2);
        assertThat(onTopic).extracting(m -> m.envelope().eventId()).containsOnly(event.eventId());
        assertThat(onTopic).extracting(TopicReader.Message::key).containsOnly(premium.driverId());
        assertThat(tracer.awaitRawByKey(DEAD_LETTER_TOPIC, premium.driverId(), 2)).hasSizeGreaterThanOrEqualTo(2);
        Validator.with(awaitNoMoreRewardsThan(0, premium.driverId())).rewardsBody().hasRewardCount(0);
    }
}
