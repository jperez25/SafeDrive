package com.qnt.safedrive.tests.rewards.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aPremiumAdjustedEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Events rewards can never handle go to the dead-letter topic untouched, without blocking what follows. */
public class DeadLetterNegativeTest extends AbstractDeadLetterTest {

    private static final String NOT_JSON = "this is not json {";

    @Test(groups = Groups.REGRESSION, description = "A message that is not JSON is dead-lettered with its key and value unchanged")
    @Story("Unreadable event")
    public void unreadableEventIsDeadLettered() {
        String key = Ids.driver();
        publisher().send(Topics.PREMIUM_ADJUSTED, key, NOT_JSON);

        Validator.with(awaitDeadLetter(key)).hasTopic(DEAD_LETTER_TOPIC).hasKey(key).hasValue(NOT_JSON);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on premium.adjusted is dead-lettered unchanged: no reward, no notification")
    @Story("Wrong contract")
    public void eventOfAnotherTypeIsDeadLettered() {
        PremiumAdjusted premium = aPremiumAdjusted();
        Envelope event = aPremiumAdjustedEvent(premium, Ids.correlation()).withType(EventTypes.SCORE_UPDATED);

        String sent = publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);

        Validator.with(awaitDeadLetter(premium.driverId())).hasKey(premium.driverId()).hasValue(sent);
        Validator.with(awaitNoMoreRewardsThan(0, premium.driverId())).rewardsBody().hasRewardCount(0);
        Validator.with(awaitNoMoreNotificationsThan(0, premium.driverId())).hasCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "An event with an unsupported contract version is dead-lettered unchanged")
    @Story("Wrong contract")
    public void unsupportedVersionIsDeadLettered() {
        PremiumAdjusted premium = aPremiumAdjusted();
        Envelope event = aPremiumAdjustedEvent(premium, Ids.correlation()).withVersion(2);

        String sent = publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);

        Validator.with(awaitDeadLetter(premium.driverId())).hasKey(premium.driverId()).hasValue(sent);
    }

    @DataProvider
    public Object[][] invalidPayloads() {
        return new Object[][] {
            {"driver ID missing", aPremiumAdjusted().withDriverId(null)},
            {"driver ID blank", aPremiumAdjusted().withDriverId(" ")},
            {"score version zero", aPremiumAdjusted().withScoreVersion(0)},
            {"score version negative", aPremiumAdjusted().withScoreVersion(-1)},
            {"band missing", aPremiumAdjusted().withBand(null)},
            {"band blank", aPremiumAdjusted().withBand(" ")}
        };
    }

    @Test(dataProvider = "invalidPayloads", groups = Groups.REGRESSION,
            description = "A well-formed envelope whose payload can never be rewarded is dead-lettered unchanged")
    @Story("Invalid payload")
    public void invalidPayloadIsDeadLettered(String reason, PremiumAdjusted invalid) {
        String key = Ids.driver(); // the payload may have no driver ID, so the test picks its own key
        String sent = publisher().send(Topics.PREMIUM_ADJUSTED, key, aPremiumAdjustedEvent(invalid, Ids.correlation()));

        Validator.with(awaitDeadLetter(key)).hasKey(key).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "A dead letter does not block the partition: a valid premium with the same key right after is still rewarded")
    @Story("Unreadable event")
    public void validPremiumAfterADeadLetterIsStillRewarded() {
        PremiumAdjusted premium = aPremiumAdjusted();
        publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), NOT_JSON);

        publishPremiumAdjusted(premium, Ids.correlation());

        awaitDeadLetter(premium.driverId());
        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);
    }

    @Test(groups = Groups.REGRESSION, description = "Dead-lettering is logged with the reason and the key, never the payload")
    @Story("Unreadable event")
    public void deadLetteringIsLogged() {
        String key = Ids.driver();
        publisher().send(Topics.PREMIUM_ADJUSTED, key, NOT_JSON);
        awaitDeadLetter(key);

        assertThat(tracer.awaitLog(Services.REWARDS, key))
                .as("rewards log lines for key %s", key)
                .anySatisfy(line -> assertThat(line.json()).contains("dead-lettering").contains("not a readable premium.adjusted event"))
                .allSatisfy(line -> assertThat(line.json()).doesNotContain(NOT_JSON));
    }
}
