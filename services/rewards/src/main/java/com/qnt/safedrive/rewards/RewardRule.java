package com.qnt.safedrive.rewards;

import java.util.Optional;

/** Decides whether a premium adjustment earns a reward. */
final class RewardRule {

    static final String SAFE_DRIVER_BONUS = "safe_driver_bonus";

    private RewardRule() {}

    /** The reward kind earned by the band, or empty when none. Only the best band ("excellent") is rewarded. */
    static Optional<String> kindFor(String band) {
        return "excellent".equals(band) ? Optional.of(SAFE_DRIVER_BONUS) : Optional.empty();
    }
}
