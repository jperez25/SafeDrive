package com.qnt.safedrive.tests.telemetryingest.health;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.client.HealthClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

/** Shared setup for the telemetry-ingest health feature. */
@Epic("telemetry-ingest")
@Feature("Health")
public abstract class AbstractHealthTest extends AbstractTest {

    protected HealthClient health() {
        return healthClient(Services.TELEMETRY_INGEST);
    }
}
