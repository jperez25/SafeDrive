package com.qnt.safedrive.tests.policyrating.health;

import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class HealthPositiveTest extends AbstractHealthTest {

    @Test(groups = Groups.SMOKE, description = "Liveness reports UP")
    @Story("Liveness")
    public void livenessIsUp() {
        Validator.with(health().liveness())
                .hasStatus(Http.OK)
                .healthBody()
                .hasStatus(Statuses.HEALTH_UP);
    }

    @Test(groups = Groups.SMOKE, description = "Readiness reports UP (database is reachable)")
    @Story("Readiness")
    public void readinessIsUp() {
        Validator.with(health().readiness())
                .hasStatus(Http.OK)
                .healthBody()
                .hasStatus(Statuses.HEALTH_UP);
    }
}
