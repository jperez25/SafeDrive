package com.qnt.safedrive.tests.core.model.constants;

/** The scopes of a gateway token; each one allows one kind of call. */
public final class Scopes {

    private Scopes() {}

    public static final String TELEMETRY_WRITE = "telemetry:write";
    public static final String SCORE_READ = "score:read";
    public static final String PREMIUM_READ = "premium:read";
    public static final String REWARDS_READ = "rewards:read";
    public static final String CLAIMS_READ = "claims:read";
}
