package com.qnt.safedrive.commons;

import java.util.UUID;
import org.slf4j.MDC;

/** Correlation ID: created at the edge, then carried by every event, log line and HTTP call. */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String KAFKA_HEADER = "correlationId";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Returns the ID if present, otherwise a new one (used at the edge). */
    public static String orGenerate(String incoming) {
        return incoming == null || incoming.isBlank() ? generate() : incoming;
    }

    /** The ID bound to the current thread's log context, or null. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Binds the ID to the log context until the returned scope is closed. */
    public static Scope bind(String correlationId) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        return () -> {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
