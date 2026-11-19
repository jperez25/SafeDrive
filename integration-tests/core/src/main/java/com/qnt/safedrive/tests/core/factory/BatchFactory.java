package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.core.utils.Ids;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Valid request models with unique IDs. Returns the {@link Batch} itself; tests override only what
 * they care about with its {@code withX} methods (or {@code toBuilder()} for several fields), e.g. {@code aBatch().withTripId(null)}.
 */
public final class BatchFactory {

    private static final int DEFAULT_SAMPLE_COUNT = 5;

    // Values sit inside the trip-processor detection bands (DrivingEventDetector) so each batch yields exactly one event.
    private static final double MEDIUM_BRAKE_ACCELERATION = -5.5;   // m/s2, medium band is -5.0 to -7.0
    private static final double HARD_BRAKE_ACCELERATION = -8.0;     // m/s2, high band is at or below -7.0
    private static final double SPEEDING_SPEED = 150.0;             // km/h, medium band is 140 to 160
    private static final double HARSH_ACCELERATION = 5.0;           // m/s2, medium band is 4.5 to 6.0
    private static final double DISTRACTED_SPEED = 60.0;            // km/h with the phone in use, medium band is 50 to 100
    private static final double CRASH_ACCELERATION = -35.0;         // m/s2, about 3.5 g, far beyond any braking
    private static final int EVENT_INDEX = 2;

    private BatchFactory() {}

    public static Batch aBatch() {
        return aBatch(DEFAULT_SAMPLE_COUNT);
    }

    public static Batch aBatch(int sampleCount) {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        return Batch.builder()
                .batchId(Ids.batch())
                .tripId(Ids.trip())
                .driverId(Ids.driver())
                .deviceId(Ids.device())
                .batchSequence(0)
                .last(true)
                .samples(cruise(start, sampleCount))
                .build();
    }

    /** Same trip, next batch: sequence + 1, new batch ID, samples continue after the previous batch's last sample. */
    public static Batch nextOf(Batch previous) {
        Instant after = Instant.parse(previous.samples().getLast().timestamp()).plusSeconds(1);
        return previous.toBuilder()
                .batchId(Ids.batch())
                .batchSequence(previous.batchSequence() + 1)
                .last(true)
                .samples(cruise(after, previous.samples().size()))
                .build();
    }

    /** A cruise batch with one medium-severity hard brake. */
    public static Batch aMediumBrakeBatch() {
        return withEvent(aBatch(), 50.0, MEDIUM_BRAKE_ACCELERATION);
    }

    /** A cruise batch with one high-severity hard brake. */
    public static Batch aHardBrakeBatch() {
        return withEvent(aBatch(), 50.0, HARD_BRAKE_ACCELERATION);
    }

    /** A cruise batch with one speeding event. */
    public static Batch aSpeedingBatch() {
        return withEvent(aBatch(), SPEEDING_SPEED, 0.0);
    }

    /** A cruise batch with one medium-severity harsh acceleration. */
    public static Batch aHarshAccelerationBatch() {
        return withEvent(aBatch(), 50.0, HARSH_ACCELERATION);
    }

    /** A cruise batch where the driver uses the phone while driving fast enough to count as distraction (medium). */
    public static Batch aDistractedBatch() {
        Batch batch = aBatch();
        List<Sample> samples = new ArrayList<>(batch.samples());
        samples.set(EVENT_INDEX, samples.get(EVENT_INDEX).withSpeedKilometersPerHour(DISTRACTED_SPEED).withPhoneInUse(true));
        return batch.withSamples(samples);
    }

    /** A cruise batch ending in a sudden stop: speed to zero with a crash-level deceleration. */
    public static Batch aCrashBatch() {
        return withEvent(aBatch(), 0.0, CRASH_ACCELERATION);
    }

    /** Replaces one sample of the batch, keeping its timestamp so ordering stays valid. */
    private static Batch withEvent(Batch batch, double speedKilometersPerHour, double accelerationMetersPerSecondSquared) {
        List<Sample> samples = new ArrayList<>(batch.samples());
        Instant timestamp = Instant.parse(samples.get(EVENT_INDEX).timestamp());
        samples.set(
                EVENT_INDEX,
                sample(timestamp, speedKilometersPerHour, accelerationMetersPerSecondSquared, false, EVENT_INDEX));
        return batch.withSamples(samples);
    }

    /** A calm drive: steady speed, no hard events, so detection stays quiet unless a test adds some. */
    public static List<Sample> cruise(Instant start, int count) {
        List<Sample> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(sample(start.plus(Duration.ofSeconds(i)), 50.0, 0.0, false, i));
        }
        return list;
    }

    /** A valid sample one minute ago; override what a test cares about, e.g. {@code aSample().withLatitude(91.0)}. */
    public static Sample aSample() {
        return sample(Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS), 50.0, 0.0, false, 0);
    }

    /** {@code count} valid samples, one second apart, ending one minute ago. */
    public static List<Sample> samples(int count) {
        return cruise(Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).minusSeconds(count), count);
    }

    private static Sample sample(Instant ts, double speedKmh, double accelMs2, boolean phoneInUse, int index) {
        return Sample.builder()
                .timestamp(ts.toString())
                .latitude(41.88 + index * 0.0001)
                .longitude(-87.63)
                .speedKilometersPerHour(speedKmh)
                .accelerationMetersPerSecondSquared(accelMs2)
                .heading(90.0)
                .phoneInUse(phoneInUse)
                .build();
    }
}
