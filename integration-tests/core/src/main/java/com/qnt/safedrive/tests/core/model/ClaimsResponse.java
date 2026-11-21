package com.qnt.safedrive.tests.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.With;

/** Body of the claims API: the driver and every claim opened for them, newest first. */
@With
@Builder(toBuilder = true)
public record ClaimsResponse(String driverId, List<Claim> claims) {

    /** One claim; also the body of {@code GET /v1/claims/{claimId}}. */
    @With
    @Builder(toBuilder = true)
    public record Claim(
            String claimId,
            String driverId,
            String tripId,
            String status,
            String severity,
            String crashAt,
            @JsonProperty("lat") double latitude,
            @JsonProperty("lon") double longitude,
            Integer policyScoreVersion,
            String createdAt) {}
}
