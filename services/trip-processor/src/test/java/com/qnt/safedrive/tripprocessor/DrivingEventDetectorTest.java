package com.qnt.safedrive.tripprocessor;

import static com.qnt.safedrive.tripprocessor.Samples.at;
import static com.qnt.safedrive.tripprocessor.Samples.cruise;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tripprocessor.DrivingEventDetector.Detected;
import com.qnt.safedrive.tripprocessor.DrivingEventDetector.Kind;
import com.qnt.safedrive.tripprocessor.DrivingEventDetector.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class DrivingEventDetectorTest {

    @Test
    void calmDrivingHasNoEvents() {
        assertThat(DrivingEventDetector.detect(List.of(cruise(0), cruise(1), cruise(2)))).isEmpty();
    }

    @Test
    void hardBrakeSeverityFollowsDeceleration() {
        var events = DrivingEventDetector.detect(List.of(
                cruise(0), at(1, 50, -3.6, false), cruise(2), at(3, 50, -5.5, false), cruise(4), at(5, 50, -8, false)));
        assertThat(events).extracting(Detected::kind).containsOnly(Kind.hard_brake);
        assertThat(events).extracting(Detected::severity).containsExactly(Severity.low, Severity.medium, Severity.high);
    }

    @Test
    void harshAccelerationIsDetected() {
        var events = DrivingEventDetector.detect(List.of(cruise(0), at(1, 50, 4.6, false), cruise(2)));
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(Kind.harsh_accel);
            assertThat(e.severity()).isEqualTo(Severity.medium);
        });
    }

    @Test
    void speedingAndDistractionAreDetected() {
        var events = DrivingEventDetector.detect(List.of(
                at(0, 145, 0, false), cruise(1), at(2, 60, 0, true), cruise(3)));
        assertThat(events).extracting(Detected::kind).containsExactly(Kind.speeding, Kind.distraction);
        assertThat(events).extracting(Detected::severity).containsExactly(Severity.medium, Severity.medium);
    }

    @Test
    void phoneInUseWhileStoppedIsNotDistraction() {
        assertThat(DrivingEventDetector.detect(List.of(at(0, 5, 0, true), at(1, 0, 0, true)))).isEmpty();
    }

    @Test
    void consecutiveSamplesAreOneEventWithWorstSeverityAndFirstTimestamp() {
        var events = DrivingEventDetector.detect(List.of(
                cruise(0), at(1, 50, -3.6, false), at(2, 50, -8, false), at(3, 50, -4, false), cruise(4)));
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.severity()).isEqualTo(Severity.high);
            assertThat(e.ts()).isEqualTo(Samples.T0.plusSeconds(1));
        });
    }

    @Test
    void anEventAtTheEndOfTheTripIsNotLost() {
        assertThat(DrivingEventDetector.detect(List.of(cruise(0), at(1, 50, -6, false)))).hasSize(1);
    }

    @Test
    void separateRunsOfTheSameKindAreSeparateEvents() {
        var events = DrivingEventDetector.detect(List.of(
                at(0, 125, 0, false), cruise(1), at(2, 125, 0, false)));
        assertThat(events).hasSize(2);
    }

    @Test
    void eventsAreOrderedByTime() {
        var events = DrivingEventDetector.detect(List.of(at(0, 125, 0, false), at(1, 50, -6, false)));
        assertThat(events).extracting(Detected::kind).containsExactly(Kind.speeding, Kind.hard_brake);
    }
}
