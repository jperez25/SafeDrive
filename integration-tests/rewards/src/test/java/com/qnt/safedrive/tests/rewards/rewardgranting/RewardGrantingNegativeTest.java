package com.qnt.safedrive.tests.rewards.rewardgranting;

import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RewardGrantingNegativeTest extends AbstractRewardGrantingTest {

    @DataProvider
    public Object[][] unrewardedBands() {
        return new Object[][] {{Bands.GOOD}, {Bands.AVERAGE}, {Bands.POOR}, {Bands.HIGH_RISK}};
    }

    @Test(dataProvider = "unrewardedBands", groups = Groups.REGRESSION, description = "Only the excellent band earns a reward: any other band gives no reward and no notification")
    @Story("Other bands")
    public void otherBandsEarnNothing(String band) {
        PremiumAdjusted premium = aPremiumAdjustedFor(Ids.driver(), 1, band);
        publishPremiumAdjusted(premium, Ids.correlation());

        Validator.with(awaitNoMoreRewardsThan(0, premium.driverId())).rewardsBody().hasRewardCount(0);
        Validator.with(awaitNoMoreNotificationsThan(0, premium.driverId())).hasCount(0);
    }
}
