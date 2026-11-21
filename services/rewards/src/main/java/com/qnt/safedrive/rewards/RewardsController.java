package com.qnt.safedrive.rewards;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RewardsController {

    private final RewardService service;

    RewardsController(RewardService service) {
        this.service = service;
    }

    /** Always 200; a driver without rewards gets an empty list. */
    @GetMapping("/v1/drivers/{driverId}/rewards")
    Map<String, Object> rewards(@PathVariable("driverId") String driverId) {
        List<RewardService.Reward> rewards = service.find(driverId);
        return Map.of("driverId", driverId, "rewards", rewards);
    }
}
