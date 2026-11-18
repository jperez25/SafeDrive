package com.qnt.safedrive.telemetryingest;

import java.util.List;

/**
 * Request body and, once validated, the {@code trip.raw} payload. Fields are boxed so that missing
 * values reach the validator instead of failing deserialization.
 */
public record TelemetryBatch(
        String batchId,
        String tripId,
        String driverId,
        String deviceId,
        Integer batchSeq,
        Boolean last,
        List<Sample> samples) {

    public record Sample(
            String ts, Double lat, Double lon, Double speedKmh, Double accelMs2, Double heading, Boolean phoneInUse) {}
}
