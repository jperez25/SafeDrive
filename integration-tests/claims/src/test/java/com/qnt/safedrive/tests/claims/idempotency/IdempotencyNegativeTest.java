package com.qnt.safedrive.tests.claims.idempotency;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class IdempotencyNegativeTest extends AbstractIdempotencyTest {

    @Test(groups = Groups.REGRESSION, description = "The same crash event delivered twice (same event ID) opens one claim")
    @Story("Duplicates")
    public void sameEventDeliveredTwiceOpensOneClaim() {
        DrivingEvent crash = aCrashEvent();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation());
        Envelope event = publishDrivingEvent(crash, Ids.correlation());
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        publisher().send(Topics.DRIVING_EVENT, crash.driverId(), event);

        Validator.with(awaitNoMoreClaimsThan(1, crash.driverId()))
                .claimsBody()
                .hasClaimCount(1)
                .hasClaim(crash.tripId(), Statuses.CLAIM_OPENED, Severities.HIGH);
    }

    @Test(groups = Groups.REGRESSION, description = "The same crash under a new event ID (a re-published event) opens no second claim")
    @Story("Duplicates")
    public void sameCrashUnderANewEventIdIsIgnored() {
        DrivingEvent crash = aCrashEvent();
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation());
        publishDrivingEvent(crash, Ids.correlation());
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);

        publishDrivingEvent(crash, Ids.correlation());

        Validator.with(awaitNoMoreClaimsThan(1, crash.driverId())).claimsBody().hasClaimCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "A crash delivered twice while it waits for the policy gives one waiting claim, then one opened claim")
    @Story("Duplicates")
    public void duplicateWhileWaitingForThePolicyStaysOneClaim() {
        DrivingEvent crash = aCrashEvent();
        Envelope event = publishDrivingEvent(crash, Ids.correlation());
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_PENDING_POLICY);
        publisher().send(Topics.DRIVING_EVENT, crash.driverId(), event);

        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation());

        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);
        Validator.with(awaitNoMoreClaimsThan(1, crash.driverId())).claimsBody().hasClaimCount(1);
    }

    @Test(groups = Groups.REGRESSION, description = "A late duplicate of an old crash, after a newer crash, opens nothing")
    @Story("Duplicates")
    public void lateDuplicateIsIgnored() {
        DrivingEvent first = aCrashEvent();
        DrivingEvent second = aCrashEvent().withDriverId(first.driverId());
        publishPremiumAdjusted(aPremiumAdjustedFor(first.driverId(), 1, Bands.GOOD), Ids.correlation());
        Envelope firstEvent = publishDrivingEvent(first, Ids.correlation());
        awaitClaim(first.driverId(), first.tripId(), Statuses.CLAIM_OPENED);
        publishDrivingEvent(second, Ids.correlation());
        awaitClaim(second.driverId(), second.tripId(), Statuses.CLAIM_OPENED);

        publisher().send(Topics.DRIVING_EVENT, first.driverId(), firstEvent);

        Validator.with(awaitNoMoreClaimsThan(2, first.driverId())).claimsBody().hasClaimCount(2);
    }
}
