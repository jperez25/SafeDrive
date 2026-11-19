package com.qnt.safedrive.tripprocessor;

import java.time.Instant;

final class Samples {

    static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private Samples() {}

    /** A sample {@code second} seconds after T0 with the given speed, acceleration and phone state. */
    static TripRaw.Sample at(int second, double speedKmh, double accel, boolean phone) {
        return new TripRaw.Sample(T0.plusSeconds(second), 41.88, -87.63, speedKmh, accel, 90, phone);
    }

    static TripRaw.Sample cruise(int second) {
        return at(second, 50, 0, false);
    }
}
