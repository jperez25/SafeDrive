package com.qnt.safedrive.telemetryingest;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.qnt.safedrive.commons.CorrelationId;

@RestController
class TelemetryController {

    private final BatchValidator validator;
    private final IngestService service;
    private final MeterRegistry meters;

    TelemetryController(BatchValidator validator, IngestService service, MeterRegistry meters) {
        this.validator = validator;
        this.service = service;
        this.meters = meters;
    }

    /** 202 accepted, 200 duplicate, 400 invalid, 409 same batchId with different content. */
    @PostMapping("/v1/telemetry/batches")
    ResponseEntity<Map<String, Object>> post(@RequestBody TelemetryBatch batch) {
        BatchValidator.Result result = validator.validate(batch);
        if (!result.valid()) {
            count("invalid");
            return error(result.errors());
        }
        IngestResult r = service.ingest(batch, result);
        count(r.status().name().toLowerCase());
        return switch (r.status()) {
            case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "status", "accepted",
                    "batchId", r.batchId(),
                    "correlationId", r.correlationId(),
                    "acceptedSamples", r.acceptedSamples(),
                    "rejectedSamples", r.rejectedSamples()));
            case DUPLICATE -> ResponseEntity.ok(Map.of(
                    "status", "duplicate", "batchId", r.batchId(), "correlationId", r.correlationId()));
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "conflict",
                    "batchId", r.batchId(),
                    "correlationId", r.correlationId(),
                    "errors", List.of("batchId was already used with different content")));
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        count("invalid");
        return error(List.of("malformed JSON body"));
    }

    private void count(String status) {
        meters.counter("safedrive.ingest.batches", "status", status).increment();
    }

    private static ResponseEntity<Map<String, Object>> error(List<String> errors) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "invalid", "correlationId", String.valueOf(CorrelationId.current()), "errors", errors));
    }
}
