package com.qnt.safedrive.tests.core.model.constants;

/** Premium bands of policy-rating (see docs/architecture/services.md). */
public final class Bands {

    private Bands() {}

    public static final String EXCELLENT = "excellent";
    public static final String GOOD = "good";
    public static final String AVERAGE = "average";
    public static final String POOR = "poor";
    public static final String HIGH_RISK = "high_risk";

    /** The base premium of a driver seen for the first time (service default). */
    public static final double DEFAULT_BASE_PREMIUM = 120.00;
}
