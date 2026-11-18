package com.qnt.safedrive.telemetryingest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cheap validation only, no business logic. Structural problems and unordered timestamps reject the
 * whole batch; bad individual samples are dropped and reported.
 */
@Component
public class BatchValidator {

    static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    static final Duration MAX_AGE = Duration.ofDays(7);
    static final double MAX_SPEED_KMH = 300;
    static final double MAX_ACCEL_MS2 = 50;
    static final int MAX_SAMPLES = 5000;

    public record RejectedSample(int index, String reason) {}

    /** Either {@code errors} is non-empty (reject with 400) or {@code accepted} holds the clean batch. */
    public record Result(List<String> errors, TelemetryBatch accepted, List<RejectedSample> rejected) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private final Clock clock;

    public BatchValidator() {
        this(Clock.systemUTC());
    }

    BatchValidator(Clock clock) {
        this.clock = clock;
    }

    public Result validate(TelemetryBatch batch) {
        List<String> errors = new ArrayList<>();
        if (batch == null) {
            return new Result(List.of("body is required"), null, List.of());
        }
        requireText(errors, "batchId", batch.batchId());
        requireText(errors, "tripId", batch.tripId());
        requireText(errors, "driverId", batch.driverId());
        requireText(errors, "deviceId", batch.deviceId());
        if (batch.batchSeq() == null || batch.batchSeq() < 0) {
            errors.add("batchSeq must be an integer >= 0");
        }
        if (batch.last() == null) {
            errors.add("last is required");
        }
        if (batch.samples() == null || batch.samples().isEmpty()) {
            errors.add("samples must contain at least one sample");
        } else if (batch.samples().size() > MAX_SAMPLES) {
            errors.add("samples must contain at most " + MAX_SAMPLES + " samples");
        }
        if (!errors.isEmpty()) {
            return new Result(errors, null, List.of());
        }

        Instant now = clock.instant();
        List<TelemetryBatch.Sample> clean = new ArrayList<>();
        List<RejectedSample> rejected = new ArrayList<>();
        Instant previous = null;
        for (int i = 0; i < batch.samples().size(); i++) {
            TelemetryBatch.Sample sample = batch.samples().get(i);
            Instant ts = parse(sample == null ? null : sample.ts());
            String problem = problem(sample, ts, now);
            if (problem != null) {
                rejected.add(new RejectedSample(i, problem));
                continue;
            }
            if (previous != null && ts.isBefore(previous)) {
                errors.add("samples must be ordered by ts (sample " + i + " is earlier than the previous one)");
                break;
            }
            previous = ts;
            clean.add(sample);
        }
        if (errors.isEmpty() && clean.isEmpty()) {
            errors.add("no valid samples in batch");
        }
        if (!errors.isEmpty()) {
            return new Result(errors, null, List.of());
        }
        TelemetryBatch accepted = new TelemetryBatch(
                batch.batchId(), batch.tripId(), batch.driverId(), batch.deviceId(), batch.batchSeq(), batch.last(), clean);
        return new Result(List.of(), accepted, rejected);
    }

    private static void requireText(List<String> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
        }
    }

    private static Instant parse(String ts) {
        if (ts == null) {
            return null;
        }
        try {
            return Instant.parse(ts);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String problem(TelemetryBatch.Sample s, Instant ts, Instant now) {
        if (s == null) {
            return "sample is null";
        }
        if (ts == null) {
            return "ts missing or not an ISO-8601 UTC instant";
        }
        if (s.lat() == null || s.lon() == null || s.speedKmh() == null || s.accelMs2() == null
                || s.heading() == null || s.phoneInUse() == null) {
            return "missing field";
        }
        if (ts.isAfter(now.plus(MAX_FUTURE))) {
            return "ts is in the future";
        }
        if (ts.isBefore(now.minus(MAX_AGE))) {
            return "ts is too old";
        }
        if (!inRange(s.lat(), -90, 90)) {
            return "lat out of range";
        }
        if (!inRange(s.lon(), -180, 180)) {
            return "lon out of range";
        }
        if (!inRange(s.speedKmh(), 0, MAX_SPEED_KMH)) {
            return "speedKmh out of range";
        }
        if (!inRange(s.accelMs2(), -MAX_ACCEL_MS2, MAX_ACCEL_MS2)) {
            return "accelMs2 out of range";
        }
        if (!inRange(s.heading(), 0, 360)) {
            return "heading out of range";
        }
        return null;
    }

    private static boolean inRange(double v, double min, double max) {
        return !Double.isNaN(v) && v >= min && v <= max;
    }
}
