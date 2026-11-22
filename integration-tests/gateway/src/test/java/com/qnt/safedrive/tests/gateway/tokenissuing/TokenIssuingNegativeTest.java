package com.qnt.safedrive.tests.gateway.tokenissuing;

import static com.qnt.safedrive.tests.core.factory.LoginFactory.aLogin;

import com.qnt.safedrive.tests.core.model.constants.GatewayMessages;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TokenIssuingNegativeTest extends AbstractTokenIssuingTest {

    @Test(groups = Groups.REGRESSION, description = "A wrong password gets 401 and no token")
    @Story("Bad credentials")
    public void wrongPasswordIsRejected() {
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().token(aLogin().withPassword("not-the-password"), Ids.driver(), correlationId))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_UNAUTHORIZED)
                .hasMessage(GatewayMessages.INVALID_CREDENTIALS)
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "An unknown user gets the same 401 and message as a wrong password, so the answer does not reveal which accounts exist")
    @Story("Bad credentials")
    public void unknownUserLooksLikeAWrongPassword() {
        Validator.with(gatewayClient().token(aLogin().withEmail("nobody-" + Ids.driver() + "@safedrive.test"), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_UNAUTHORIZED)
                .hasMessage(GatewayMessages.INVALID_CREDENTIALS);
    }

    @Test(groups = Groups.REGRESSION, description = "A disabled user with the right password gets the same 401 and message")
    @Story("Bad credentials")
    public void disabledUserIsRejected() {
        Validator.with(gatewayClient().token(aLogin().withEmail(GatewayUsers.DISABLED_EMAIL), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_UNAUTHORIZED)
                .hasMessage(GatewayMessages.INVALID_CREDENTIALS);
    }

    @Test(groups = Groups.REGRESSION, description = "An email written as an SQL injection is just an unknown user")
    @Story("Bad credentials")
    public void sqlInjectionInTheEmailIsRejected() {
        Validator.with(gatewayClient().token(aLogin().withEmail("' OR '1'='1' --"), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.UNAUTHORIZED)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.INVALID_CREDENTIALS);
    }

    @Test(groups = Groups.REGRESSION, description = "A token request without the X-Driver-Id header is a 400")
    @Story("Bad request")
    public void missingDriverHeaderIsRejected() {
        Validator.with(gatewayClient().token(aLogin(), null, Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "A blank X-Driver-Id header is a 400")
    @Story("Bad request")
    public void blankDriverHeaderIsRejected() {
        Validator.with(gatewayClient().token(aLogin(), " ", Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "A token request without an email is a 400")
    @Story("Bad request")
    public void missingEmailIsRejected() {
        Validator.with(gatewayClient().token(aLogin().withEmail(null), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "A token request with an empty password is a 400")
    @Story("Bad request")
    public void emptyPasswordIsRejected() {
        Validator.with(gatewayClient().token(aLogin().withPassword(""), Ids.driver(), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "A token request with no body is a 400")
    @Story("Bad request")
    public void emptyBodyIsRejected() {
        Validator.with(gatewayClient().tokenRaw("", Ids.driver(), Ids.correlation())).hasStatus(Http.BAD_REQUEST);
    }

    @Test(groups = Groups.REGRESSION, description = "A token request with malformed JSON is a 400")
    @Story("Bad request")
    public void malformedJsonIsRejected() {
        Validator.with(gatewayClient().tokenRaw("{not json", Ids.driver(), Ids.correlation())).hasStatus(Http.BAD_REQUEST);
    }
}
