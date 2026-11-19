package com.qnt.safedrive.tests.core.model.constants;

/** Status values in response bodies. */
public final class Statuses {

    private Statuses() {}

    /** The {@code status} field of an ingest response body. */
    public static final String INGEST_ACCEPTED = "accepted";
    public static final String INGEST_DUPLICATE = "duplicate";
    public static final String INGEST_CONFLICT = "conflict";
    public static final String INGEST_INVALID = "invalid";

    /** The {@code status} field of the score API when the driver has no score yet. */
    public static final String SCORE_NOT_FOUND = "not_found";

    /** The {@code status} field of the premium API when no score was applied for the driver yet. */
    public static final String PREMIUM_NOT_FOUND = "not_found";

    public static final String HEALTH_UP = "UP";
}
