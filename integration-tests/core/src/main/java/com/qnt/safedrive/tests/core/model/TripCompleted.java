package com.qnt.safedrive.tests.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.With;

/** Payload of {@code trip.completed}. */
@With
@Builder(toBuilder = true)
public record TripCompleted(
        String tripId,
        String driverId,
        int revision,
        String startedAt,
        String endedAt,
        @JsonProperty("distanceKm") double distanceKilometers,
        @JsonProperty("durationS") long durationSeconds,
        @JsonProperty("avgSpeedKmh") double averageSpeedKilometersPerHour,
        @JsonProperty("maxSpeedKmh") double maximumSpeedKilometersPerHour,
        Map<String, Integer> eventCounts) {}
