package com.qnt.safedrive.scoring;

import java.time.Instant;
import java.util.Map;

/** Payload of trip.completed v1 (docs/contracts/events). Fields scoring does not need are still read. */
public record TripCompleted(
        String tripId,
        String driverId,
        int revision,
        Instant startedAt,
        Instant endedAt,
        double distanceKm,
        long durationS,
        double avgSpeedKmh,
        double maxSpeedKmh,
        Map<String, Integer> eventCounts) {}
