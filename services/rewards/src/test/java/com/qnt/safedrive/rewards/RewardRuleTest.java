package com.qnt.safedrive.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RewardRuleTest {

    @Test
    void onlyTheExcellentBandEarnsAReward() {
        assertThat(RewardRule.kindFor("excellent")).contains("safe_driver_bonus");
        for (String band : new String[] {"good", "average", "poor", "high_risk", "unknown"}) {
            assertThat(RewardRule.kindFor(band)).isEmpty();
        }
    }
}
