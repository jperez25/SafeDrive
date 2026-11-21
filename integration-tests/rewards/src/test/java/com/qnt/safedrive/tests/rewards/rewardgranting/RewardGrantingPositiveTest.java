package com.qnt.safedrive.tests.rewards.rewardgranting;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.RewardKinds;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

public class RewardGrantingPositiveTest extends AbstractRewardGrantingTest {

    @Test(groups = Groups.SMOKE, description = "An excellent premium earns a reward and the provider is notified")
    @Story("Excellent band")
    public void excellentPremiumEarnsAReward() {
        PremiumAdjusted premium = aPremiumAdjusted();
        publishPremiumAdjusted(premium, Ids.correlation());

        awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);
        Validator.with(rewardsClient().getRewardsWithRetry(premium.driverId(), null))
                .hasStatus(Http.OK)
                .rewardsBody()
                .hasDriverId(premium.driverId())
                .hasRewardCount(1)
                .hasReward(1, RewardKinds.SAFE_DRIVER_BONUS, Statuses.REWARD_NOTIFIED);
    }

    @Test(groups = Groups.REGRESSION,
            description = "The provider receives the reward ID, driver and kind, the reward ID as idempotency key and the correlation ID, and nothing else")
    @Story("Notification content")
    public void providerReceivesTheNotification() {
        PremiumAdjusted premium = aPremiumAdjusted();
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(premium, correlationId);
        var reward = awaitReward(premium.driverId(), 1, Statuses.REWARD_NOTIFIED);

        JsonNode received = awaitNotifications(premium.driverId(), 1).getFirst();
        JsonNode body = bodyOf(received);
        assertThat(body.path("rewardId").asString()).isEqualTo(reward.rewardId());
        assertThat(body.path("driverId").asString()).isEqualTo(premium.driverId());
        assertThat(body.path("kind").asString()).isEqualTo(RewardKinds.SAFE_DRIVER_BONUS);
        assertThat(body.propertyNames()).as("notification fields (no PII, no premium data)")
                .containsExactlyInAnyOrder("rewardId", "driverId", "kind");
        assertThat(headerOf(received, "Idempotency-Key")).isEqualTo(reward.rewardId());
        assertThat(headerOf(received, Http.CORRELATION_HEADER)).isEqualTo(correlationId);
        Validator.with(awaitNoMoreNotificationsThan(1, premium.driverId())).hasCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "Every excellent score version of a driver earns its own reward")
    @Story("Excellent band")
    public void eachExcellentScoreVersionEarnsAReward() {
        String driverId = Ids.driver();
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 1, Bands.EXCELLENT), Ids.correlation());
        awaitReward(driverId, 1, Statuses.REWARD_NOTIFIED);
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 2, Bands.EXCELLENT), Ids.correlation());
        awaitReward(driverId, 2, Statuses.REWARD_NOTIFIED);

        Validator.with(rewardsClient().getRewardsWithRetry(driverId, null))
                .rewardsBody()
                .hasRewardCount(2)
                .hasReward(1, RewardKinds.SAFE_DRIVER_BONUS, Statuses.REWARD_NOTIFIED)
                .hasReward(2, RewardKinds.SAFE_DRIVER_BONUS, Statuses.REWARD_NOTIFIED);
        Validator.with(awaitNoMoreNotificationsThan(2, driverId)).hasCount(2);
    }

    @Test(groups = Groups.REGRESSION, description = "A driver who drops from excellent to poor keeps the earned reward and gets no new one")
    @Story("Excellent band")
    public void aLaterWorseBandKeepsTheEarnedReward() {
        String driverId = Ids.driver();
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 1, Bands.EXCELLENT), Ids.correlation());
        awaitReward(driverId, 1, Statuses.REWARD_NOTIFIED);

        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 2, Bands.POOR), Ids.correlation());

        Validator.with(awaitNoMoreRewardsThan(1, driverId)).rewardsBody().hasRewardCount(1);
        Validator.with(awaitNoMoreNotificationsThan(1, driverId)).hasCount(1);
    }
}
