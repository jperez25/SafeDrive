package com.qnt.safedrive.tests.core.model.constants;

import java.util.Set;

/** HTTP status codes, headers and what must never reach Allure attachments. */
public final class Http {

    private Http() {}

    public static final int OK = 200;
    public static final int ACCEPTED = 202;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int CONFLICT = 409;

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    /** Sent on the gateway token request only: the driver the token is for. */
    public static final String DRIVER_HEADER = "X-Driver-Id";
    public static final String BEARER_PREFIX = "Bearer ";

    /** Header names (lower case) whose values never reach Allure attachments. */
    public static final Set<String> REDACTED_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "x-api-key", "idempotency-key");

    /** JSON field names (lower case) whose values never reach Allure attachments. Secrets only for now; add PII as needed. */
    public static final Set<String> REDACTED_FIELDS =
            Set.of("password", "secret", "token", "accesstoken", "refreshtoken", "apikey", "authorization");

    public static final String REDACTED = "[REDACTED]";
}
