package com.qnt.safedrive.scoring;

import java.time.Instant;

/** Payloads published by this service. Contract: score.updated.v1 in docs/contracts/events. */
public final class Published {

    private Published() {}

    public record ScoreUpdated(String driverId, int scoreVersion, double score, int basedOnTrips, Instant computedAt) {}
}
