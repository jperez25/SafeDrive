package com.qnt.safedrive.telemetryingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchValidatorTest {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private final BatchValidator validator = new BatchValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    static TelemetryBatch.Sample sample(String ts) {
        return new TelemetryBatch.Sample(ts, 52.52, 13.40, 50.0, 0.5, 90.0, false);
    }

    static TelemetryBatch batch(TelemetryBatch.Sample... samples) {
        return new TelemetryBatch("b-1", "t-1", "d-1", "dev-1", 0, false, List.of(samples));
    }

    @Test
    void validBatchIsAcceptedUnchanged() {
        var result = validator.validate(batch(sample("2026-11-18T09:59:00Z"), sample("2026-11-18T09:59:01Z")));
        assertThat(result.valid()).isTrue();
        assertThat(result.accepted().samples()).hasSize(2);
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void missingBatchFieldsRejectWholeBatch() {
        var result = validator.validate(new TelemetryBatch(null, " ", "d", "dev", -1, null, List.of()));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(5);
    }

    @Test
    void nullBodyIsRejected() {
        assertThat(validator.validate(null).valid()).isFalse();
    }

    @Test
    void invalidSamplesAreDroppedAndReported() {
        var badLat = new TelemetryBatch.Sample("2026-11-18T09:59:01Z", 91.0, 13.4, 50.0, 0.5, 90.0, false);
        var future = sample("2026-11-18T10:30:00Z");
        var old = sample("2026-11-01T10:00:00Z");
        var result = validator.validate(batch(sample("2026-11-18T09:59:00Z"), badLat, future, old));
        assertThat(result.valid()).isTrue();
        assertThat(result.accepted().samples()).hasSize(1);
        assertThat(result.rejected()).extracting(BatchValidator.RejectedSample::index).containsExactly(1, 2, 3);
        assertThat(result.rejected()).extracting(BatchValidator.RejectedSample::reason)
                .containsExactly("lat out of range", "ts is in the future", "ts is too old");
    }

    @Test
    void speedAccelAndHeadingBoundsAreChecked() {
        var speed = new TelemetryBatch.Sample("2026-11-18T09:59:01Z", 1.0, 1.0, 301.0, 0.0, 0.0, false);
        var accel = new TelemetryBatch.Sample("2026-11-18T09:59:02Z", 1.0, 1.0, 10.0, -51.0, 0.0, false);
        var heading = new TelemetryBatch.Sample("2026-11-18T09:59:03Z", 1.0, 1.0, 10.0, 0.0, 361.0, false);
        var result = validator.validate(batch(sample("2026-11-18T09:59:00Z"), speed, accel, heading));
        assertThat(result.rejected()).extracting(BatchValidator.RejectedSample::reason)
                .containsExactly("speedKmh out of range", "accelMs2 out of range", "heading out of range");
    }

    @Test
    void missingFieldAndBadTimestampAreRejectedPerSample() {
        var missing = new TelemetryBatch.Sample("2026-11-18T09:59:01Z", null, 1.0, 1.0, 1.0, 1.0, false);
        var badTs = sample("yesterday");
        var result = validator.validate(batch(sample("2026-11-18T09:59:00Z"), missing, badTs));
        assertThat(result.rejected()).hasSize(2);
        assertThat(result.accepted().samples()).hasSize(1);
    }

    @Test
    void unorderedTimestampsRejectWholeBatch() {
        var result = validator.validate(batch(sample("2026-11-18T09:59:05Z"), sample("2026-11-18T09:59:00Z")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().getFirst()).contains("ordered");
    }

    @Test
    void equalTimestampsAreAllowed() {
        var result = validator.validate(batch(sample("2026-11-18T09:59:00Z"), sample("2026-11-18T09:59:00Z")));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void batchWithNoValidSamplesIsRejected() {
        var result = validator.validate(batch(sample("2026-11-01T00:00:00Z")));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("no valid samples in batch");
    }

    @Test
    void tooManySamplesIsRejected() {
        List<TelemetryBatch.Sample> many = new ArrayList<>();
        for (int i = 0; i <= BatchValidator.MAX_SAMPLES; i++) {
            many.add(sample("2026-11-18T09:59:00Z"));
        }
        var result = validator.validate(new TelemetryBatch("b", "t", "d", "dev", 0, true, many));
        assertThat(result.valid()).isFalse();
    }
}
