package com.qnt.safedrive.tests.core.model.constants;

/** Status values in response bodies. */
public final class Statuses {

    private Statuses() {}

    /** The {@code status} field of an ingest response body. */
    public static final String INGEST_ACCEPTED = "accepted";
    public static final String INGEST_DUPLICATE = "duplicate";
    public static final String INGEST_CONFLICT = "conflict";
    public static final String INGEST_INVALID = "invalid";

    public static final String HEALTH_UP = "UP";
}
