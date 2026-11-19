package com.qnt.safedrive.tests.tripprocessor.deadletter;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aTripRawEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Events the processor can never handle go to the dead-letter topic untouched, without blocking what follows. */
public class DeadLetterNegativeTest extends AbstractDeadLetterTest {

    private static final String NOT_JSON = "this is not json {";

    @Test(groups = Groups.REGRESSION, description = "A message that is not JSON is dead-lettered with its key and value unchanged")
    @Story("Unreadable event")
    public void unreadableEventIsDeadLettered() {
        String key = Ids.trip();
        publisher().send(Topics.TRIP_RAW, key, NOT_JSON);

        Validator.with(awaitDeadLetter(key))
                .hasTopic(DEAD_LETTER_TOPIC)
                .hasKey(key)
                .hasValue(NOT_JSON);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on trip.raw is dead-lettered unchanged and produces no trip")
    @Story("Wrong contract")
    public void eventOfAnotherTypeIsDeadLettered() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        Envelope event = aTripRawEvent(batch, correlationId).withType(EventTypes.TRIP_COMPLETED);

        String sent = publisher().send(Topics.TRIP_RAW, batch.tripId(), event);

        Validator.with(awaitDeadLetter(batch.tripId())).hasKey(batch.tripId()).hasValue(sent);
        assertThat(tripCompletedFor(batch, correlationId)).as("trip.completed for a dead-lettered event").isEmpty();
    }

    @Test(groups = Groups.REGRESSION, description = "An event with an unsupported contract version is dead-lettered unchanged")
    @Story("Wrong contract")
    public void unsupportedVersionIsDeadLettered() {
        Batch batch = aBatch();
        Envelope event = aTripRawEvent(batch, Ids.correlation()).withVersion(2);

        String sent = publisher().send(Topics.TRIP_RAW, batch.tripId(), event);

        Validator.with(awaitDeadLetter(batch.tripId())).hasKey(batch.tripId()).hasValue(sent);
    }

    @DataProvider
    public Object[][] invalidPayloads() {
        return new Object[][] {
            {"no samples", aBatch().withSamples(List.of())},
            {"samples missing", aBatch().withSamples(null)},
            {"trip ID missing", aBatch().withTripId(null)},
            {"trip ID blank", aBatch().withTripId(" ")},
            {"driver ID missing", aBatch().withDriverId(null)},
            {"negative batch sequence", aBatch().withBatchSequence(-1)}
        };
    }

    @Test(dataProvider = "invalidPayloads", groups = Groups.REGRESSION,
            description = "A well-formed envelope whose payload can never be assembled is dead-lettered unchanged")
    @Story("Invalid payload")
    public void invalidPayloadIsDeadLettered(String reason, Batch invalid) {
        String key = Ids.trip(); // the payload may have no trip ID, so the test picks its own key
        String sent = publisher().send(Topics.TRIP_RAW, key, aTripRawEvent(invalid, Ids.correlation()));

        Validator.with(awaitDeadLetter(key)).hasKey(key).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "A dead letter does not block the partition: a valid batch with the same key right after still completes")
    @Story("Unreadable event")
    public void validBatchAfterADeadLetterStillCompletes() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        publisher().send(Topics.TRIP_RAW, batch.tripId(), NOT_JSON);

        publishTripRaw(batch, correlationId);

        awaitDeadLetter(batch.tripId());
        Validator.with(awaitTripCompleted(batch, correlationId, 1)).envelope().tripCompletedPayload().hasRevision(1);
    }

    @Test(groups = Groups.REGRESSION, description = "Dead-lettering is logged with the reason and the key, never the payload")
    @Story("Unreadable event")
    public void deadLetteringIsLogged() {
        String key = Ids.trip();
        publisher().send(Topics.TRIP_RAW, key, NOT_JSON);
        awaitDeadLetter(key);

        assertThat(tracer.awaitLog(Services.TRIP_PROCESSOR, key))
                .as("trip-processor log lines for key %s", key)
                .anySatisfy(line -> assertThat(line.json()).contains("dead-lettering").contains("not a readable trip.raw event"))
                .allSatisfy(line -> assertThat(line.json()).doesNotContain(NOT_JSON));
    }
}
