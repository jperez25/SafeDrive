package com.qnt.safedrive.claims;

import java.time.Instant;

/** Payload of driving.event v1 (docs/contracts/events). */
public record DrivingEvent(String tripId, String driverId, String kind, String severity, Instant ts, double lat,
                           double lon) {}
