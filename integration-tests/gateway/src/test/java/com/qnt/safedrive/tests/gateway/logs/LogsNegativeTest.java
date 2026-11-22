package com.qnt.safedrive.tests.gateway.logs;

import static com.qnt.safedrive.tests.core.factory.LoginFactory.aLogin;

import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class LogsNegativeTest extends AbstractLogsTest {

    @Test(groups = Groups.REGRESSION, description = "A rejected login is logged with its correlation ID, and the log has neither the wrong password nor the email")
    @Story("Login logs")
    public void rejectedLoginLogsHaveNoSecrets() {
        String correlationId = Ids.correlation();
        String wrongPassword = "wrong-" + Ids.driver();
        String email = "nobody-" + Ids.driver() + "@safedrive.test";

        Validator.with(gatewayClient().token(aLogin().withEmail(email).withPassword(wrongPassword), Ids.driver(), correlationId))
                .hasStatus(Http.UNAUTHORIZED);

        Validator.withLogLines(tracer.awaitLog(Services.GATEWAY, correlationId))
                .hasLines()
                .neverLog(wrongPassword, email);
    }
}
