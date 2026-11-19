package com.qnt.safedrive.tripprocessor;

import java.time.Instant;
import java.util.List;

/** Payload of {@code trip.raw}. Contract: docs/contracts/events/trip.raw.v1.schema.json. */
public record TripRaw(
        String batchId,
        String tripId,
        String driverId,
        String deviceId,
        int batchSeq,
        boolean last,
        List<Sample> samples) {

    public record Sample(
            Instant ts, double lat, double lon, double speedKmh, double accelMs2, double heading, boolean phoneInUse) {}
}
