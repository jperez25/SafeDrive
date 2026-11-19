package com.qnt.safedrive.policyrating;

import java.time.Instant;

/** Payload of score.updated v1 (docs/contracts/events). */
public record ScoreUpdated(String driverId, int scoreVersion, double score, int basedOnTrips, Instant computedAt) {}
