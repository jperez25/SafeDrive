package com.qnt.safedrive.tests.policyrating;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.factory.ScoreUpdatedFactory;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.ScoreUpdated;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Epic;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Shared setup for every policy-rating feature. Scores are put straight on {@code score.updated} (keyed by driver,
 * like scoring does), so each test controls the exact score and version and therefore the expected premium. Each test
 * uses its own driver, so parallel tests never share a policy.
 */
@Epic("policy-rating")
public abstract class AbstractRatingTest extends AbstractTest {

    /** How long to watch for an event that must not appear (publishing is asynchronous, so one look is not enough). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** Puts a new {@code score.updated} event on the topic; returns the event that was sent. */
    protected Envelope publishScoreUpdated(ScoreUpdated score, String correlationId) {
        Envelope event = EnvelopeFactory.aScoreUpdatedEvent(score, correlationId);
        publisher().send(Topics.SCORE_UPDATED, score.driverId(), event);
        return event;
    }

    /** Publishes a score of this driver and waits for the premium it should produce. */
    protected TopicReader.Message publishAndAwaitPremium(String driverId, int scoreVersion, double score, String correlationId) {
        publishScoreUpdated(ScoreUpdatedFactory.aScoreUpdatedFor(driverId, scoreVersion, score), correlationId);
        return awaitPremiumAdjusted(driverId, correlationId, scoreVersion);
    }

    protected TopicReader.Message awaitPremiumAdjusted(String driverId, String correlationId, int scoreVersion) {
        return tracer.awaitEvent(Topics.PREMIUM_ADJUSTED, correlationId,
                m -> driverId.equals(m.envelope().payloadText("driverId"))
                        && scoreVersion == m.envelope().payload().path("scoreVersion").asInt());
    }

    /** {@code premium.adjusted} events published so far for the driver. */
    protected List<TopicReader.Message> premiumAdjustedFor(String driverId, String correlationId) {
        return tracer.events(Topics.PREMIUM_ADJUSTED, correlationId).stream()
                .filter(m -> driverId.equals(m.envelope().payloadText("driverId")))
                .toList();
    }

    /** Watches the window for a {@code premium.adjusted} beyond {@code count}, then checks there are exactly {@code count}. */
    protected void assertPremiumAdjustedTimes(int count, String driverId, String correlationId) {
        waiter.staysEmpty("more than " + count + " premium.adjusted event(s) for driver " + driverId, NOTHING_MORE_WINDOW, () -> {
            int found = premiumAdjustedFor(driverId, correlationId).size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        assertThat(premiumAdjustedFor(driverId, correlationId)).as("premium.adjusted events for driver %s", driverId).hasSize(count);
    }
}
