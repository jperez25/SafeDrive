package com.qnt.safedrive.tests.gateway.scopes;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScopePositiveTest extends AbstractScopeTest {

    @Test(groups = Groups.REGRESSION, description = "telemetry:write opens the ingest route")
    @Story("telemetry:write")
    public void telemetryWriteReachesIngest() {
        Batch batch = aBatch();

        Validator.with(gatewayClient().postBatch(batch, fullAccessFor(batch.driverId()), Ids.correlation()))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasBatchId(batch.batchId());
    }

    @Test(groups = Groups.REGRESSION, description = "score:read opens the score route")
    @Story("score:read")
    public void scoreReadReachesScoring() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverScore(driverId), fullAccessFor(driverId), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND)
                .scoreBody()
                .hasStatus(Statuses.SCORE_NOT_FOUND)
                .hasDriverId(driverId);
    }

    @Test(groups = Groups.REGRESSION, description = "premium:read opens the premium route")
    @Story("premium:read")
    public void premiumReadReachesPolicyRating() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverPremium(driverId), fullAccessFor(driverId), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND)
                .premiumBody()
                .hasStatus(Statuses.PREMIUM_NOT_FOUND)
                .hasDriverId(driverId);
    }

    @Test(groups = Groups.REGRESSION, description = "rewards:read opens the rewards route")
    @Story("rewards:read")
    public void rewardsReadReachesRewards() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverRewards(driverId), fullAccessFor(driverId), Ids.correlation()))
                .hasStatus(Http.OK)
                .rewardsBody()
                .hasDriverId(driverId)
                .hasRewardCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "claims:read opens the claims list of a driver")
    @Story("claims:read")
    public void claimsReadReachesClaimsList() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverClaims(driverId), fullAccessFor(driverId), Ids.correlation()))
                .hasStatus(Http.OK)
                .claimsBody()
                .hasDriverId(driverId)
                .hasClaimCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "claims:read opens a single claim (the service's own 404 for an unknown claim comes back)")
    @Story("claims:read")
    public void claimsReadReachesSingleClaim() {
        Validator.with(gatewayClient().get(Paths.claim(Ids.event()), fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "A user with only score:read can use the score route")
    @Story("score:read")
    public void readOnlyUserCanReadScore() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverScore(driverId), readOnlyFor(driverId), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND)
                .scoreBody()
                .hasStatus(Statuses.SCORE_NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "The driver in the token is not checked against the path (PoC): a token for one driver can read another driver")
    @Story("No ownership check")
    public void tokenOfOneDriverReadsAnotherDriver() {
        String otherDriver = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverScore(otherDriver), fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND)
                .scoreBody()
                .hasDriverId(otherDriver);
    }
}
