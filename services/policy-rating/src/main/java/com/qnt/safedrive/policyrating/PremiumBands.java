package com.qnt.safedrive.policyrating;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Score bands and their premium adjustment (documented in docs/architecture/services.md).
 * A negative percentage is a discount, a positive one a surcharge.
 */
final class PremiumBands {

    record Band(String name, double adjustmentPct) {}

    static final Band EXCELLENT = new Band("excellent", -20);
    static final Band GOOD = new Band("good", -10);
    static final Band AVERAGE = new Band("average", 0);
    static final Band POOR = new Band("poor", 15);
    static final Band HIGH_RISK = new Band("high_risk", 30);

    private PremiumBands() {}

    /** Lower bounds are inclusive: 90 is excellent, 89.99 is good. */
    static Band forScore(double score) {
        if (score >= 90) return EXCELLENT;
        if (score >= 75) return GOOD;
        if (score >= 60) return AVERAGE;
        if (score >= 40) return POOR;
        return HIGH_RISK;
    }

    static BigDecimal apply(BigDecimal basePremium, Band band) {
        return basePremium.multiply(BigDecimal.valueOf(100 + band.adjustmentPct()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
