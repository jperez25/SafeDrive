package com.qnt.safedrive.tests.scoring.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aTripCompletedEvent;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Events scoring can never handle go to the dead-letter topic untouched, without blocking what follows. */
public class DeadLetterNegativeTest extends AbstractDeadLetterTest {

    private static final String NOT_JSON = "this is not json {";

    @Test(groups = Groups.REGRESSION, description = "A message that is not JSON is dead-lettered with its key and value unchanged")
    @Story("Unreadable event")
    public void unreadableEventIsDeadLettered() {
        String key = Ids.driver();
        publisher().send(Topics.TRIP_COMPLETED, key, NOT_JSON);

        Validator.with(awaitDeadLetter(key))
                .hasTopic(DEAD_LETTER_TOPIC)
                .hasKey(key)
                .hasValue(NOT_JSON);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on trip.completed is dead-lettered unchanged and produces no score")
    @Story("Wrong contract")
    public void eventOfAnotherTypeIsDeadLettered() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        Envelope event = aTripCompletedEvent(trip, correlationId).withType(EventTypes.SCORE_UPDATED);

        String sent = publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), event);

        Validator.with(awaitDeadLetter(trip.driverId())).hasKey(trip.driverId()).hasValue(sent);
        assertScoreUpdatedTimes(0, trip.driverId(), correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "An event with an unsupported contract version is dead-lettered unchanged")
    @Story("Wrong contract")
    public void unsupportedVersionIsDeadLettered() {
        TripCompleted trip = aTripCompleted();
        Envelope event = aTripCompletedEvent(trip, Ids.correlation()).withVersion(2);

        String sent = publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), event);

        Validator.with(awaitDeadLetter(trip.driverId())).hasKey(trip.driverId()).hasValue(sent);
    }

    @DataProvider
    public Object[][] invalidPayloads() {
        return new Object[][] {
            {"trip ID missing", aTripCompleted().withTripId(null)},
            {"trip ID blank", aTripCompleted().withTripId(" ")},
            {"driver ID missing", aTripCompleted().withDriverId(null)},
            {"driver ID blank", aTripCompleted().withDriverId(" ")},
            {"revision zero", aTripCompleted().withRevision(0)},
            {"end time missing", aTripCompleted().withEndedAt(null)},
            {"negative distance", aTripCompleted().withDistanceKilometers(-1)},
            {"event counts missing", aTripCompleted().withEventCounts(null)}
        };
    }

    @Test(dataProvider = "invalidPayloads", groups = Groups.REGRESSION,
            description = "A well-formed envelope whose payload can never be scored is dead-lettered unchanged")
    @Story("Invalid payload")
    public void invalidPayloadIsDeadLettered(String reason, TripCompleted invalid) {
        String key = Ids.driver(); // the payload may have no driver ID, so the test picks its own key
        String sent = publisher().send(Topics.TRIP_COMPLETED, key, aTripCompletedEvent(invalid, Ids.correlation()));

        Validator.with(awaitDeadLetter(key)).hasKey(key).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "A dead letter does not block the partition: a valid trip with the same key right after is still scored")
    @Story("Unreadable event")
    public void validTripAfterADeadLetterIsStillScored() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), NOT_JSON);

        publishTripCompleted(trip, correlationId);

        awaitDeadLetter(trip.driverId());
        Validator.with(awaitScoreUpdated(trip.driverId(), correlationId, 1)).envelope().scoreUpdatedPayload().hasScore(100.0);
    }

    @Test(groups = Groups.REGRESSION, description = "Dead-lettering is logged with the reason and the key, never the payload")
    @Story("Unreadable event")
    public void deadLetteringIsLogged() {
        String key = Ids.driver();
        publisher().send(Topics.TRIP_COMPLETED, key, NOT_JSON);
        awaitDeadLetter(key);

        assertThat(tracer.awaitLog(Services.SCORING, key))
                .as("scoring log lines for key %s", key)
                .anySatisfy(line -> assertThat(line.json()).contains("dead-lettering").contains("not a readable trip.completed event"))
                .allSatisfy(line -> assertThat(line.json()).doesNotContain(NOT_JSON));
    }
}
