package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.utils.Ids;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Valid {@code driving.event} payloads with a unique driver and trip: a high-severity crash a minute ago.
 * Override with {@code withX}, e.g. {@code aCrashEvent().withSeverity(Severities.LOW)}.
 */
public final class DrivingEventFactory {

    private DrivingEventFactory() {}

    public static DrivingEvent aCrashEvent() {
        return aCrashEventFor(Ids.driver(), Ids.trip());
    }

    /** A crash of this driver on this trip, so a test can send several for one driver or the same crash twice. */
    public static DrivingEvent aCrashEventFor(String driverId, String tripId) {
        return DrivingEvent.builder()
                .tripId(tripId)
                .driverId(driverId)
                .kind(DrivingEventKinds.CRASH)
                .severity(Severities.HIGH)
                .timestamp(Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString())
                .latitude(41.88)
                .longitude(-87.63)
                .build();
    }

    /** A driving event that is not a crash: it must never open a claim. */
    public static DrivingEvent aHardBrakeEvent() {
        return aCrashEvent().withKind(DrivingEventKinds.HARD_BRAKE).withSeverity(Severities.MEDIUM);
    }
}
