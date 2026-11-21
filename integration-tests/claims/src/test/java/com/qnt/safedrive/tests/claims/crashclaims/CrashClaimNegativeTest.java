package com.qnt.safedrive.tests.claims.crashclaims;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aHardBrakeEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import org.testng.annotations.Test;

public class CrashClaimNegativeTest extends AbstractCrashClaimTest {

    @Test(groups = Groups.REGRESSION, description = "Driving events that are not crashes open no claim, even for a driver with a policy")
    @Story("Only crashes")
    public void eventsThatAreNotCrashesOpenNoClaim() {
        DrivingEvent brake = aHardBrakeEvent();
        publishPremiumAdjusted(aPremiumAdjustedFor(brake.driverId(), 1, Bands.GOOD), Ids.correlation());
        for (String kind : List.of(DrivingEventKinds.HARD_BRAKE, DrivingEventKinds.HARSH_ACCELERATION,
                DrivingEventKinds.SPEEDING, DrivingEventKinds.DISTRACTION)) {
            publishDrivingEvent(brake.withKind(kind), Ids.correlation());
        }

        Validator.with(awaitNoMoreClaimsThan(0, brake.driverId())).claimsBody().hasDriverId(brake.driverId()).hasClaimCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "A stale policy (older score version) is ignored: the claim is opened with the newest policy")
    @Story("Policy updates")
    public void stalePolicyIsIgnored() {
        DrivingEvent crash = aCrashEvent();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 2, Bands.POOR), Ids.correlation());
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.EXCELLENT), Ids.correlation());
        publishDrivingEvent(crash, Ids.correlation());

        var claim = awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        Validator.with(claimsClient().getClaim(claim.claimId(), null))
                .claimBody()
                .hasStatus(Statuses.CLAIM_OPENED, Severities.HIGH)
                .hasPolicyScoreVersion(2);
    }

    @Test(groups = Groups.REGRESSION, description = "A policy of another driver does not open a waiting claim")
    @Story("Crash before policy")
    public void policyOfAnotherDriverDoesNotOpenTheClaim() {
        DrivingEvent crash = aCrashEvent();
        publishDrivingEvent(crash, Ids.correlation());
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_PENDING_POLICY);

        publishPremiumAdjusted(aPremiumAdjustedFor(Ids.driver(), 1, Bands.GOOD), Ids.correlation());

        Validator.with(awaitClaimStays(crash.driverId(), crash.tripId(), Statuses.CLAIM_PENDING_POLICY, null))
                .claimsBody()
                .hasClaim(crash.tripId(), Statuses.CLAIM_PENDING_POLICY, Severities.HIGH);
    }
}
