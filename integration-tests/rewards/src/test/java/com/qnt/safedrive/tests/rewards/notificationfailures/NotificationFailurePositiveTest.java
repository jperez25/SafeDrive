package com.qnt.safedrive.tests.rewards.notificationfailures;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class NotificationFailurePositiveTest extends AbstractNotificationFailureTest {

    @Test(groups = Groups.REGRESSION, description = "A provider that fails and then recovers is retried: the reward ends notified, once")
    @Story("Transient failure")
    public void retriedUntilTheProviderRecovers() {
        PremiumAdjusted premium = aPremiumAdjusted();
        String stub = failProvider(premium.driverId(), 503);
        publishPremiumAdjusted(premium, Ids.correlation());
        awaitNotifications(premium.driverId(), 2); // the first call and a retry both failed
        assertThat(rewardsOf(premium.driverId()).rewards()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(Statuses.REWARD_PENDING));

        notificationMock().removeStub(stub);

        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);
        Validator.with(awaitNoMoreRewardsThan(1, premium.driverId())).rewardsBody().hasRewardCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "Replaying the dead letter once the provider is back notifies the pending reward without a second reward")
    @Story("Replay")
    public void replayNotifiesAfterTheProviderIsBack() {
        PremiumAdjusted premium = aPremiumAdjusted();
        String stub = failProvider(premium.driverId(), 503);
        publishPremiumAdjusted(premium, Ids.correlation());
        awaitDeadLetter(premium.driverId());
        var pending = rewardsOf(premium.driverId()).rewards().getFirst();
        assertThat(pending.status()).isEqualTo(Statuses.REWARD_PENDING);
        notificationMock().removeStub(stub);

        replayer().replay(DEAD_LETTER_TOPIC);

        var notified = awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);
        assertThat(notified.rewardId()).as("same reward, not a new one").isEqualTo(pending.rewardId());
        Validator.with(awaitNoMoreRewardsThan(1, premium.driverId())).rewardsBody().hasRewardCount(1);
    }
}
