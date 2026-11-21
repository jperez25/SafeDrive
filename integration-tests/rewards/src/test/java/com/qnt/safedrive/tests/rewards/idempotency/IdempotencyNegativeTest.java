package com.qnt.safedrive.tests.rewards.idempotency;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class IdempotencyNegativeTest extends AbstractIdempotencyTest {

    @Test(groups = Groups.REGRESSION, description = "The same event delivered twice (same event ID) gives one reward and one notification")
    @Story("Duplicates")
    public void sameEventDeliveredTwiceIsProcessedOnce() {
        PremiumAdjusted premium = aPremiumAdjusted();
        Envelope event = publishPremiumAdjusted(premium, Ids.correlation());
        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);

        publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);

        Validator.with(awaitNoMoreRewardsThan(1, premium.driverId())).rewardsBody().hasRewardCount(1);
        Validator.with(awaitNoMoreNotificationsThan(1, premium.driverId())).hasCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "The same score version under a new event ID (a re-published premium) gives no second reward")
    @Story("Duplicates")
    public void sameScoreVersionUnderANewEventIdIsIgnored() {
        PremiumAdjusted premium = aPremiumAdjusted();
        publishPremiumAdjusted(premium, Ids.correlation());
        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);

        publishPremiumAdjusted(premium, Ids.correlation());

        Validator.with(awaitNoMoreRewardsThan(1, premium.driverId())).rewardsBody().hasRewardCount(1);
        Validator.with(awaitNoMoreNotificationsThan(1, premium.driverId())).hasCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "A duplicate that arrives after later scores changes nothing")
    @Story("Duplicates")
    public void lateDuplicateIsIgnored() {
        String driverId = Ids.driver();
        Envelope first = publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 1, Bands.EXCELLENT), Ids.correlation());
        awaitReward(driverId, 1, Statuses.REWARD_NOTIFIED);
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 2, Bands.EXCELLENT), Ids.correlation());
        awaitReward(driverId, 2, Statuses.REWARD_NOTIFIED);

        publisher().send(Topics.PREMIUM_ADJUSTED, driverId, first);

        Validator.with(awaitNoMoreRewardsThan(2, driverId)).rewardsBody().hasRewardCount(2);
        Validator.with(awaitNoMoreNotificationsThan(2, driverId)).hasCount(2);
    }
}
