package com.qnt.safedrive.tests.core.utils;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** Polls until a result shows up or the timeout passes. Replaces fixed sleeps for async results. */
public final class Waiter {

    private final Duration timeout;
    private final Duration interval;

    public Waiter(Duration timeout, Duration interval) {
        this.timeout = timeout;
        this.interval = interval;
    }

    /** Returns the first present result, or throws {@link WaitTimeoutException} describing what never happened. */
    public <T> T until(String description, Supplier<Optional<T>> probe) {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException lastError = null;
        while (true) {
            try {
                Optional<T> result = probe.get();
                if (result.isPresent()) {
                    return result.get();
                }
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e; // transient (e.g. kubectl hiccup): keep polling until the deadline
            }
            if (System.nanoTime() >= deadline) {
                throw new WaitTimeoutException("timed out after " + timeout + " waiting for: " + description, lastError);
            }
            pause();
        }
    }

    /**
     * Like {@link #until} but bounded by a number of tries instead of a deadline: the probe runs at most
     * {@code attempts} times with the waiter's interval between tries (none after the last one).
     */
    public <T> T untilAttempts(String description, int attempts, Supplier<Optional<T>> probe) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Optional<T> result = probe.get();
                if (result.isPresent()) {
                    return result.get();
                }
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e;
            }
            if (attempt < attempts) {
                pause();
            }
        }
        throw new WaitTimeoutException("gave up after " + attempts + " attempts waiting for: " + description, lastError);
    }

    public void untilTrue(String description, Supplier<Boolean> condition) {
        until(description, () -> condition.get() ? Optional.of(Boolean.TRUE) : Optional.empty());
    }

    /**
     * Asserts that something does NOT happen: the probe must stay empty for the whole window.
     * The one place where waiting the full duration is the point of the test.
     */
    public <T> void staysEmpty(String description, Duration window, Supplier<Optional<T>> probe) {
        long deadline = System.nanoTime() + window.toNanos();
        do {
            Optional<T> result = probe.get();
            if (result.isPresent()) {
                throw new AssertionError("expected nothing, but found: " + description + " -> " + result.get());
            }
            pause();
        } while (System.nanoTime() < deadline);
    }

    private void pause() {
        java.util.concurrent.locks.LockSupport.parkNanos(interval.toNanos());
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("interrupted while waiting");
        }
    }

    public static final class WaitTimeoutException extends AssertionError {
        public WaitTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
