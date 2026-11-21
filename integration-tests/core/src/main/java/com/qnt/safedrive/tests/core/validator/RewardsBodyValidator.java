package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.RewardsResponse;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import io.qameta.allure.Step;

/** The body of the rewards API. */
public class RewardsBodyValidator {

    private final RewardsResponse actual;

    RewardsBodyValidator(RewardsResponse actual) {
        this.actual = actual;
    }

    @Step("Has driver id {driverId}")
    public RewardsBodyValidator hasDriverId(String driverId) {
        assertThat(actual.driverId()).as("body driverId").isEqualTo(driverId);
        return this;
    }

    @Step("Has {count} reward(s)")
    public RewardsBodyValidator hasRewardCount(int count) {
        assertThat(actual.rewards()).as("body rewards").hasSize(count);
        return this;
    }

    @Step("Has a {kind} reward for score version {scoreVersion}, status {status}")
    public RewardsBodyValidator hasReward(int scoreVersion, String kind, String status) {
        assertThat(actual.rewards())
                .as("rewards of score version %d", scoreVersion)
                .filteredOn(r -> r.scoreVersion() == scoreVersion)
                .singleElement()
                .satisfies(reward -> {
                    assertThat(reward.rewardId()).as("rewardId").isNotBlank();
                    assertThat(reward.kind()).as("kind").isEqualTo(kind);
                    assertThat(reward.status()).as("status").isEqualTo(status);
                    assertThat(reward.createdAt()).as("createdAt").isNotBlank();
                    if (Statuses.REWARD_NOTIFIED.equals(status)) {
                        assertThat(reward.notifiedAt()).as("notifiedAt").isNotBlank();
                    } else {
                        assertThat(reward.notifiedAt()).as("notifiedAt").isNull();
                    }
                });
        return this;
    }

    /** The ID of the reward for a score version, so a test can match it with what the provider received. */
    public String rewardIdOf(int scoreVersion) {
        return actual.rewards().stream()
                .filter(r -> r.scoreVersion() == scoreVersion)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reward for score version " + scoreVersion))
                .rewardId();
    }
}
