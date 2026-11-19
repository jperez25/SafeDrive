package com.qnt.safedrive.tripprocessor;

import static com.qnt.safedrive.tripprocessor.Samples.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class TripSummaryTest {

    private static TripRaw.Sample sample(int second, double lat, double speed) {
        return new TripRaw.Sample(T0.plusSeconds(second), lat, 0, speed, 0, 0, false);
    }

    @Test
    void summarisesTimeSpeedAndDistance() {
        // one degree of latitude is about 111.2 km
        var s = TripSummary.of(List.of(sample(0, 0, 40), sample(30, 0.5, 80), sample(60, 1.0, 60)));
        assertThat(s.startedAt()).isEqualTo(T0);
        assertThat(s.endedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(s.durationS()).isEqualTo(60);
        assertThat(s.maxSpeedKmh()).isEqualTo(80);
        assertThat(s.avgSpeedKmh()).isEqualTo(60);
        assertThat(s.distanceKm()).isCloseTo(111.19, within(0.05));
    }

    @Test
    void singleSampleTripIsEmpty() {
        var s = TripSummary.of(List.of(sample(0, 10, 33)));
        assertThat(s.durationS()).isZero();
        assertThat(s.distanceKm()).isZero();
        assertThat(s.avgSpeedKmh()).isEqualTo(33);
    }
}
