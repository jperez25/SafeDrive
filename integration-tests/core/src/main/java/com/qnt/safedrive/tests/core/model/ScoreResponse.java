package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Body of the score API: the score (200), or {@code status}, {@code driverId} and {@code correlationId} (404). */
@With
@Builder(toBuilder = true)
public record ScoreResponse(
        String driverId,
        Double score,
        Integer scoreVersion,
        Integer basedOnTrips,
        String computedAt,
        String status,
        String correlationId) {}
