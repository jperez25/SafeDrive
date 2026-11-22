package com.qnt.safedrive.tests.core.model.constants;

/** Users seeded by the gateway's {@code V2__seed_users.sql} (dummy local data, one shared password). */
public final class GatewayUsers {

    private GatewayUsers() {}

    public static final String PASSWORD = "safedrive-poc";
    /** Has every scope. */
    public static final String DRIVER_EMAIL = "driver1@safedrive.test";
    public static final String SECOND_DRIVER_EMAIL = "driver2@safedrive.test";
    /** Has only {@code score:read}. */
    public static final String READ_ONLY_EMAIL = "readonly@safedrive.test";
    /** Right password, but the account is disabled. */
    public static final String DISABLED_EMAIL = "disabled@safedrive.test";

    public static final String ISSUER = "safedrive-gateway";
    public static final String AUDIENCE = "safedrive";
    public static final long TOKEN_TTL_SECONDS = 3600;
    public static final String TOKEN_TYPE = "Bearer";
    public static final String SIGNING_ALGORITHM = "RS256";
}
