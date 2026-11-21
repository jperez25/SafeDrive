package com.qnt.safedrive.tests.claims.claimsapi;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.time.Instant;
import org.testng.annotations.Test;

public class ClaimsApiPositiveTest extends AbstractClaimsApiTest {

    @Test(groups = Groups.REGRESSION, description = "The API lists the driver's claims, one per trip, and echoes the correlation ID")
    @Story("Read the claims")
    public void apiListsTheClaimsOfTheDriver() {
        DrivingEvent first = aCrashEvent();
        DrivingEvent second = aCrashEvent().withDriverId(first.driverId()).withSeverity(Severities.LOW);
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(aPremiumAdjustedFor(first.driverId(), 1, Bands.GOOD), correlationId);
        publishDrivingEvent(first, correlationId);
        awaitClaim(first.driverId(), first.tripId(), Statuses.CLAIM_OPENED);
        publishDrivingEvent(second, correlationId);
        awaitClaim(second.driverId(), second.tripId(), Statuses.CLAIM_OPENED);

        Validator.with(claimsClient().getClaimsWithRetry(first.driverId(), correlationId))
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .claimsBody()
                .hasDriverId(first.driverId())
                .hasClaimCount(2)
                .hasClaim(first.tripId(), Statuses.CLAIM_OPENED, Severities.HIGH)
                .hasClaim(second.tripId(), Statuses.CLAIM_OPENED, Severities.LOW);
    }

    @Test(groups = Groups.REGRESSION, description = "One claim can be read on its own by its ID, with the crash position and time")
    @Story("Read one claim")
    public void oneClaimCanBeReadById() {
        DrivingEvent crash = aCrashEvent().withLatitude(40.71).withLongitude(-74.0);
        String correlationId = Ids.correlation();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), correlationId);
        publishDrivingEvent(crash, correlationId);
        var claim = awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        var body = claimsClient().getClaim(claim.claimId(), correlationId);

        Validator.with(body)
                .hasStatus(Http.OK)
                .hasCorrelationHeader(correlationId)
                .claimBody()
                .hasClaimId(claim.claimId())
                .isForTrip(crash.driverId(), crash.tripId())
                .hasStatus(Statuses.CLAIM_OPENED, Severities.HIGH);
        org.assertj.core.api.Assertions.assertThat(claim.latitude()).as("latitude").isEqualTo(40.71);
        org.assertj.core.api.Assertions.assertThat(claim.longitude()).as("longitude").isEqualTo(-74.0);
        org.assertj.core.api.Assertions.assertThat(Instant.parse(claim.crashAt())).as("crashAt").isEqualTo(Instant.parse(crash.timestamp()));
    }

    @Test(groups = Groups.REGRESSION, description = "A request without a correlation ID still gets one back in the response header")
    @Story("Correlation ID")
    public void apiCreatesACorrelationIdWhenNoneIsSent() {
        Validator.with(claimsClient().getClaimsWithRetry(Ids.driver(), null)).hasStatus(Http.OK).hasCorrelationHeader();
    }
}
