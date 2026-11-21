package com.qnt.safedrive.tests.claims.deadletter;

import static com.qnt.safedrive.tests.core.factory.DrivingEventFactory.aCrashEvent;
import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aDrivingEventEvent;
import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aPremiumAdjustedEvent;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjusted;
import static com.qnt.safedrive.tests.core.factory.PremiumAdjustedFactory.aPremiumAdjustedFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.DrivingEvent;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.constants.Bands;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Events claims can never handle go to the dead-letter topic untouched, without blocking what follows. */
public class DeadLetterNegativeTest extends AbstractDeadLetterTest {

    private static final String NOT_JSON = "this is not json {";

    @DataProvider
    public Object[][] topics() {
        return new Object[][] {
            {Topics.DRIVING_EVENT, DRIVING_EVENT_DEAD_LETTER},
            {Topics.PREMIUM_ADJUSTED, PREMIUM_DEAD_LETTER}
        };
    }

    @Test(dataProvider = "topics", groups = Groups.REGRESSION, description = "A message that is not JSON is dead-lettered with its key and value unchanged")
    @Story("Unreadable event")
    public void unreadableEventIsDeadLettered(String topic, String deadLetterTopic) {
        String key = Ids.driver();
        publisher().send(topic, key, NOT_JSON);

        Validator.with(awaitDeadLetter(deadLetterTopic, key)).hasTopic(deadLetterTopic).hasKey(key).hasValue(NOT_JSON);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on driving.event is dead-lettered unchanged: no claim")
    @Story("Wrong contract")
    public void drivingEventOfAnotherTypeIsDeadLettered() {
        DrivingEvent crash = aCrashEvent();
        Envelope event = aDrivingEventEvent(crash, Ids.correlation()).withType(EventTypes.SCORE_UPDATED);

        String sent = publisher().send(Topics.DRIVING_EVENT, crash.driverId(), event);

        Validator.with(awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, crash.driverId())).hasKey(crash.driverId()).hasValue(sent);
        Validator.with(awaitNoMoreClaimsThan(0, crash.driverId())).claimsBody().hasClaimCount(0);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on premium.adjusted is dead-lettered unchanged")
    @Story("Wrong contract")
    public void premiumEventOfAnotherTypeIsDeadLettered() {
        PremiumAdjusted premium = aPremiumAdjusted();
        Envelope event = aPremiumAdjustedEvent(premium, Ids.correlation()).withType(EventTypes.DRIVING_EVENT);

        String sent = publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);

        Validator.with(awaitDeadLetter(PREMIUM_DEAD_LETTER, premium.driverId())).hasKey(premium.driverId()).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "An event with an unsupported contract version is dead-lettered unchanged")
    @Story("Wrong contract")
    public void unsupportedVersionIsDeadLettered() {
        DrivingEvent crash = aCrashEvent();
        Envelope event = aDrivingEventEvent(crash, Ids.correlation()).withVersion(2);

        String sent = publisher().send(Topics.DRIVING_EVENT, crash.driverId(), event);

        Validator.with(awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, crash.driverId())).hasKey(crash.driverId()).hasValue(sent);
    }

    @DataProvider
    public Object[][] invalidCrashes() {
        return new Object[][] {
            {"driver ID missing", aCrashEvent().withDriverId(null)},
            {"driver ID blank", aCrashEvent().withDriverId(" ")},
            {"trip ID missing", aCrashEvent().withTripId(null)},
            {"trip ID blank", aCrashEvent().withTripId(" ")},
            {"severity unknown", aCrashEvent().withSeverity("extreme")},
            {"severity missing", aCrashEvent().withSeverity(null)},
            {"timestamp missing", aCrashEvent().withTimestamp(null)}
        };
    }

    @Test(dataProvider = "invalidCrashes", groups = Groups.REGRESSION,
            description = "A well-formed envelope whose payload can never open a claim is dead-lettered unchanged")
    @Story("Invalid payload")
    public void invalidCrashIsDeadLettered(String reason, DrivingEvent invalid) {
        String key = Ids.driver(); // the payload may have no driver ID, so the test picks its own key
        String sent = publisher().send(Topics.DRIVING_EVENT, key, aDrivingEventEvent(invalid, Ids.correlation()));

        Validator.with(awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, key)).hasKey(key).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "A dead letter does not block the partition: a valid crash with the same key right after still opens a claim")
    @Story("Unreadable event")
    public void validCrashAfterADeadLetterStillOpensAClaim() {
        DrivingEvent crash = aCrashEvent();
        publisher().send(Topics.DRIVING_EVENT, crash.driverId(), NOT_JSON);
        publishPremiumAdjusted(aPremiumAdjustedFor(crash.driverId(), 1, Bands.GOOD), Ids.correlation());

        publishDrivingEvent(crash, Ids.correlation());

        awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, crash.driverId());
        awaitClaim(crash.driverId(), crash.tripId(), Statuses.CLAIM_OPENED);
    }

    @Test(groups = Groups.REGRESSION, description = "Dead-lettering is logged with the reason and the key, never the payload")
    @Story("Unreadable event")
    public void deadLetteringIsLogged() {
        String key = Ids.driver();
        publisher().send(Topics.DRIVING_EVENT, key, NOT_JSON);
        awaitDeadLetter(DRIVING_EVENT_DEAD_LETTER, key);

        assertThat(tracer.awaitLog(Services.CLAIMS, key))
                .as("claims log lines for key %s", key)
                .anySatisfy(line -> assertThat(line.json()).contains("dead-lettering").contains("not a readable driving.event event"))
                .allSatisfy(line -> assertThat(line.json()).doesNotContain(NOT_JSON));
    }
}
