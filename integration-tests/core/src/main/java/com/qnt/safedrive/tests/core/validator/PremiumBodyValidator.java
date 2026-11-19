package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.PremiumResponse;
import org.assertj.core.data.Offset;

/** The body of the premium API: the current rating (200) or the not-found answer (404). */
public class PremiumBodyValidator {

    private final PremiumResponse actual;

    PremiumBodyValidator(PremiumResponse actual) {
        this.actual = actual;
    }

    public PremiumBodyValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        return this;
    }

    public PremiumBodyValidator hasScoreVersion(int version) {
        assertThat(actual.scoreVersion()).as("body scoreVersion").isEqualTo(version);
        return this;
    }

    public PremiumBodyValidator hasBasePremium(double premium) {
        assertThat(actual.basePremium()).as("body basePremium").isNotNull().isCloseTo(premium, Offset.offset(0.005));
        return this;
    }

    public PremiumBodyValidator hasAdjustmentPct(double pct) {
        assertThat(actual.adjustmentPct()).as("body adjustmentPct").isNotNull().isCloseTo(pct, Offset.offset(0.005));
        return this;
    }

    public PremiumBodyValidator hasNewPremium(double premium) {
        assertThat(actual.newPremium()).as("body newPremium").isNotNull().isCloseTo(premium, Offset.offset(0.005));
        return this;
    }

    public PremiumBodyValidator hasBand(String band) {
        assertThat(actual.band()).as("body band").isEqualTo(band);
        return this;
    }

    public PremiumBodyValidator hasUpdatedAt() {
        assertThat(actual.updatedAt()).as("body updatedAt").isNotBlank();
        return this;
    }

    public PremiumBodyValidator hasStatus(String status) {
        assertThat(actual.status()).as("body status").isEqualTo(status);
        return this;
    }

    public PremiumBodyValidator hasCorrelationId(String correlationId) {
        assertThat(actual.correlationId()).as("body correlationId").isEqualTo(correlationId);
        return this;
    }
}
