package com.qnt.safedrive.tests.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;

/** Payload of {@code driving.event}. */
@With
@Builder(toBuilder = true)
public record DrivingEvent(
        String tripId,
        String driverId,
        String kind,
        String severity,
        @JsonProperty("ts") String timestamp,
        @JsonProperty("lat") double latitude,
        @JsonProperty("lon") double longitude) {}
