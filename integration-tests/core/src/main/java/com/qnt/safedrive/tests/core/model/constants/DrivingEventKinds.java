package com.qnt.safedrive.tests.core.model.constants;

/** The {@code kind} of a driving event (and the keys of {@code eventCounts} in {@code trip.completed}). */
public final class DrivingEventKinds {

    private DrivingEventKinds() {}

    public static final String HARD_BRAKE = "hard_brake";
    public static final String HARSH_ACCELERATION = "harsh_accel";
    public static final String SPEEDING = "speeding";
    public static final String DISTRACTION = "distraction";
    public static final String CRASH = "crash";
}
