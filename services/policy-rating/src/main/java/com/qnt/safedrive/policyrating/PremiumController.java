package com.qnt.safedrive.policyrating;

import com.qnt.safedrive.commons.CorrelationId;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PremiumController {

    private final RatingService service;

    PremiumController(RatingService service) {
        this.service = service;
    }

    /** 200 with the current rating, 404 when no score was applied for the driver yet. */
    @GetMapping("/v1/drivers/{driverId}/premium")
    ResponseEntity<?> premium(@PathVariable("driverId") String driverId) {
        return service.find(driverId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "status", "not_found", "driverId", driverId,
                        "correlationId", String.valueOf(CorrelationId.current()))));
    }
}
