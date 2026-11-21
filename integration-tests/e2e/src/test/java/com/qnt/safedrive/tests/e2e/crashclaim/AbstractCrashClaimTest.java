package com.qnt.safedrive.tests.e2e.crashclaim;

import com.qnt.safedrive.tests.core.model.ClaimsResponse;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.e2e.AbstractE2ETest;
import io.qameta.allure.Feature;

/** Shared setup for the crash-to-claim journey: a crash in the telemetry opens a claim, through every service. */
@Feature("Crash claim")
public abstract class AbstractCrashClaimTest extends AbstractE2ETest {

    /** Waits until the driver has a claim for the trip in the given status. */
    protected ClaimsResponse.Claim awaitClaim(String driverId, String tripId, String status) {
        return waiter.until("claim of driver " + driverId + " trip " + tripId + " in status " + status, () -> Json
                .read(claimsClient().getClaimsWithRetry(driverId, null).asString(), ClaimsResponse.class)
                .claims().stream()
                .filter(c -> tripId.equals(c.tripId()) && status.equals(c.status()))
                .findFirst());
    }
}
