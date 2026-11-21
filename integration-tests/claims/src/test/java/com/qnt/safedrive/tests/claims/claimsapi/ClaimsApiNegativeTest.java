package com.qnt.safedrive.tests.claims.claimsapi;

import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.UUID;
import org.testng.annotations.Test;

public class ClaimsApiNegativeTest extends AbstractClaimsApiTest {

    @Test(groups = Groups.REGRESSION, description = "A driver without claims gets 200 and an empty list, not 404")
    @Story("Unknown driver")
    public void unknownDriverHasNoClaims() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(claimsClient().getClaims(driverId, correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .claimsBody()
                .hasDriverId(driverId)
                .hasClaimCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "A claim ID that does not exist is 404")
    @Story("Unknown claim")
    public void unknownClaimIsNotFound() {
        String correlationId = Ids.correlation();

        Validator.with(claimsClient().getClaim(UUID.randomUUID().toString(), correlationId))
                .hasStatus(Http.NOT_FOUND)
                .hasCorrelationHeader(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A claim ID that is not a UUID is a 400, not a server error")
    @Story("Unknown claim")
    public void malformedClaimIdIsABadRequest() {
        Validator.with(claimsClient().getClaim("not-a-uuid", null)).hasStatus(Http.BAD_REQUEST);
    }
}
