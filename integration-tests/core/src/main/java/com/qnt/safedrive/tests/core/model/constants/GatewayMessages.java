package com.qnt.safedrive.tests.core.model.constants;

/** The {@code message} of gateway error bodies. */
public final class GatewayMessages {

    private GatewayMessages() {}

    /** Same text for a wrong password, an unknown user and a disabled user, so the answer does not reveal which. */
    public static final String INVALID_CREDENTIALS = "invalid credentials";
    public static final String MISSING_BEARER_TOKEN = "missing bearer token";
    public static final String INVALID_TOKEN = "invalid or expired token";
    public static final String UNKNOWN_PATH = "unknown path";

    public static String missingScope(String scope) {
        return "missing scope " + scope;
    }
}
