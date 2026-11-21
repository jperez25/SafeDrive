package com.qnt.safedrive.tests.rewards.rewardsapi;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.RewardKinds;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class RewardsApiPositiveTest extends AbstractRewardsApiTest {

    @Test(groups = Groups.REGRESSION, description = "The API lists the driver's rewards by score version and echoes the correlation ID")
    @Story("Read the rewards")
    public void apiListsTheRewardsInOrder() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 1, Bands.EXCELLENT), correlationId);
        awaitReward(driverId, 1, Statuses.REWARD_NOTIFIED);
        publishPremiumAdjusted(aPremiumAdjustedFor(driverId, 2, Bands.EXCELLENT), correlationId);
        awaitReward(driverId, 2, Statuses.REWARD_NOTIFIED);

        Validator.with(rewardsClient().getRewardsWithRetry(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .rewardsBody()
                .hasDriverId(driverId)
                .hasRewardCount(2)
                .hasReward(1, RewardKinds.SAFE_DRIVER_BONUS, Statuses.REWARD_NOTIFIED)
                .hasReward(2, RewardKinds.SAFE_DRIVER_BONUS, Statuses.REWARD_NOTIFIED);
    }

    @Test(groups = Groups.REGRESSION, description = "A request without a correlation ID still gets one back in the response header")
    @Story("Correlation ID")
    public void apiCreatesACorrelationIdWhenNoneIsSent() {
        Validator.with(rewardsClient().getRewardsWithRetry(Ids.driver(), null)).hasStatus(Http.OK).hasCorrelationHeader();
    }
}
