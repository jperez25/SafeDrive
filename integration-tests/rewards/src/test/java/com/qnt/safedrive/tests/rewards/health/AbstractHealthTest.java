package com.qnt.safedrive.tests.rewards.health;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.client.HealthClient;
import com.qnt.safedrive.tests.core.model.constants.Services;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

/** Shared setup for the rewards health feature. */
@Epic("rewards")
@Feature("Health")
public abstract class AbstractHealthTest extends AbstractTest {

    protected HealthClient health() {
        return healthClient(Services.REWARDS);
    }
}
