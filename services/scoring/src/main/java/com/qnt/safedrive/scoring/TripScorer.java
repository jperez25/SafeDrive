package com.qnt.safedrive.scoring;

import java.util.List;
import java.util.Map;

/**
 * The scoring formula, kept simple on purpose (documented in docs/architecture/services.md).
 *
 * <pre>
 * penalty     = sum(weight[kind] * count[kind]) per 100 km, distance floored at 1 km
 * trip score  = max(0, 100 - penalty), rounded to 2 decimals
 * driver score = mean of the trip scores of the last N trips, rounded to 2 decimals
 * </pre>
 */
final class TripScorer {

    static final Map<String, Integer> WEIGHTS =
            Map.of("hard_brake", 2, "harsh_accel", 2, "speeding", 3, "distraction", 4, "crash", 25);
    static final double MIN_DISTANCE_KM = 1.0;

    private TripScorer() {}

    static double tripScore(double distanceKm, Map<String, Integer> eventCounts) {
        int weighted = 0;
        for (Map.Entry<String, Integer> e : eventCounts.entrySet()) {
            weighted += WEIGHTS.getOrDefault(e.getKey(), 0) * e.getValue();   // unknown kinds do not count
        }
        double penalty = weighted / (Math.max(distanceKm, MIN_DISTANCE_KM) / 100.0);
        return round2(Math.max(0, 100 - penalty));
    }

    /** Mean of the given trip scores; 100 when the list is empty. */
    static double driverScore(List<Double> tripScores) {
        return tripScores.isEmpty()
                ? 100
                : round2(tripScores.stream().mapToDouble(Double::doubleValue).average().orElse(100));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
