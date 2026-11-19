package com.qnt.safedrive.policyrating;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PremiumBandsTest {

    @Test
    void bandBoundariesAreInclusiveAtTheLowerBound() {
        assertThat(PremiumBands.forScore(100)).isEqualTo(PremiumBands.EXCELLENT);
        assertThat(PremiumBands.forScore(90)).isEqualTo(PremiumBands.EXCELLENT);
        assertThat(PremiumBands.forScore(89.99)).isEqualTo(PremiumBands.GOOD);
        assertThat(PremiumBands.forScore(75)).isEqualTo(PremiumBands.GOOD);
        assertThat(PremiumBands.forScore(74.99)).isEqualTo(PremiumBands.AVERAGE);
        assertThat(PremiumBands.forScore(60)).isEqualTo(PremiumBands.AVERAGE);
        assertThat(PremiumBands.forScore(59.99)).isEqualTo(PremiumBands.POOR);
        assertThat(PremiumBands.forScore(40)).isEqualTo(PremiumBands.POOR);
        assertThat(PremiumBands.forScore(39.99)).isEqualTo(PremiumBands.HIGH_RISK);
        assertThat(PremiumBands.forScore(0)).isEqualTo(PremiumBands.HIGH_RISK);
    }

    @Test
    void premiumIsBaseAdjustedByTheBandRoundedToCents() {
        var base = new BigDecimal("120.00");
        assertThat(PremiumBands.apply(base, PremiumBands.EXCELLENT)).isEqualByComparingTo("96.00");
        assertThat(PremiumBands.apply(base, PremiumBands.GOOD)).isEqualByComparingTo("108.00");
        assertThat(PremiumBands.apply(base, PremiumBands.AVERAGE)).isEqualByComparingTo("120.00");
        assertThat(PremiumBands.apply(base, PremiumBands.POOR)).isEqualByComparingTo("138.00");
        assertThat(PremiumBands.apply(base, PremiumBands.HIGH_RISK)).isEqualByComparingTo("156.00");
        assertThat(PremiumBands.apply(new BigDecimal("99.99"), PremiumBands.POOR)).isEqualByComparingTo("114.99");
    }
}
