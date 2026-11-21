package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.ClaimsResponse;
import io.qameta.allure.Step;

/** The body of the claims API of a driver. */
public class ClaimsBodyValidator {

    private final ClaimsResponse actual;

    ClaimsBodyValidator(ClaimsResponse actual) {
        this.actual = actual;
    }

    @Step("Has driver id {driverId}")
    public ClaimsBodyValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        return this;
    }

    @Step("Has {count} claim(s)")
    public ClaimsBodyValidator hasClaimCount(int count) {
        assertThat(actual.claims()).as("body claims").hasSize(count);
        return this;
    }

    @Step("Has a {severity} claim for trip {tripId}, status {status}")
    public ClaimsBodyValidator hasClaim(String tripId, String status, String severity) {
        assertThat(actual.claims())
                .as("claims of trip %s", tripId)
                .filteredOn(c -> tripId.equals(c.tripId()))
                .singleElement()
                .satisfies(claim -> {
                    assertThat(claim.claimId()).as("claimId").isNotBlank();
                    assertThat(claim.driverId()).as("driverId").isEqualTo(actual.driverId());
                    assertThat(claim.status()).as("status").isEqualTo(status);
                    assertThat(claim.severity()).as("severity").isEqualTo(severity);
                    assertThat(claim.crashAt()).as("crashAt").isNotBlank();
                    assertThat(claim.createdAt()).as("createdAt").isNotBlank();
                });
        return this;
    }

    /** The ID of the claim of a trip, so a test can fetch that claim on its own. */
    public String claimIdOf(String tripId) {
        return actual.claims().stream()
                .filter(c -> tripId.equals(c.tripId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no claim for trip " + tripId))
                .claimId();
    }
}
