package com.qnt.safedrive.tests.policyrating.idempotency;

import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdated;
import static com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory.aScoreUpdatedFor;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class IdempotencyNegativeTest extends AbstractIdempotencyTest {

    @Test(groups = Groups.REGRESSION, description = "The same event delivered twice (same event ID) gives one premium.adjusted")
    @Story("Duplicates")
    public void sameEventDeliveredTwiceIsProcessedOnce() {
        ScoreUpdated score = aScoreUpdated();
        String correlationId = Ids.correlation();
        Envelope event = publishScoreUpdated(score, correlationId);
        awaitPremiumAdjusted(score.driverId(), correlationId, 1);

        publisher().send(Topics.SCORE_UPDATED, score.driverId(), event);

        assertPremiumAdjustedTimes(1, score.driverId(), correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A duplicate that arrives after later scores changes neither the version nor the premium")
    @Story("Duplicates")
    public void lateDuplicateDoesNotChangeThePremium() {
        String driverId = Ids.driver();
        String correlationId = Ids.correlation();
        Envelope event = publishScoreUpdated(aScoreUpdatedFor(driverId, 1, 95.0), correlationId);
        awaitPremiumAdjusted(driverId, correlationId, 1);
        publishAndAwaitPremium(driverId, 2, 50.0, correlationId);

        publisher().send(Topics.SCORE_UPDATED, driverId, event);

        assertPremiumAdjustedTimes(2, driverId, correlationId);
        Validator.with(premiumClient().getPremiumWithRetry(driverId, correlationId))
                .premiumBody()
                .hasScoreVersion(2)
                .hasNewPremium(138.00);
    }
}
