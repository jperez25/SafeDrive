package com.qnt.safedrive.tests.gateway.tokenvalidation;

import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TokenValidationPositiveTest extends AbstractTokenValidationTest {

    @Test(groups = Groups.SMOKE, description = "A token from the gateway is accepted and the call reaches the service (the service's own 404 comes back)")
    @Story("Valid token")
    public void validTokenReachesTheService() {
        Validator.with(callWith(fullAccessFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.NOT_FOUND)
                .premiumBody()
                .hasStatus(Statuses.PREMIUM_NOT_FOUND);
    }

    @Test(groups = Groups.REGRESSION, description = "One token can be used for many calls until it expires")
    @Story("Valid token")
    public void tokenCanBeReused() {
        String authorization = fullAccessFor(Ids.driver());

        Validator.with(callWith(authorization, Ids.correlation())).hasStatus(Http.NOT_FOUND);
        Validator.with(callWith(authorization, Ids.correlation())).hasStatus(Http.NOT_FOUND);
    }
}
