package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Body of the premium API: the rating (200), or {@code status}, {@code driverId} and {@code correlationId} (404). */
@With
@Builder(toBuilder = true)
public record PremiumResponse(
        String driverId,
        Integer scoreVersion,
        Double basePremium,
        Double adjustmentPct,
        Double newPremium,
        String band,
        String updatedAt,
        String status,
        String correlationId) {}
