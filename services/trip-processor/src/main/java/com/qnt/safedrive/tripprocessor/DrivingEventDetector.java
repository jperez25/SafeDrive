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
        hard_brake, harsh_accel, speeding, distraction, crash
    }

    public enum Severity {
        low, medium, high
    }

    public record Detected(Kind kind, Severity severity, Instant ts, double lat, double lon) {}

    static final double BRAKE_LOW = -3.5, BRAKE_MEDIUM = -5.0, BRAKE_HIGH = -7.0;      // m/s2
    static final double ACCEL_LOW = 3.0, ACCEL_MEDIUM = 4.5, ACCEL_HIGH = 6.0;         // m/s2
    static final double SPEED_LOW = 120, SPEED_MEDIUM = 140, SPEED_HIGH = 160;         // km/h, no road data yet
    static final double PHONE_MIN_SPEED = 10, PHONE_MEDIUM = 50, PHONE_HIGH = 100;     // km/h while the phone is in use

    static final double CRASH_SPEED_DROP = 40, CRASH_ACCEL = -15;                      // km/h between samples, m/s2
    static final double CRASH_STOPPED_BELOW = 5;                                       // km/h
    static final long CRASH_STOP_WINDOW_S = 10;                                        // must stay stopped this long
    static final double CRASH_MEDIUM = 50, CRASH_HIGH = 80;                            // km/h before the impact

    private DrivingEventDetector() {}

    public static List<Detected> detect(List<TripRaw.Sample> samples) {
        List<Detected> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (kind != Kind.crash) {
                detect(kind, rule(kind), samples, out);
            }
        }
        List<Detected> crashes = new ArrayList<>();
        detectCrashes(samples, crashes);
        // The impact sample also breaks the hard-brake rule; the crash replaces it so one impact is not scored twice.
        out.removeIf(d -> d.kind() == Kind.hard_brake && crashes.stream().anyMatch(c -> c.ts().equals(d.ts())));
        out.addAll(crashes);
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
            case crash -> throw new IllegalArgumentException("crash is not a single-sample rule");
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

    /**
     * A crash is a sudden deceleration (speed drop between consecutive samples, or extreme negative
     * acceleration) followed by a stop that lasts the whole window. A hard brake that keeps moving is
     * not a crash. The event is stamped with the impact sample; severity follows the speed before it.
     */
    private static void detectCrashes(List<TripRaw.Sample> samples, List<Detected> out) {
        int i = 0;
        while (i < samples.size()) {
            TripRaw.Sample s = samples.get(i);
            double before = i > 0 ? samples.get(i - 1).speedKmh() : s.speedKmh();
            boolean impact = before - s.speedKmh() >= CRASH_SPEED_DROP || s.accelMs2() <= CRASH_ACCEL;
            int end = i + 1;
            while (end < samples.size() && !samples.get(end).ts().isAfter(s.ts().plusSeconds(CRASH_STOP_WINDOW_S))) {
                end++;
            }
            if (impact && stopped(samples.subList(i + 1, end), s)) {
                double speed = Math.max(before, s.speedKmh());
                Severity severity = speed >= CRASH_HIGH ? Severity.high : speed >= CRASH_MEDIUM ? Severity.medium : Severity.low;
                out.add(new Detected(Kind.crash, severity, s.ts(), s.lat(), s.lon()));
                i = end;   // the stopped window belongs to this crash
            } else {
                i++;
            }
        }
    }

    private static boolean stopped(List<TripRaw.Sample> after, TripRaw.Sample impact) {
        if (after.isEmpty()) {
            return impact.speedKmh() < CRASH_STOPPED_BELOW;   // the trip ends at the impact
        }
        return after.stream().allMatch(a -> a.speedKmh() < CRASH_STOPPED_BELOW);
    }
}
