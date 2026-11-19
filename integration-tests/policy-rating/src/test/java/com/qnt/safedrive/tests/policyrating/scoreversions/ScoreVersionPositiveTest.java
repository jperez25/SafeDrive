package com.qnt.safedrive.tests.policyrating.scoreversions;

import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScoreVersionPositiveTest extends AbstractScoreVersionTest {

    @Test(groups = Groups.REGRESSION, description = "Versions that skip numbers are fine: a higher version is always applied")
    @Story("Newer versions")
    public void higherVersionWithAGapIsApplied() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId);
        publishAndAwaitPremium(driverId, 5, 65.0, correlationId);

        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(5)
                .hasBand(Bands.AVERAGE)
                .hasNewPremium(120.00);
    }

    @Test(groups = Groups.REGRESSION, description = "Every applied version publishes its own premium.adjusted with that version")
    @Story("Newer versions")
    public void everyAppliedVersionIsPublished() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        publishAndAwaitPremium(driverId, 1, 95.0, correlationId);
        publishAndAwaitPremium(driverId, 2, 80.0, correlationId);
        publishAndAwaitPremium(driverId, 3, 50.0, correlationId);

        assertPremiumAdjustedTimes(3, driverId, correlationId);
    }
}
