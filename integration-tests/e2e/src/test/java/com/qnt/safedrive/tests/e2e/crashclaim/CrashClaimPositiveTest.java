package com.qnt.safedrive.tests.e2e.crashclaim;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aCrashBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.ClaimsResponse;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class CrashClaimPositiveTest extends AbstractCrashClaimTest {

    @Test(groups = Groups.REGRESSION,
            description = "A crash in the telemetry is detected by trip-processor and opens a claim with the policy that policy-rating produced")
    @Story("Crash to claim")
    public void crashInTheTelemetryOpensAClaim() {
        Batch batch = aCrashBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);

        awaitEvent(Topics.DRIVING_EVENT, correlationId, "kind", DrivingEventKinds.CRASH);
        awaitPremiumAdjusted(batch.driverId(), correlationId, 1);
        ClaimsResponse.Claim claim = awaitClaim(batch.driverId(), batch.tripId(), Statuses.CLAIM_OPENED);
        Validator.with(claimsClient().getClaim(claim.claimId(), correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .claimBody()
                .isForTrip(batch.driverId(), batch.tripId())
                .hasStatus(Statuses.CLAIM_OPENED, Severities.MEDIUM)
                .hasPolicyScoreVersion(1);
    }
}
