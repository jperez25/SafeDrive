package com.qnt.safedrive.tripprocessor;

import java.time.Instant;
import java.util.Map;

/** Payloads published by this service. Contracts: trip.completed.v1 and driving.event.v1 in docs/contracts/events. */
public final class Published {

    private Published() {}

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

    public record DrivingEvent(
            String tripId, String driverId, String kind, String severity, Instant ts, double lat, double lon) {}
}
