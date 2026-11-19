package com.qnt.safedrive.scoring;

import com.qnt.safedrive.commons.CorrelationId;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ScoreController {

    private final ScoringService service;

    ScoreController(ScoringService service) {
        this.service = service;
    }

    /** 200 with the current score, 404 when the driver has no scored trip yet. */
    @GetMapping("/v1/drivers/{driverId}/score")
    ResponseEntity<?> score(@PathVariable("driverId") String driverId) {
        return service.find(driverId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "status", "not_found", "driverId", driverId,
                        "correlationId", String.valueOf(CorrelationId.current()))));
    }
}
