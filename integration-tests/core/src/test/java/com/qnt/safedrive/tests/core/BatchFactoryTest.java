package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.factory.BatchFactory;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.utils.Json;
import java.time.Instant;
import org.testng.annotations.Test;

public class BatchFactoryTest {

    @Test
    public void everyBatchGetsUniqueIds() {
        Batch a = BatchFactory.aBatch();
        Batch b = BatchFactory.aBatch();
        assertThat(a.batchId()).isNotEqualTo(b.batchId());
        assertThat(a.tripId()).isNotEqualTo(b.tripId());
        assertThat(a.driverId()).isNotEqualTo(b.driverId());
    }

    @Test
    public void defaultBatchIsValidAndOrdered() {
        Batch batch = BatchFactory.aBatch(4);
        assertThat(batch.samples()).hasSize(4);
        assertThat(batch.batchSequence()).isZero();
        assertThat(batch.last()).isTrue();
        Instant previous = Instant.MIN;
        for (var sample : batch.samples()) {
            Instant ts = Instant.parse(sample.timestamp());
            assertThat(ts).isAfter(previous);
            previous = ts;
        }
    }

    @Test
    public void nextBatchContinuesTheSameTrip() {
        Batch first = BatchFactory.aBatch().withLast(false);
        Batch second = BatchFactory.nextOf(first);
        assertThat(second.tripId()).isEqualTo(first.tripId());
        assertThat(second.driverId()).isEqualTo(first.driverId());
        assertThat(second.batchSequence()).isEqualTo(1);
        assertThat(second.batchId()).isNotEqualTo(first.batchId());
        assertThat(Instant.parse(second.samples().getFirst().timestamp()))
                .isAfter(Instant.parse(first.samples().getLast().timestamp()));
    }

    @Test
    public void speedingBatchAfterContinuesTheTripWithOneFastSample() {
        Batch first = BatchFactory.aBatch().withLast(false);
        Batch late = BatchFactory.aSpeedingBatchAfter(first);
        assertThat(late.tripId()).isEqualTo(first.tripId());
        assertThat(late.batchSequence()).isEqualTo(1);
        assertThat(late.samples()).filteredOn(s -> s.speedKilometersPerHour() > 140.0).hasSize(1);
    }

    @Test
    public void missingFieldsAreLeftOutOfTheJson() {
        String json = Json.write(BatchFactory.aBatch().withTripId(null));
        assertThat(Json.tree(json).has("tripId")).isFalse();
        assertThat(Json.tree(json).has("batchId")).isTrue();
    }

    @Test
    public void eventBatchesChangeOnlyOneSample() {
        Batch calm = BatchFactory.aBatch();
        for (Batch batch : new Batch[] {
            BatchFactory.aMediumBrakeBatch(), BatchFactory.aHardBrakeBatch(),
            BatchFactory.aSpeedingBatch(), BatchFactory.aCrashBatch()
        }) {
            assertThat(batch.samples()).hasSize(calm.samples().size());
            assertThat(batch.samples().stream().filter(s -> s.accelerationMetersPerSecondSquared() != 0.0
                            || s.speedKilometersPerHour() != 50.0)).hasSize(1);
        }
    }

    @Test
    public void eventBatchesSitInTheirBands() {
        var medium = BatchFactory.aMediumBrakeBatch().samples().get(2);
        var hard = BatchFactory.aHardBrakeBatch().samples().get(2);
        var speeding = BatchFactory.aSpeedingBatch().samples().get(2);
        var crash = BatchFactory.aCrashBatch().samples().get(2);
        assertThat(medium.accelerationMetersPerSecondSquared()).isBetween(-7.0, -5.0);
        assertThat(hard.accelerationMetersPerSecondSquared()).isLessThanOrEqualTo(-7.0);
        assertThat(speeding.speedKilometersPerHour()).isBetween(140.0, 160.0);
        assertThat(crash.speedKilometersPerHour()).isZero();
        assertThat(crash.accelerationMetersPerSecondSquared()).isLessThan(-20.0);
    }
}
