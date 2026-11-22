package com.qnt.safedrive.tests.gateway.scopes;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.GatewayMessages;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Scopes;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class ScopeNegativeTest extends AbstractScopeTest {

    @Test(groups = Groups.REGRESSION, description = "Without premium:read the premium route is 403 and names the missing scope")
    @Story("premium:read")
    public void premiumNeedsItsScope() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().get(Paths.driverPremium(driverId), readOnlyFor(driverId), correlationId))
                .hasStatus(Http.FORBIDDEN)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_FORBIDDEN)
                .hasMessage(GatewayMessages.missingScope(Scopes.PREMIUM_READ))
                .hasCorrelationId(correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Without rewards:read the rewards route is 403")
    @Story("rewards:read")
    public void rewardsNeedTheirScope() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverRewards(driverId), readOnlyFor(driverId), Ids.correlation()))
                .hasStatus(Http.FORBIDDEN)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.missingScope(Scopes.REWARDS_READ));
    }

    @Test(groups = Groups.REGRESSION, description = "Without claims:read the claims list is 403")
    @Story("claims:read")
    public void claimsListNeedsItsScope() {
        String driverId = Ids.driver();

        Validator.with(gatewayClient().get(Paths.driverClaims(driverId), readOnlyFor(driverId), Ids.correlation()))
                .hasStatus(Http.FORBIDDEN)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.missingScope(Scopes.CLAIMS_READ));
    }

    @Test(groups = Groups.REGRESSION, description = "Without claims:read a single claim is 403")
    @Story("claims:read")
    public void singleClaimNeedsItsScope() {
        Validator.with(gatewayClient().get(Paths.claim(Ids.event()), readOnlyFor(Ids.driver()), Ids.correlation()))
                .hasStatus(Http.FORBIDDEN)
                .gatewayErrorBody()
                .hasMessage(GatewayMessages.missingScope(Scopes.CLAIMS_READ));
    }

    @Test(groups = Groups.REGRESSION, description = "Without telemetry:write posting a batch is 403")
    @Story("telemetry:write")
    public void ingestNeedsItsScope() {
        Batch batch = aBatch();

        Validator.with(gatewayClient().postBatch(batch, readOnlyFor(batch.driverId()), Ids.correlation()))
                .hasStatus(Http.FORBIDDEN)
                .gatewayErrorBody()
                .hasStatus(Statuses.GATEWAY_FORBIDDEN)
                .hasMessage(GatewayMessages.missingScope(Scopes.TELEMETRY_WRITE));
    }

    @Test(groups = Groups.REGRESSION, description = "A refused batch never reaches ingest: nothing is published for it")
    @Story("telemetry:write")
    public void refusedBatchIsNotForwarded() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        Validator.with(gatewayClient().postBatch(batch, readOnlyFor(batch.driverId()), correlationId)).hasStatus(Http.FORBIDDEN);

        waiter.staysEmpty("trip.raw events for correlation ID " + correlationId, NOTHING_MORE_WINDOW,
                () -> tracer.events(Topics.TRIP_RAW, correlationId).stream().findAny().map(Object::toString));
    }
}
