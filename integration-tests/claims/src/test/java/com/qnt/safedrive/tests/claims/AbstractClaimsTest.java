package com.qnt.safedrive.tests.claims;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.model.ClaimsResponse;
import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Epic;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared setup for every claims feature. Events are put straight on {@code driving.event} and {@code premium.adjusted}
 * (keyed by driver, like their producers do), so each test controls the crash, the policy and their order. The two
 * topics have no order between them, so every assertion on a claim is eventual: it waits for the status it expects.
 */
@Epic("claims")
public abstract class AbstractClaimsTest extends AbstractTest {

    /** How long to watch for something that must not happen (everything here is asynchronous). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    protected static final String DRIVING_EVENT_DEAD_LETTER = Topics.deadLetter(Topics.DRIVING_EVENT, Services.CLAIMS);
    protected static final String PREMIUM_DEAD_LETTER = Topics.deadLetter(Topics.PREMIUM_ADJUSTED, Services.CLAIMS);

    /** Puts a new {@code driving.event} on the topic; returns the event that was sent. */
    protected Envelope publishDrivingEvent(DrivingEvent drivingEvent, String correlationId) {
        Envelope event = EnvelopeFactory.aDrivingEventEvent(drivingEvent, correlationId);
        publisher().send(Topics.DRIVING_EVENT, drivingEvent.driverId(), event);
        return event;
    }

    /** Puts a new {@code premium.adjusted} event on the topic; returns the event that was sent. */
    protected Envelope publishPremiumAdjusted(PremiumAdjusted premium, String correlationId) {
        Envelope event = EnvelopeFactory.aPremiumAdjustedEvent(premium, correlationId);
        publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);
        return event;
    }

    protected ClaimsResponse claimsOf(String driverId) {
        return Json.read(claimsClient().getClaimsWithRetry(driverId, null).asString(), ClaimsResponse.class);
    }

    /** Waits until the driver has a claim for the trip in the given status. */
    protected ClaimsResponse.Claim awaitClaim(String driverId, String tripId, String status) {
        return waiter.until("claim of driver " + driverId + " trip " + tripId + " in status " + status, () -> claimsOf(driverId)
                .claims().stream()
                .filter(c -> tripId.equals(c.tripId()) && status.equals(c.status()))
                .findFirst());
    }

    /**
     * Watches the window for a claim beyond {@code count} (fails if one shows up), then returns the claims API
     * response, for a validator to check: {@code Validator.with(awaitNoMoreClaimsThan(1, id)).claimsBody().hasClaimCount(1)}.
     */
    protected Response awaitNoMoreClaimsThan(int count, String driverId) {
        waiter.staysEmpty("more than " + count + " claim(s) for driver " + driverId, NOTHING_MORE_WINDOW, () -> {
            int found = claimsOf(driverId).claims().size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        return claimsClient().getClaimsWithRetry(driverId, null);
    }

    /**
     * Watches the window for the claim of a trip to leave the given status or policy version (fails if it does), then
     * returns the claims API response of the driver.
     */
    protected Response awaitClaimStays(String driverId, String tripId, String status, Integer policyScoreVersion) {
        waiter.staysEmpty("claim of trip " + tripId + " changing from " + status + " v" + policyScoreVersion, NOTHING_MORE_WINDOW,
                () -> claimsOf(driverId).claims().stream()
                        .filter(c -> tripId.equals(c.tripId()))
                        .filter(c -> !status.equals(c.status()) || !Objects.equals(policyScoreVersion, c.policyScoreVersion()))
                        .findFirst());
        return claimsClient().getClaimsWithRetry(driverId, null);
    }
}
