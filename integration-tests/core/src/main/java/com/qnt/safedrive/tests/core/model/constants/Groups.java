package com.qnt.safedrive.tests.core.model.constants;

/**
 * TestNG groups, one per test (never both). {@code SMOKE}: fast checks that the service is up and its
 * main path works (health always lives here). {@code REGRESSION}: everything else. Suites select by
 * package and group; {@code master} runs smoke first and {@link SmokeGateListener} skips regression if
 * any smoke test fails.
 */
public final class Groups {

    private Groups() {}

    public static final String SMOKE = "smoke";
    public static final String REGRESSION = "regression";
}
