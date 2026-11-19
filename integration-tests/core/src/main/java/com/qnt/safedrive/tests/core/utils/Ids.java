package com.qnt.safedrive.tests.core.utils;

import java.util.UUID;

/**
 * The one place that defines how test identifiers look: a short prefix plus a random UUID, so every
 * call is unique (parallel-safe) and test traffic is easy to find in logs and topics. Stateless.
 */
public final class Ids {

    private Ids() {}

    public static String correlation() {
        return next("it");
    }

    /** An event ID: a bare UUID, because the envelope contract requires that format. */
    public static String event() {
        return UUID.randomUUID().toString();
    }

    public static String batch() {
        return next("b");
    }

    public static String trip() {
        return next("t");
    }

    public static String driver() {
        return next("d");
    }

    public static String device() {
        return next("dev");
    }

    private static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
