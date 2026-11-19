package com.qnt.safedrive.tests.core.model;

import java.util.List;
import lombok.Builder;
import lombok.With;
import tools.jackson.databind.JsonNode;

/** Body of the ingest API: accepted, duplicate, conflict and invalid all share this shape. */
@With
@Builder(toBuilder = true)
public record IngestResponse(
        String status,
        String batchId,
        String correlationId,
        Integer acceptedSamples,
        List<JsonNode> rejectedSamples,
        List<String> errors) {}
