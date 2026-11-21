package com.qnt.safedrive.tests.claims.crashclaims;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.ClaimsResponse;
import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Parallel;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.testng.annotations.Test;

public class CrashClaimPositiveTest extends AbstractCrashClaimTest {

    @Test(groups = Groups.SMOKE, description = "A crash of a driver with a policy opens a claim with the trip evidence and the policy version")
    @Story("Open a claim")
    public void crashOpensAClaim() {
        DrivingEvent crash = aCrashEvent();
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 3, Bands.GOOD), correlationId);
        publishDrivingEvent(crash, correlationId);

        ClaimsResponse.Claim claim = awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        Validator.with(claimsClient().getClaim(claim.claimId(), correlationId))
                .hasStatus(Http.OK)
                .claimBody()
                .hasClaimId(claim.claimId())
                .isForTrip(crash.driverId(), crash.tripId())
                .hasStatus(Statuses.CLAIM_OPENED, Severities.HIGH)
                .hasPolicyScoreVersion(3);
    }

    @Test(groups = Groups.REGRESSION, description = "A crash that arrives before the policy waits as pending_policy and is opened when the policy arrives")
    @Story("Crash before policy")
    public void crashBeforeThePolicyWaitsThenOpens() {
        DrivingEvent crash = aCrashEvent();
        String correlationId = Ids.correlation();
        publishDrivingEvent(crash, correlationId);
        ClaimsResponse.Claim waiting = awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_PENDING_POLICY);
        Validator.with(claimsClient().getClaim(waiting.claimId(), correlationId))
                .claimBody()
                .hasStatus(Statuses.CLAIM_PENDING_POLICY, Severities.HIGH)
                .hasPolicyScoreVersion(null);

        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 2, Bands.POOR), correlationId);

        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);
        Validator.with(claimsClient().getClaim(waiting.claimId(), correlationId))
                .claimBody()
                .hasClaimId(waiting.claimId())
                .hasStatus(Statuses.CLAIM_OPENED, Severities.HIGH)
                .hasPolicyScoreVersion(2);
    }

    @Test(groups = Groups.REGRESSION, description = "A later policy version does not change a claim that is already opened")
    @Story("Policy updates")
    public void laterPolicyDoesNotChangeAnOpenedClaim() {
        DrivingEvent crash = aCrashEvent();
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), correlationId);
        publishDrivingEvent(crash, correlationId);
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 2, Bands.HIGH_RISK), correlationId);

        Validator.with(awaitClaimStays(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED, 1))
                .claimsBody()
                .hasClaimCount(1)
                .hasClaim(crash.tripId(), Statuses.CLAIM_OPENED, Severities.HIGH);
    }

    @Test(groups = Groups.REGRESSION, description = "Every severity of a crash is carried to the claim")
    @Story("Open a claim")
    public void severityOfTheCrashIsKept() {
        for (String severity : List.of(Severities.LOW, Severities.MEDIUM, Severities.HIGH)) {
            DrivingEvent crash = aCrashEvent().withSeverity(severity);
            publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.AVERAGE), Ids.correlation());
            publishDrivingEvent(crash, Ids.correlation());

            awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

            Validator.with(claimsClient().getClaimsWithRetry(crash.driverId(), null))
                    .claimsBody()
                    .hasClaim(crash.tripId(), Statuses.CLAIM_OPENED, severity);
        }
    }

    @Test(groups = Groups.REGRESSION,
            description = "A crash and a policy sent at the same moment never leave a claim waiting (the two consumers do not miss each other)")
    @Story("Crash and policy together")
    public void crashAndPolicyAtTheSameMomentEndOpened() {
        List<DrivingEvent> crashes = new ArrayList<>();
        List<Callable<Object>> sends = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            DrivingEvent crash = aCrashEvent();
            crashes.add(crash);
            sends.add(() -> publishDrivingEvent(crash, Ids.correlation()));
            sends.add(() -> publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation()));
        }

        Parallel.all(sends);

        for (DrivingEvent crash : crashes) {
            awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);
        }
    }
}
