package com.qnt.safedrive.tests.core.model.constants;

import java.time.Duration;

/** Validation bounds of the ingest API, as documented in {@code docs/architecture/services.md}. */
public final class IngestLimits {

    private IngestLimits() {}

    public static final int MAX_SAMPLES = 5000;
    public static final double MAX_SPEED_KILOMETERS_PER_HOUR = 300;
    public static final double MAX_ACCELERATION_METERS_PER_SECOND_SQUARED = 50;
    public static final double MAX_LATITUDE = 90;
    public static final double MAX_LONGITUDE = 180;
    public static final double MAX_HEADING = 360;
    public static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    public static final Duration MAX_AGE = Duration.ofDays(7);
}
