package com.qnt.safedrive.tripprocessor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Threshold detection over the ordered samples of a trip. Consecutive samples that break the same
 * rule are one event: it is stamped with the first sample of the run and the worst severity in it.
 */
public final class DrivingEventDetector {

    public enum Kind {
        hard_brake, harsh_accel, speeding, distraction
    }

    public enum Severity {
        low, medium, high
    }

    public record Detected(Kind kind, Severity severity, Instant ts, double lat, double lon) {}

    static final double BRAKE_LOW = -3.5, BRAKE_MEDIUM = -5.0, BRAKE_HIGH = -7.0;      // m/s2
    static final double ACCEL_LOW = 3.0, ACCEL_MEDIUM = 4.5, ACCEL_HIGH = 6.0;         // m/s2
    static final double SPEED_LOW = 120, SPEED_MEDIUM = 140, SPEED_HIGH = 160;         // km/h, no road data yet
    static final double PHONE_MIN_SPEED = 10, PHONE_MEDIUM = 50, PHONE_HIGH = 100;     // km/h while the phone is in use

    private DrivingEventDetector() {}

    public static List<Detected> detect(List<TripRaw.Sample> samples) {
        List<Detected> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            detect(kind, rule(kind), samples, out);
        }
        out.sort(java.util.Comparator.comparing(Detected::ts));
        return out;
    }

    private static Function<TripRaw.Sample, Severity> rule(Kind kind) {
        return switch (kind) {
            case hard_brake -> s -> s.accelMs2() <= BRAKE_HIGH ? Severity.high
                    : s.accelMs2() <= BRAKE_MEDIUM ? Severity.medium
                    : s.accelMs2() <= BRAKE_LOW ? Severity.low : null;
            case harsh_accel -> s -> s.accelMs2() >= ACCEL_HIGH ? Severity.high
                    : s.accelMs2() >= ACCEL_MEDIUM ? Severity.medium
                    : s.accelMs2() >= ACCEL_LOW ? Severity.low : null;
            case speeding -> s -> s.speedKmh() >= SPEED_HIGH ? Severity.high
                    : s.speedKmh() >= SPEED_MEDIUM ? Severity.medium
                    : s.speedKmh() >= SPEED_LOW ? Severity.low : null;
            case distraction -> s -> !s.phoneInUse() || s.speedKmh() < PHONE_MIN_SPEED ? null
                    : s.speedKmh() >= PHONE_HIGH ? Severity.high
                    : s.speedKmh() >= PHONE_MEDIUM ? Severity.medium : Severity.low;
        };
    }

    private static void detect(
            Kind kind, Function<TripRaw.Sample, Severity> rule, List<TripRaw.Sample> samples, List<Detected> out) {
        TripRaw.Sample start = null;
        Severity worst = null;
        for (TripRaw.Sample s : samples) {
            Severity severity = rule.apply(s);
            if (severity != null) {
                if (start == null) {
                    start = s;
                    worst = severity;
                } else if (severity.compareTo(worst) > 0) {
                    worst = severity;
                }
            } else if (start != null) {
                out.add(new Detected(kind, worst, start.ts(), start.lat(), start.lon()));
                start = null;
            }
        }
        if (start != null) {
            out.add(new Detected(kind, worst, start.ts(), start.lat(), start.lon()));
        }
    }
}
