package com.qnt.safedrive.tests.core.model.constants;

/** Event types (the {@code type} field of the envelope). */
public final class EventTypes {

    private EventTypes() {}

    public static final String TRIP_RAW = "trip.raw";
    public static final String TRIP_COMPLETED = "trip.completed";
    public static final String DRIVING_EVENT = "driving.event";
    public static final String SCORE_UPDATED = "score.updated";
    public static final String PREMIUM_ADJUSTED = "premium.adjusted";
}
