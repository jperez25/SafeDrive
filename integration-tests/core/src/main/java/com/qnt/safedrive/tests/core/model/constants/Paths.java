package com.qnt.safedrive.tests.core.model.constants;

/** URL paths of the services. */
public final class Paths {

    private Paths() {}

    public static final String INGEST_BATCHES = "/v1/telemetry/batches";
    public static final String HEALTH = "/actuator/health";
    public static final String HEALTH_LIVENESS = "/actuator/health/liveness";
    public static final String HEALTH_READINESS = "/actuator/health/readiness";
}
