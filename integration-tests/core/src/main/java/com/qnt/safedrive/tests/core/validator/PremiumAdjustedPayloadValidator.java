package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import org.assertj.core.data.Offset;

/** The payload of a {@code premium.adjusted} event. */
public class PremiumAdjustedPayloadValidator {

    private final PremiumAdjusted actual;

    PremiumAdjustedPayloadValidator(PremiumAdjusted actual) {
        this.actual = actual;
    }

    public PremiumAdjustedPayloadValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("driverId").isEqualTo(driverId);
        return this;
    }

    public PremiumAdjustedPayloadValidator hasScoreVersion(int version) {
        assertThat(actual.scoreVersion()).as("scoreVersion").isEqualTo(version);
        return this;
    }

    public PremiumAdjustedPayloadValidator hasBasePremium(double premium) {
        assertThat(actual.basePremium()).as("basePremium").isCloseTo(premium, Offset.offset(0.005));
        return this;
    }

    public PremiumAdjustedPayloadValidator hasAdjustmentPct(double pct) {
        assertThat(actual.adjustmentPct()).as("adjustmentPct").isCloseTo(pct, Offset.offset(0.005));
        return this;
    }

    public PremiumAdjustedPayloadValidator hasNewPremium(double premium) {
        assertThat(actual.newPremium()).as("newPremium").isCloseTo(premium, Offset.offset(0.005));
        return this;
    }

    public PremiumAdjustedPayloadValidator hasBand(String band) {
        assertThat(actual.band()).as("band").isEqualTo(band);
        return this;
    }
}
