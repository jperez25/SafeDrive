package com.qnt.safedrive.tests.load;

import java.time.Duration;

/**
 * How hard a simulation pushes: {@code -Dload.profile=smoke|baseline|stress}. Arrival rate is in new virtual
 * drivers per second (each sends one trip's batch), ramped up first and then held.
 * The rates are provisional: run {@code baseline} on the target hardware, record the numbers and pin the
 * assertion thresholds in the simulations from them.
 */
public record LoadProfile(String name, int ratePerSecond, Duration ramp, Duration hold) {

    public static LoadProfile current() {
        String name = System.getProperty("load.profile", "smoke");
        return switch (name) {
            case "smoke" -> new LoadProfile(name, 2, Duration.ofSeconds(5), Duration.ofSeconds(25));
            case "baseline" -> new LoadProfile(name, 10, Duration.ofSeconds(30), Duration.ofMinutes(2));
            case "stress" -> new LoadProfile(name, 50, Duration.ofMinutes(1), Duration.ofMinutes(2));
            default -> throw new IllegalArgumentException("unknown load.profile '" + name + "' (smoke|baseline|stress)");
        };
    }
}
