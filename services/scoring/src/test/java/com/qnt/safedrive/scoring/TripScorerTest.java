package com.qnt.safedrive.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TripScorerTest {

    @Test
    void cleanTripScoresHundred() {
        assertThat(TripScorer.tripScore(25, Map.of("hard_brake", 0, "speeding", 0))).isEqualTo(100);
    }

    @Test
    void penaltyIsWeightedEventsPer100Km() {
        // 2 hard brakes (2 each) + 1 speeding (3) over 50 km = 7 per 50 km = 14 per 100 km
        assertThat(TripScorer.tripScore(50, Map.of("hard_brake", 2, "speeding", 1))).isEqualTo(86);
    }

    @Test
    void shortTripsAreMeasuredOverAtLeastOneKm() {
        // 1 distraction (4) over 0 km counts as 1 km: penalty 400, floored at 0
        assertThat(TripScorer.tripScore(0, Map.of("distraction", 1))).isEqualTo(0);
        // 1 hard brake over 0.5 km counts as 1 km: penalty 200
        assertThat(TripScorer.tripScore(0.5, Map.of("hard_brake", 1))).isEqualTo(0);
    }

    @Test
    void scoreNeverGoesBelowZero() {
        assertThat(TripScorer.tripScore(10, Map.of("crash", 1))).isEqualTo(0);
    }

    @Test
    void unknownEventKindsDoNotCount() {
        assertThat(TripScorer.tripScore(10, Map.of("teleport", 5))).isEqualTo(100);
    }

    @Test
    void tripScoreIsRoundedToTwoDecimals() {
        // 1 hard brake (2) over 30 km = 6.666.. per 100 km
        assertThat(TripScorer.tripScore(30, Map.of("hard_brake", 1))).isEqualTo(93.33);
    }

    @Test
    void driverScoreIsTheMeanOfTheTripScores() {
        assertThat(TripScorer.driverScore(List.of(100.0, 80.0, 90.0))).isEqualTo(90);
        assertThat(TripScorer.driverScore(List.of(100.0, 93.33, 93.33))).isEqualTo(95.55);
    }

    @Test
    void noTripsMeansPerfectScore() {
        assertThat(TripScorer.driverScore(List.of())).isEqualTo(100);
    }
}
