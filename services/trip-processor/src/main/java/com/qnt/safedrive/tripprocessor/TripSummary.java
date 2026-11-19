package com.qnt.safedrive.tripprocessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Totals for a trip, computed from all of its samples. */
public record TripSummary(
        Instant startedAt, Instant endedAt, double distanceKm, long durationS, double avgSpeedKmh, double maxSpeedKmh) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Samples must be non-empty and in trip order. Average speed is the mean of the sampled speeds. */
    public static TripSummary of(List<TripRaw.Sample> samples) {
        Instant start = samples.getFirst().ts();
        Instant end = start;
        double distance = 0;
        double sum = 0;
        double max = 0;
        TripRaw.Sample prev = null;
        for (TripRaw.Sample s : samples) {
            if (s.ts().isBefore(start)) {
                start = s.ts();
            }
            if (s.ts().isAfter(end)) {
                end = s.ts();
            }
            if (prev != null) {
                distance += haversineKm(prev.lat(), prev.lon(), s.lat(), s.lon());
            }
            sum += s.speedKmh();
            max = Math.max(max, s.speedKmh());
            prev = s;
        }
        return new TripSummary(start, end, round(distance), Duration.between(start, end).toSeconds(),
                round(sum / samples.size()), max);
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
