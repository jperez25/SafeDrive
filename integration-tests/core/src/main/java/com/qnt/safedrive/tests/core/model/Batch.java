package com.qnt.safedrive.tests.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.With;

/** Request body of {@code POST /v1/telemetry/batches}. Build with {@code BatchFactory}. */
@With
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Batch(
        String batchId,
        String tripId,
        String driverId,
        String deviceId,
        @JsonProperty("batchSeq") Integer batchSequence,
        Boolean last,
        List<Sample> samples) {}
