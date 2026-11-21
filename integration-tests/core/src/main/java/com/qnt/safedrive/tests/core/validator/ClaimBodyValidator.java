package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.ClaimsResponse.Claim;
import io.qameta.allure.Step;

/** The body of one claim. */
public class ClaimBodyValidator {

    private final Claim actual;

    ClaimBodyValidator(Claim actual) {
        this.actual = actual;
    }

    @Step("Has claim id {claimId}")
    public ClaimBodyValidator hasClaimId(String claimId) {
        assertThat(actual.claimId()).as("body claimId").isEqualTo(claimId);
        return this;
    }

    @Step("Has driver id {driverId} and trip id {tripId}")
    public ClaimBodyValidator isForTrip(String driverId, String tripId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        assertThat(actual.tripId()).as("body tripId").isEqualTo(tripId);
        return this;
    }

    @Step("Has status {status} and severity {severity}")
    public ClaimBodyValidator hasStatus(String status, String severity) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        assertThat(actual.severity()).as("body severity").isEqualTo(severity);
        return this;
    }

    @Step("Has policy score version {version}")
    public ClaimBodyValidator hasPolicyScoreVersion(Integer version) {
        assertThat(actual.policyScoreVersion()).as("body policyScoreVersion").isEqualTo(version);
        return this;
    }
}
