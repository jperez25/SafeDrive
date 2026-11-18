package com.qnt.safedrive.commons;

import java.util.Map;

/** Config comes from environment variables only. */
public final class Env {

    private Env() {}

    public static String get(String name, String defaultValue) {
        return get(System.getenv(), name, defaultValue);
    }

    public static String require(String name) {
        return require(System.getenv(), name);
    }

    static String get(Map<String, String> env, String name, String defaultValue) {
        String value = env.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String require(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
