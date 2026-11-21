package com.qnt.safedrive.tests.core.model;

import java.util.List;
import lombok.Builder;
import lombok.With;

/** Body of the rewards API: the driver and every reward granted, oldest score version first. */
@With
@Builder(toBuilder = true)
public record RewardsResponse(String driverId, List<Reward> rewards) {

    @With
    @Builder(toBuilder = true)
    public record Reward(
            String rewardId, int scoreVersion, String kind, String status, String createdAt, String notifiedAt) {}
}
