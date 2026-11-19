package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.utils.Ids;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Valid {@code trip.completed} payloads with unique IDs: a clean 100 km trip (no events, trip score 100).
 * Override with {@code withX}, e.g. {@code aTripCompleted().withDriverId(id).withEventCounts(Map.of(...))}.
 */
public final class TripCompletedFactory {

    private static final double DEFAULT_DISTANCE_KM = 100.0;

    private TripCompletedFactory() {}

    public static TripCompleted aTripCompleted() {
        Instant end = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        return TripCompleted.builder()
                .tripId(Ids.trip())
                .driverId(Ids.driver())
                .revision(1)
                .startedAt(end.minus(1, ChronoUnit.HOURS).toString())
                .endedAt(end.toString())
                .distanceKilometers(DEFAULT_DISTANCE_KM)
                .durationSeconds(3600)
                .averageSpeedKilometersPerHour(100.0)
                .maximumSpeedKilometersPerHour(120.0)
                .eventCounts(noEvents())
                .build();
    }

    /** A clean trip of this driver, ending the given number of minutes before now (later trips end later). */
    public static TripCompleted aTripCompletedFor(String driverId, int minutesAgo) {
        Instant end = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        return aTripCompleted().withDriverId(driverId).withStartedAt(end.minus(1, ChronoUnit.HOURS).toString()).withEndedAt(end.toString());
    }

    /** All event kinds of the trip summary, none of them happened. */
    public static Map<String, Integer> noEvents() {
        return Map.of(
                DrivingEventKinds.HARD_BRAKE, 0,
                DrivingEventKinds.HARSH_ACCELERATION, 0,
                DrivingEventKinds.SPEEDING, 0,
                DrivingEventKinds.DISTRACTION, 0);
    }

    /** {@link #noEvents()} with one kind set to {@code count}. */
    public static Map<String, Integer> events(String kind, int count) {
        Map<String, Integer> counts = new java.util.HashMap<>(noEvents());
        counts.put(kind, count);
        return counts;
    }
}
