package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Payload of {@code score.updated}. */
@With
@Builder(toBuilder = true)
public record ScoreUpdated(String driverId, int scoreVersion, double score, int basedOnTrips, String computedAt) {}
