package com.qnt.safedrive.tests.rewards.rewardsapi;

import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class RewardsApiNegativeTest extends AbstractRewardsApiTest {

    @Test(groups = Groups.REGRESSION, description = "A driver without rewards gets 200 and an empty list, not 404")
    @Story("Unknown driver")
    public void unknownDriverHasNoRewards() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(rewardsClient().getRewards(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .rewardsBody()
                .hasDriverId(driverId)
                .hasRewardCount(0);
    }
}
