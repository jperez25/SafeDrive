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

    /** The {@code status} of a reward: granted but not yet delivered, or delivered to the provider. */
    public static final String REWARD_PENDING = "pending";
    public static final String REWARD_NOTIFIED = "notified";

    /** The {@code status} of a claim: opened with the driver's policy, or waiting because the policy was not known yet. */
    public static final String CLAIM_OPENED = "opened";
    public static final String CLAIM_PENDING_POLICY = "pending_policy";

    public static final String HEALTH_UP = "UP";
}
