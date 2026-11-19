package com.qnt.safedrive.tests.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;

/** One telemetry sample. Boxed fields so negative tests can leave values out (null is not serialized). */
@With
@Builder(toBuilder = true)
public record Sample(
        @JsonProperty("ts") String timestamp,
        @JsonProperty("lat") Double latitude,
        @JsonProperty("lon") Double longitude,
        @JsonProperty("speedKmh") Double speedKilometersPerHour,
        @JsonProperty("accelMs2") Double accelerationMetersPerSecondSquared,
        Double heading,
        Boolean phoneInUse) {}
