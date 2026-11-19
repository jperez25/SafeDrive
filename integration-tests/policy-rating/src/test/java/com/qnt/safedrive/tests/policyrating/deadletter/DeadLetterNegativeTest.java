package com.qnt.safedrive.tests.policyrating.deadletter;

import static com.qnt.safedrive.tests.core.factory.EnvelopeFactory.aScoreUpdatedEvent;
import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdated;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Events policy-rating can never handle go to the dead-letter topic untouched, without blocking what follows. */
public class DeadLetterNegativeTest extends AbstractDeadLetterTest {

    private static final String NOT_JSON = "this is not json {";

    @Test(groups = Groups.REGRESSION, description = "A message that is not JSON is dead-lettered with its key and value unchanged")
    @Story("Unreadable event")
    public void unreadableEventIsDeadLettered() {
        String key = Ids.driver();
        publisher().send(Topics.SCORE_UPDATED, key, NOT_JSON);

        Validator.with(awaitDeadLetter(key))
                .hasTopic(DEAD_LETTER_TOPIC)
                .hasKey(key)
                .hasValue(NOT_JSON);
    }

    @Test(groups = Groups.REGRESSION, description = "An event of another type on score.updated is dead-lettered unchanged and produces no premium")
    @Story("Wrong contract")
    public void eventOfAnotherTypeIsDeadLettered() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        Envelope event = aScoreUpdatedEvent(score, correlationId).withType(EventTypes.TRIP_COMPLETED);

        String sent = publisher().send(Topics.SCORE_UPDATED, score.driverId(), event);

        Validator.with(awaitDeadLetter(score.driverId())).hasKey(score.driverId()).hasValue(sent);
        assertPremiumAdjustedTimes(0, score.driverId(), correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "An event with an unsupported contract version is dead-lettered unchanged")
    @Story("Wrong contract")
    public void unsupportedVersionIsDeadLettered() {
        ScoreUpdated score = aScoreUpdated();
        Envelope event = aScoreUpdatedEvent(score, Ids.correlation()).withVersion(2);

        String sent = publisher().send(Topics.SCORE_UPDATED, score.driverId(), event);

        Validator.with(awaitDeadLetter(score.driverId())).hasKey(score.driverId()).hasValue(sent);
    }

    @DataProvider
    public Object[][] invalidPayloads() {
        return new Object[][] {
            {"driver ID missing", aScoreUpdated().withDriverId(null)},
            {"driver ID blank", aScoreUpdated().withDriverId(" ")},
            {"score version zero", aScoreUpdated().withScoreVersion(0)},
            {"score version negative", aScoreUpdated().withScoreVersion(-1)},
            {"score negative", aScoreUpdated().withScore(-0.01)},
            {"score above 100", aScoreUpdated().withScore(100.01)}
        };
    }

    @Test(dataProvider = "invalidPayloads", groups = Groups.REGRESSION,
            description = "A well-formed envelope whose payload can never be rated is dead-lettered unchanged")
    @Story("Invalid payload")
    public void invalidPayloadIsDeadLettered(String reason, ScoreUpdated invalid) {
        String key = Ids.driver(); // the payload may have no driver ID, so the test picks its own key
        String sent = publisher().send(Topics.SCORE_UPDATED, key, aScoreUpdatedEvent(invalid, Ids.correlation()));

        Validator.with(awaitDeadLetter(key)).hasKey(key).hasValue(sent);
    }

    @Test(groups = Groups.REGRESSION, description = "A dead letter does not block the partition: a valid score with the same key right after is still rated")
    @Story("Unreadable event")
    public void validScoreAfterADeadLetterIsStillRated() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        publisher().send(Topics.SCORE_UPDATED, score.driverId(), NOT_JSON);

        publishScoreUpdated(score, correlationId);

        awaitDeadLetter(score.driverId());
        Validator.with(awaitPremiumAdjusted(score.driverId(), correlationId, 1)).envelope().premiumAdjustedPayload().hasNewPremium(96.00);
    }

    @Test(groups = Groups.REGRESSION, description = "Dead-lettering is logged with the reason and the key, never the payload")
    @Story("Unreadable event")
    public void deadLetteringIsLogged() {
        String key = Ids.driver();
        publisher().send(Topics.SCORE_UPDATED, key, NOT_JSON);
        awaitDeadLetter(key);

        assertThat(tracer.awaitLog(Services.POLICY_RATING, key))
                .as("policy-rating log lines for key %s", key)
                .anySatisfy(line -> assertThat(line.json()).contains("dead-lettering").contains("not a readable score.updated event"))
                .allSatisfy(line -> assertThat(line.json()).doesNotContain(NOT_JSON));
    }
}
