package com.qnt.safedrive.tests.rewards.notificationfailures;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class NotificationFailureNegativeTest extends AbstractNotificationFailureTest {

    @DataProvider
    public Object[][] transientStatuses() {
        return new Object[][] {{500}, {503}, {429}};
    }

    @Test(dataProvider = "transientStatuses", groups = Groups.REGRESSION,
            description = "A provider that stays down (5xx, 429) is called 4 times, then the event is dead-lettered unchanged and the reward stays pending")
    @Story("Provider down")
    public void providerStaysDown(int status) {
        PremiumAdjusted premium = aPremiumAdjusted();
        failProvider(premium.driverId(), status);
        Envelope event = publishPremiumAdjusted(premium, Ids.correlation());

        var deadLetter = awaitDeadLetter(premium.driverId());

        Validator.with(deadLetter).hasKey(premium.driverId());
        assertThat(deadLetter.value()).contains(event.eventId());
        Validator.with(awaitNoMoreNotificationsThan(ATTEMPTS_BEFORE_DEAD_LETTER, premium.driverId())).hasCount(ATTEMPTS_BEFORE_DEAD_LETTER);
        assertThat(rewardsOf(premium.driverId()).rewards()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(Statuses.REWARD_PENDING));
    }

    @DataProvider
    public Object[][] permanentStatuses() {
        return new Object[][] {{400}, {404}, {422}};
    }

    @Test(dataProvider = "permanentStatuses", groups = Groups.REGRESSION,
            description = "A provider that rejects the request (4xx except 429) is called once: dead-lettered at once, no retries, reward stays pending")
    @Story("Permanent failure")
    public void providerRejectsTheRequest(int status) {
        PremiumAdjusted premium = aPremiumAdjusted();
        failProvider(premium.driverId(), status);
        publishPremiumAdjusted(premium, Ids.correlation());

        awaitDeadLetter(premium.driverId());

        Validator.with(awaitNoMoreNotificationsThan(1, premium.driverId())).hasCount(1);
        assertThat(rewardsOf(premium.driverId()).rewards()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(Statuses.REWARD_PENDING));
    }

    @Test(groups = Groups.REGRESSION, description = "A provider slower than the timeout counts as down: the call is retried and the reward stays pending")
    @Story("Provider slow")
    public void slowProviderIsRetried() {
        PremiumAdjusted premium = aPremiumAdjusted();
        String stub = notificationMock().delayFor(premium.driverId(), 202, 5000);
        onCleanup(() -> notificationMock().removeStub(stub));
        publishPremiumAdjusted(premium, Ids.correlation());

        awaitNotifications(premium.driverId(), 2);

        assertThat(rewardsOf(premium.driverId()).rewards()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(Statuses.REWARD_PENDING));
    }

    @Test(groups = Groups.REGRESSION, description = "A failing provider does not block the partition: another driver's reward on the same topic is still notified")
    @Story("Provider down")
    public void aFailingRewardDoesNotBlockOthersForever() {
        PremiumAdjusted failing = aPremiumAdjusted();
        failProvider(failing.driverId(), 400);
        publishPremiumAdjusted(failing, Ids.correlation());
        awaitDeadLetter(failing.driverId());

        PremiumAdjusted healthy = aPremiumAdjusted();
        publishPremiumAdjusted(healthy, Ids.correlation());

        awaitReward(healthy.driverId(), 1, Statuses.REWARD_NOTIFIED);
    }
}
