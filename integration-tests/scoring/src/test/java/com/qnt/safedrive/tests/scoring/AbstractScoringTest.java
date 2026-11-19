package com.qnt.safedrive.tests.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Epic;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Shared setup for every scoring feature. Trips are put straight on {@code trip.completed} (keyed by driver, like
 * trip-processor does), so each test controls the exact trip summary and therefore the expected score. Each test
 * uses its own driver, so parallel tests never share a score.
 */
@Epic("scoring")
public abstract class AbstractScoringTest extends AbstractTest {

    /** How long to watch for an event that must not appear (publishing is asynchronous, so one look is not enough). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** Puts a new {@code trip.completed} event on the topic; returns the event that was sent. */
    protected Envelope publishTripCompleted(TripCompleted trip, String correlationId) {
        Envelope event = EnvelopeFactory.aTripCompletedEvent(trip, correlationId);
        publisher().send(Topics.TRIP_COMPLETED, trip.driverId(), event);
        return event;
    }

    /** Publishes the trip and waits for the score version it should produce. */
    protected TopicReader.Message publishAndAwaitScore(TripCompleted trip, String correlationId, int expectedVersion) {
        publishTripCompleted(trip, correlationId);
        return awaitScoreUpdated(trip.driverId(), correlationId, expectedVersion);
    }

    protected TopicReader.Message awaitScoreUpdated(String driverId, String correlationId, int version) {
        return tracer.awaitEvent(Topics.SCORE_UPDATED, correlationId,
                m -> driverId.equals(m.envelope().payloadText("driverId"))
                        && version == m.envelope().payload().path("scoreVersion").asInt());
    }

    /** {@code score.updated} events published so far for the driver. */
    protected List<TopicReader.Message> scoreUpdatedFor(String driverId, String correlationId) {
        return tracer.events(Topics.SCORE_UPDATED, correlationId).stream()
                .filter(m -> driverId.equals(m.envelope().payloadText("driverId")))
                .toList();
    }

    /** Watches the window for a {@code score.updated} beyond {@code count}, then checks there are exactly {@code count}. */
    protected void assertScoreUpdatedTimes(int count, String driverId, String correlationId) {
        waiter.staysEmpty("more than " + count + " score.updated event(s) for driver " + driverId, NOTHING_MORE_WINDOW, () -> {
            int found = scoreUpdatedFor(driverId, correlationId).size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        assertThat(scoreUpdatedFor(driverId, correlationId)).as("score.updated events for driver %s", driverId).hasSize(count);
    }
}
