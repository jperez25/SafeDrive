package com.qnt.safedrive.claims;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ClaimsController {

    private final ClaimService service;

    ClaimsController(ClaimService service) {
        this.service = service;
    }

    /** Always 200; a driver without claims gets an empty list. */
    @GetMapping("/v1/drivers/{driverId}/claims")
    Map<String, Object> claims(@PathVariable("driverId") String driverId) {
        List<ClaimService.Claim> claims = service.find(driverId);
        return Map.of("driverId", driverId, "claims", claims);
    }

    @GetMapping("/v1/claims/{claimId}")
    ClaimService.Claim claim(@PathVariable("claimId") UUID claimId) {
        return service.findOne(claimId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
