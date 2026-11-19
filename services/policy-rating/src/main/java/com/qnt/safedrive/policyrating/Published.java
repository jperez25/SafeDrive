package com.qnt.safedrive.policyrating;

import java.math.BigDecimal;

/** Payloads published by this service. Contract: premium.adjusted.v1 in docs/contracts/events. */
public final class Published {

    private Published() {}

    public record PremiumAdjusted(String driverId, int scoreVersion, BigDecimal basePremium, double adjustmentPct,
                                  BigDecimal newPremium, String band) {}
}
