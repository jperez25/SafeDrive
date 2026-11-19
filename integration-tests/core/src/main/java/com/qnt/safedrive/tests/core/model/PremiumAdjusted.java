package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Payload of {@code premium.adjusted}. */
@With
@Builder(toBuilder = true)
public record PremiumAdjusted(
        String driverId, int scoreVersion, double basePremium, double adjustmentPct, double newPremium, String band) {}
