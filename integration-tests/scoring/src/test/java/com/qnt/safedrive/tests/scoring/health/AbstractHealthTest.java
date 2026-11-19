package com.qnt.safedrive.tests.scoring.health;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.client.HealthClient;
import com.qnt.safedrive.tests.core.model.constants.Services;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

/** Shared setup for the scoring health feature. */
@Epic("scoring")
@Feature("Health")
public abstract class AbstractHealthTest extends AbstractTest {

    protected HealthClient health() {
        return healthClient(Services.SCORING);
    }
}
