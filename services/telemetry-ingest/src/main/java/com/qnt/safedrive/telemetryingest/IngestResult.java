package com.qnt.safedrive.telemetryingest;

import java.util.List;

/** Outcome of an ingest call; the controller maps the status to 202, 200 or 409. */
public record IngestResult(
        Status status, String batchId, String correlationId, int acceptedSamples, List<BatchValidator.RejectedSample> rejectedSamples) {

    public enum Status { ACCEPTED, DUPLICATE, CONFLICT }
}
