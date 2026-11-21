package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.utils.Ids;

/**
 * Valid {@code premium.adjusted} payloads with a unique driver: version 1, band excellent, base 120.00, premium 96.00.
 * Override with {@code withX}, e.g. {@code aPremiumAdjusted().withBand(Bands.GOOD)}.
 */
public final class PremiumAdjustedFactory {

    private PremiumAdjustedFactory() {}

    public static PremiumAdjusted aPremiumAdjusted() {
        return aPremiumAdjustedFor(Ids.driver(), 1, Bands.EXCELLENT);
    }

    /** A premium of this driver at the given score version, with the adjustment that matches the band. */
    public static PremiumAdjusted aPremiumAdjustedFor(String driverId, int scoreVersion, String band) {
        double pct = switch (band) {
            case Bands.EXCELLENT -> -20.0;
            case Bands.GOOD -> -10.0;
            case Bands.AVERAGE -> 0.0;
            case Bands.POOR -> 15.0;
            default -> 30.0;
        };
        double base = Bands.DEFAULT_BASE_PREMIUM;
        return PremiumAdjusted.builder()
                .driverId(driverId)
                .scoreVersion(scoreVersion)
                .basePremium(base)
                .adjustmentPct(pct)
                .newPremium(Math.round(base * (100 + pct)) / 100.0)
                .band(band)
                .build();
    }
}
