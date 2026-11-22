package com.qnt.safedrive.tests.core.model.constants;

/** Service names, as used in environment files and the {@code source} of events. */
public final class Services {

    private Services() {}

    public static final String TELEMETRY_INGEST = "telemetry-ingest";
    public static final String TRIP_PROCESSOR = "trip-processor";
    public static final String SCORING = "scoring";
    public static final String POLICY_RATING = "policy-rating";
    public static final String REWARDS = "rewards";
    public static final String CLAIMS = "claims";
    /** The PoC entry point for the UI (JWT login and scoped proxy). */
    public static final String GATEWAY = "gateway";
    /** The mocked external notification provider (WireMock) that rewards calls. */
    public static final String NOTIFICATION_MOCK = "notification-mock";
}
